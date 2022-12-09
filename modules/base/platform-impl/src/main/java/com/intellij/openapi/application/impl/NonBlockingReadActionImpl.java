// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.diagnostic.ThreadDumper;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution;
import com.intellij.openapi.application.constraints.Expiration;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import consulo.logging.Logger;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * @author peter
 */
@VisibleForTesting
public class NonBlockingReadActionImpl<T> extends ExpirableConstrainedExecution<NonBlockingReadActionImpl<T>> implements NonBlockingReadAction<T> {
  private static final Logger LOG = Logger.getInstance(NonBlockingReadActionImpl.class);
  private static final Executor SYNC_DUMMY_EXECUTOR = __ -> {
    throw new UnsupportedOperationException();
  };

  private final
  @Nullable
  Pair<ModalityState, Consumer<T>> myEdtFinish;
  private final
  @Nullable
  List<Object> myCoalesceEquality;
  private final
  @Nullable
  ProgressIndicator myProgressIndicator;
  private final Callable<T> myComputation;

  private static final Set<CancellablePromise<?>> ourTasks = ContainerUtil.newConcurrentSet();
  private static final Map<List<Object>, NonBlockingReadActionImpl<?>.Submission> ourTasksByEquality = new HashMap<>();
  private static final AtomicInteger ourUnboundedSubmissionCount = new AtomicInteger();

  NonBlockingReadActionImpl(@Nonnull Callable<T> computation) {
    this(computation, null, new ContextConstraint[0], new BooleanSupplier[0], Collections.emptySet(), null, null);
  }

  private NonBlockingReadActionImpl(@Nonnull Callable<T> computation,
                                    @Nullable Pair<ModalityState, Consumer<T>> edtFinish,
                                    @Nonnull ContextConstraint[] constraints,
                                    @Nonnull BooleanSupplier[] cancellationConditions,
                                    @Nonnull Set<? extends Expiration> expirationSet,
                                    @Nullable List<Object> coalesceEquality,
                                    @Nullable ProgressIndicator progressIndicator) {
    super(constraints, cancellationConditions, expirationSet);
    myComputation = computation;
    myEdtFinish = edtFinish;
    myCoalesceEquality = coalesceEquality;
    myProgressIndicator = progressIndicator;
  }

  @Nonnull
  @Override
  protected NonBlockingReadActionImpl<T> cloneWith(@Nonnull ContextConstraint[] constraints, @Nonnull BooleanSupplier[] cancellationConditions, @Nonnull Set<? extends Expiration> expirationSet) {
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, constraints, cancellationConditions, expirationSet, myCoalesceEquality, myProgressIndicator);
  }

  @Override
  public void dispatchLaterUnconstrained(@Nonnull Runnable runnable) {
    ApplicationManager.getApplication().invokeLater(runnable, ModalityState.any());
  }

  @Override
  public NonBlockingReadAction<T> inSmartMode(@Nonnull Project project) {
    return withConstraint(new InSmartMode(project), project);
  }

  @Override
  public NonBlockingReadAction<T> withDocumentsCommitted(@Nonnull Project project) {
    return withConstraint(new WithDocumentsCommitted(project, ModalityState.any()), project);
  }

  @Override
  public NonBlockingReadAction<T> expireWhen(@Nonnull BooleanSupplier expireCondition) {
    return cancelIf(expireCondition);
  }

  @Override
  public NonBlockingReadAction<T> cancelWith(@Nonnull ProgressIndicator progressIndicator) {
    LOG.assertTrue(myProgressIndicator == null, "Unspecified behaviour. Outer progress indicator is already set for the action.");
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, getConstraints(), getCancellationConditions(), getExpirationSet(), myCoalesceEquality, progressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> finishOnUiThread(@Nonnull ModalityState modality, @Nonnull Consumer<T> uiThreadAction) {
    return new NonBlockingReadActionImpl<>(myComputation, Pair.create(modality, uiThreadAction), getConstraints(), getCancellationConditions(), getExpirationSet(), myCoalesceEquality,
                                           myProgressIndicator);
  }

  @Override
  public NonBlockingReadAction<T> coalesceBy(@Nonnull Object... equality) {
    if (myCoalesceEquality != null) throw new IllegalStateException("Setting equality twice is not allowed");
    if (equality.length == 0) throw new IllegalArgumentException("Equality should include at least one object");
    return new NonBlockingReadActionImpl<>(myComputation, myEdtFinish, getConstraints(), getCancellationConditions(), getExpirationSet(), ContainerUtil.newArrayList(equality), myProgressIndicator);
  }

  @Override
  public CancellablePromise<T> submit(@Nonnull Executor backgroundThreadExecutor) {
    AsyncPromise<T> promise = new AsyncPromise<>();
    trackSubmission(backgroundThreadExecutor, promise);
    Submission submission = new Submission(promise, backgroundThreadExecutor);
    if (myCoalesceEquality == null) {
      submission.transferToBgThread();
    }
    else {
      submission.submitOrScheduleCoalesced(myCoalesceEquality);
    }
    return promise;
  }

  private void trackSubmission(@Nonnull Executor backgroundThreadExecutor, AsyncPromise<T> promise) {
    if (backgroundThreadExecutor == AppExecutorUtil.getAppExecutorService()) {
      preventTooManySubmissions(promise);
    }
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      rememberSubmissionInTests(promise);
    }
  }

  private static void preventTooManySubmissions(AsyncPromise<?> promise) {
    if (ourUnboundedSubmissionCount.incrementAndGet() % 107 == 0) {
      LOG.error("Too many non-blocking read actions submitted at once. " + "Please use coalesceBy, BoundedTaskExecutor or another way of limiting the number of concurrently running threads.");
    }
    promise.onProcessed(__ -> ourUnboundedSubmissionCount.decrementAndGet());
  }

  private static void rememberSubmissionInTests(AsyncPromise<?> promise) {
    ourTasks.add(promise);
    promise.onProcessed(__ -> ourTasks.remove(promise));
  }

  private class Submission {
    private final AsyncPromise<? super T> promise;
    @Nonnull
    private final Executor backendExecutor;
    private volatile ProgressIndicator currentIndicator;
    private final ModalityState creationModality = ModalityState.defaultModalityState();
    @Nullable
    private final BooleanSupplier myExpireCondition;
    @Nullable
    private NonBlockingReadActionImpl<?>.Submission myReplacement;

    // a sum composed of: 1 for non-done promise, 1 for each currently running thread
    // so 0 means that the process is marked completed or canceled, and it has no running not-yet-finished threads
    private int myUseCount;

    Submission(AsyncPromise<? super T> promise, @Nonnull Executor backgroundThreadExecutor) {
      this.promise = promise;
      backendExecutor = backgroundThreadExecutor;
      promise.onError(__ -> {
        ProgressIndicator indicator = currentIndicator;
        if (indicator != null) {
          indicator.cancel();
        }
      });
      if (myCoalesceEquality != null) {
        acquire();
        promise.onProcessed(__ -> release());
      }
      final Expiration expiration = composeExpiration();
      if (expiration != null) {
        final Expiration.Handle expirationHandle = expiration.invokeOnExpiration(promise::cancel);
        promise.onProcessed(value -> expirationHandle.unregisterHandler());
      }
      myExpireCondition = composeCancellationCondition();
    }

    void acquire() {
      assert myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        myUseCount++;
      }
    }

    void release() {
      assert myCoalesceEquality != null;
      synchronized (ourTasksByEquality) {
        if (--myUseCount == 0 && ourTasksByEquality.get(myCoalesceEquality) == this) {
          scheduleReplacementIfAny();
        }
      }
    }

    void scheduleReplacementIfAny() {
      if (myReplacement == null) {
        ourTasksByEquality.remove(myCoalesceEquality, this);
      }
      else {
        ourTasksByEquality.put(myCoalesceEquality, myReplacement);
        myReplacement.transferToBgThread();
      }
    }

    void submitOrScheduleCoalesced(@Nonnull List<Object> coalesceEquality) {
      synchronized (ourTasksByEquality) {
        NonBlockingReadActionImpl<?>.Submission current = ourTasksByEquality.get(coalesceEquality);
        if (current == null) {
          ourTasksByEquality.put(coalesceEquality, this);
          transferToBgThread();
        }
        else {
          if (current.myReplacement != null) {
            current.myReplacement.promise.cancel();
            assert current == ourTasksByEquality.get(coalesceEquality);
          }
          current.myReplacement = this;
          current.promise.cancel();
        }
      }
    }

    void transferToBgThread() {
      transferToBgThread(ReschedulingAttempt.NULL);
    }

    void transferToBgThread(@Nonnull ReschedulingAttempt previousAttempt) {
      if (myCoalesceEquality != null) {
        acquire();
      }
      backendExecutor.execute(() -> {
        final ProgressIndicator indicator = myProgressIndicator != null ? new SensitiveProgressWrapper(myProgressIndicator) {
          @Nonnull
          @Override
          public ModalityState getModalityState() {
            return creationModality;
          }
        } : new EmptyProgressIndicator(creationModality);

        currentIndicator = indicator;
        try {
          ReadAction.run(() -> {
            boolean success = ProgressIndicatorUtils.runWithWriteActionPriority(() -> insideReadAction(previousAttempt, indicator), indicator);
            if (!success && Promises.isPending(promise)) {
              reschedule(previousAttempt);
            }
          });
        }
        finally {
          currentIndicator = null;
          if (myCoalesceEquality != null) {
            release();
          }
        }
      });
    }

    private void reschedule(ReschedulingAttempt previousAttempt) {
      if (!checkObsolete()) {
        doScheduleWithinConstraints(attempt -> dispatchLaterUnconstrained(() -> transferToBgThread(attempt)), previousAttempt);
      }
    }

    void insideReadAction(ReschedulingAttempt previousAttempt, ProgressIndicator indicator) {
      try {
        if (checkObsolete()) {
          return;
        }
        if (!constraintsAreSatisfied()) {
          reschedule(previousAttempt);
          return;
        }

        T result = myComputation.call();

        if (myEdtFinish != null) {
          safeTransferToEdt(result, myEdtFinish, previousAttempt);
        }
        else {
          promise.setResult(result);
        }
      }
      catch (ProcessCanceledException e) {
        if (!indicator.isCanceled()) {
          promise.setError(e); // don't restart after a manually thrown PCE
        }
        throw e;
      }
      catch (Throwable e) {
        promise.setError(e);
      }
    }

    private boolean constraintsAreSatisfied() {
      return ContainerUtil.all(getConstraints(), ContextConstraint::isCorrectContext);
    }

    private boolean checkObsolete() {
      if (Promises.isRejected(promise)) return true;
      if (myExpireCondition != null && myExpireCondition.getAsBoolean()) {
        promise.cancel();
        return true;
      }
      if (myProgressIndicator != null && myProgressIndicator.isCanceled()) {
        promise.cancel();
        return true;
      }
      return false;
    }

    void safeTransferToEdt(T result, Pair<? extends ModalityState, ? extends Consumer<T>> edtFinish, ReschedulingAttempt previousAttempt) {
      if (Promises.isRejected(promise)) return;

      long stamp = AsyncExecutionServiceImpl.getWriteActionCounter();

      ApplicationManager.getApplication().invokeLater(() -> {
        if (stamp != AsyncExecutionServiceImpl.getWriteActionCounter()) {
          reschedule(previousAttempt);
          return;
        }

        if (checkObsolete()) {
          return;
        }

        promise.setResult(result);

        if (promise.isSucceeded()) { // in case another thread managed to cancel it just before `setResult`
          edtFinish.second.accept(result);
        }
      }, edtFinish.first);
    }

  }

  @TestOnly
  public static void cancelAllTasks() {
    while (!ourTasks.isEmpty()) {
      for (CancellablePromise<?> task : ourTasks) {
        task.cancel();
      }
      WriteAction.run(() -> {
      }); // let background threads complete
    }
  }

  @TestOnly
  public static void waitForAsyncTaskCompletion() {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    for (CancellablePromise<?> task : ourTasks) {
      waitForTask(task);
    }
  }

  @TestOnly
  private static void waitForTask(@Nonnull CancellablePromise<?> task) {
    int iteration = 0;
    while (!task.isDone() && iteration++ < 60_000) {
      UIUtil.dispatchAllInvocationEvents();
      try {
        task.blockingGet(1, TimeUnit.MILLISECONDS);
        return;
      }
      catch (TimeoutException ignore) {
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    if (!task.isDone()) {
      //noinspection UseOfSystemOutOrSystemErr
      System.err.println(ThreadDumper.dumpThreadsToString());
      throw new AssertionError("Too long async task");
    }
  }

}
