// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.text.BlockSupport;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSetQueue;
import com.intellij.util.ui.UIUtil;
import consulo.application.TransactionGuardEx;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.dataholder.UserDataHolderEx;
import jakarta.inject.Inject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.ide.PooledThreadExecutor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import jakarta.inject.Singleton;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Singleton
public final class DocumentCommitThread implements Runnable, Disposable, DocumentCommitProcessor {
  private static final Logger LOG = Logger.getInstance(DocumentCommitThread.class);
  private static final String SYNC_COMMIT_REASON = "Sync commit";

  private final ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Document Committing Pool", PooledThreadExecutor.INSTANCE, 1, this);
  private final Object lock = new Object();
  private final HashSetQueue<CommitTask> documentsToCommit = new HashSetQueue<>();      // guarded by lock
  private final HashSetQueue<CommitTask> documentsToApplyInEDT = new HashSetQueue<>();  // guarded by lock
  private volatile boolean isDisposed;
  private CommitTask currentTask; // guarded by lock
  private boolean myEnabled; // true if we can do commits. set to false temporarily during the write action.  guarded by lock

  public static DocumentCommitThread getInstance() {
    return (DocumentCommitThread)ServiceManager.getService(DocumentCommitProcessor.class);
  }

  @Inject
  DocumentCommitThread(Application application) {
    // install listener in EDT to avoid missing events in case we are inside write action right now
    application.invokeLater(() -> {
      if (application.isDisposed()) return;
      assert !application.isWriteAccessAllowed() || application.isUnitTestMode(); // crazy stuff happens in tests, e.g. UIUtil.dispatchInvocationEvents() inside write action
      application.addApplicationListener(new ApplicationListener() {
        @Override
        public void beforeWriteActionStart(@Nonnull Object action) {
          disable("Write action started: " + action);
        }

        @Override
        public void afterWriteActionFinished(@Nonnull Object action) {
          // crazy things happen when running tests, like starting write action in one thread but firing its end in the other
          enable("Write action finished: " + action);
        }
      }, this);

      enable("Listener installed, started");
    });
  }

  @Override
  public void dispose() {
    isDisposed = true;
    synchronized (lock) {
      documentsToCommit.clear();
    }
    cancel("Stop thread", false);
  }

  private void disable(@NonNls @Nonnull Object reason) {
    // write action has just started, all commits are useless
    synchronized (lock) {
      cancel(reason, true);
      myEnabled = false;
    }
    log(null, "disabled", null, reason);
  }

  private void enable(@NonNls @Nonnull Object reason) {
    synchronized (lock) {
      myEnabled = true;
      wakeUpQueue();
    }
    log(null, "enabled", null, reason);
  }

  // under lock
  private void wakeUpQueue() {
    if (!isDisposed && !documentsToCommit.isEmpty()) {
      executor.execute(this);
    }
  }

  private void cancel(@NonNls @Nonnull Object reason, boolean canReQueue) {
    startNewTask(null, reason, canReQueue);
  }

  @Override
  public void commitAsynchronously(@Nonnull final Project project, @Nonnull final Document document, @NonNls @Nonnull Object reason, @Nullable TransactionId context) {
    assert !isDisposed : "already disposed";

    if (!project.isInitialized()) return;
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = documentManager.getCachedPsiFile(document);
    if (psiFile == null || psiFile instanceof PsiCompiledElement) return;
    doQueue(project, document, reason, context, documentManager.getLastCommittedText(document));
  }

  private void doQueue(@Nonnull Project project, @Nonnull Document document, @Nonnull Object reason, @Nullable TransactionId context, @Nonnull CharSequence lastCommittedText) {
    synchronized (lock) {
      if (!project.isInitialized()) return;  // check the project is disposed under lock.
      CommitTask newTask = createNewTaskAndCancelSimilar(project, document, reason, context, lastCommittedText, false);

      documentsToCommit.offer(newTask);
      log(project, "Queued", newTask, reason);

      wakeUpQueue();
    }
  }

  @Nonnull
  private CommitTask createNewTaskAndCancelSimilar(@Nonnull Project project,
                                                   @Nonnull Document document,
                                                   @Nonnull Object reason,
                                                   @Nullable TransactionId context,
                                                   @Nonnull CharSequence lastCommittedText,
                                                   boolean canReQueue) {
    synchronized (lock) {
      CommitTask newTask = new CommitTask(project, document, createProgressIndicator(), reason, context, lastCommittedText);
      cancelAndRemoveFromDocsToCommit(newTask, reason, canReQueue);
      cancelAndRemoveCurrentTask(newTask, reason, canReQueue);
      cancelAndRemoveFromDocsToApplyInEDT(newTask, reason, canReQueue);

      return newTask;
    }
  }

  @SuppressWarnings("unused")
  private void log(Project project, @NonNls String msg, @Nullable CommitTask task, @NonNls Object... args) {
    //System.out.println(msg + "; task: "+task + "; args: "+StringUtil.first(Arrays.toString(args), 80, true));
  }


  // cancels all pending commits
  @TestOnly // under lock
  private void cancelAll() {
    String reason = "Cancel all in tests";
    cancel(reason, false);
    for (CommitTask commitTask : documentsToCommit) {
      commitTask.cancel(reason, false);
      log(commitTask.project, "Removed from background queue", commitTask);
    }
    documentsToCommit.clear();
    for (CommitTask commitTask : documentsToApplyInEDT) {
      commitTask.cancel(reason, false);
      log(commitTask.project, "Removed from EDT apply queue (sync commit called)", commitTask);
    }
    documentsToApplyInEDT.clear();
    CommitTask task = currentTask;
    if (task != null) {
      cancelAndRemoveFromDocsToCommit(task, reason, false);
    }
    cancel("Sync commit intervened", false);
    ((BoundedTaskExecutor)executor).clearAndCancelAll();
  }

  @TestOnly
  public void clearQueue() {
    synchronized (lock) {
      cancelAll();
      wakeUpQueue();
    }
  }

  private void cancelAndRemoveCurrentTask(@Nonnull CommitTask newTask, @Nonnull Object reason, boolean canReQueue) {
    CommitTask currentTask = this.currentTask;
    if (newTask.equals(currentTask)) {
      cancelAndRemoveFromDocsToCommit(currentTask, reason, canReQueue);
      cancel(reason, canReQueue);
    }
  }

  private void cancelAndRemoveFromDocsToApplyInEDT(@Nonnull CommitTask newTask, @Nonnull Object reason, boolean canReQueue) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToApplyInEDT, reason, canReQueue);
    if (removed) {
      log(newTask.project, "Removed from EDT apply queue", newTask);
    }
  }

  private void cancelAndRemoveFromDocsToCommit(@Nonnull final CommitTask newTask, @Nonnull Object reason, boolean canReQueue) {
    boolean removed = cancelAndRemoveFromQueue(newTask, documentsToCommit, reason, canReQueue);
    if (removed) {
      log(newTask.project, "Removed from background queue", newTask);
    }
  }

  private boolean cancelAndRemoveFromQueue(@Nonnull CommitTask newTask, @Nonnull HashSetQueue<CommitTask> queue, @Nonnull Object reason, boolean canReQueue) {
    CommitTask queuedTask = queue.find(newTask);
    if (queuedTask != null) {
      assert queuedTask != newTask;
      queuedTask.cancel(reason, canReQueue);
    }
    return queue.remove(newTask);
  }

  @Override
  public void run() {
    while (!isDisposed) {
      try {
        if (!pollQueue()) break;
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }

  // returns true if queue changed
  private boolean pollQueue() {
    assert !ApplicationManager.getApplication().isDispatchThread() : Thread.currentThread();
    CommitTask task;
    synchronized (lock) {
      if (!myEnabled || (task = documentsToCommit.poll()) == null) {
        return false;
      }

      Document document = task.getDocument();
      Project project = task.project;

      if (project.isDisposed() || !((PsiDocumentManagerBase)PsiDocumentManager.getInstance(project)).isInUncommittedSet(document)) {
        log(project, "Abandon and proceed to next", task);
        return true;
      }

      if (task.isCanceled() || task.dead) {
        return true; // document has been marked as removed, e.g. by synchronous commit
      }

      startNewTask(task, "Pulled new task", true);

      documentsToApplyInEDT.add(task);
    }

    boolean success = false;
    Object failureReason = null;
    try {
      if (!task.isCanceled()) {
        final CommitTask commitTask = task;
        final Ref<Pair<Runnable, Object>> result = new Ref<>();
        ProgressManager.getInstance().executeProcessUnderProgress(() -> result.set(commitUnderProgress(commitTask, false)), task.indicator);
        final Runnable finishRunnable = result.get().first;
        success = finishRunnable != null;
        failureReason = result.get().second;

        if (success) {
          assert !ApplicationManager.getApplication().isDispatchThread();
          TransactionGuardEx guard = (TransactionGuardEx)TransactionGuard.getInstance();
          guard.submitTransaction(task.project, task.myCreationContext, finishRunnable);
        }
      }
    }
    catch (ProcessCanceledException e) {
      cancel(e + " (indicator cancel reason: " + ((UserDataHolder)task.indicator).getUserData(CANCEL_REASON) + ")", true); // leave queue unchanged
      success = false;
      failureReason = e;
    }
    catch (Throwable e) {
      LOG.error(e); // unrecoverable
      cancel(e, false);
      failureReason = ExceptionUtil.getThrowableText(e);
    }

    if (!success) {
      Project project = task.project;
      Document document = task.document;
      String reQueuedReason = "re-added on failure: " + failureReason;
      ReadAction.run(() -> {
        if (project.isDisposed()) return;
        PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
        if (documentManager.isCommitted(document)) return; // sync commit hasn't intervened
        CharSequence lastCommittedText = documentManager.getLastCommittedText(document);
        PsiFile file = documentManager.getPsiFile(document);
        List<Pair<PsiFileImpl, FileASTNode>> oldFileNodes = file == null ? null : getAllFileNodes(file);
        if (oldFileNodes != null) {
          // somebody's told us explicitly not to beat the dead horse again,
          // e.g. when submitted the same document with the different transaction context
          if (task.dead) return;

          doQueue(project, document, reQueuedReason, task.myCreationContext, lastCommittedText);
        }
      });
    }
    synchronized (lock) {
      currentTask = null; // do not cancel, it's being invokeLatered
    }

    return true;
  }

  @Override
  public void commitSynchronously(@Nonnull Document document, @Nonnull Project project, @Nonnull PsiFile psiFile) {
    assert !isDisposed;

    if (!project.isInitialized() && !project.isDefault()) {
      @NonNls String s = project + "; Disposed: " + project.isDisposed() + "; Open: " + project.isOpen();
      try {
        Disposer.dispose(project);
      }
      catch (Throwable ignored) {
        // do not fill log with endless exceptions
      }
      throw new RuntimeException(s);
    }

    Lock documentLock = getDocumentLock(document);

    CommitTask task;
    synchronized (lock) {
      // synchronized to ensure no new similar tasks can start before we hold the document's lock
      task = createNewTaskAndCancelSimilar(project, document, SYNC_COMMIT_REASON, TransactionGuard.getInstance().getContextTransaction(),
                                           PsiDocumentManager.getInstance(project).getLastCommittedText(document), true);
    }

    documentLock.lock();
    try {
      assert !task.isCanceled();
      Pair<Runnable, Object> result = commitUnderProgress(task, true);
      Runnable finish = result.first;
      log(project, "Committed sync", task, finish, task.indicator);
      assert finish != null;

      finish.run();
    }
    finally {
      documentLock.unlock();
    }

    // will wake itself up on write action end
  }

  @Nonnull
  private static List<Pair<PsiFileImpl, FileASTNode>> getAllFileNodes(@Nonnull PsiFile file) {
    if (!file.isValid()) {
      throw new PsiInvalidElementAccessException(file, "File " + file + " is invalid, can't commit");
    }
    if (file instanceof PsiCompiledFile) {
      throw new IllegalArgumentException("Can't commit ClsFile: " + file);
    }

    return ContainerUtil.map(file.getViewProvider().getAllFiles(), root -> Pair.create((PsiFileImpl)root, root.getNode()));
  }

  @Nonnull
  private static ProgressIndicator createProgressIndicator() {
    return new StandardProgressIndicatorBase();
  }

  private void startNewTask(@Nullable CommitTask task, @Nonnull Object reason, boolean canReQueue) {
    synchronized (lock) { // sync to prevent overwriting
      CommitTask cur = currentTask;
      if (cur != null) {
        cur.cancel(reason, canReQueue);
      }
      currentTask = task;
    }
  }

  // returns (finish commit Runnable (to be invoked later in EDT), null) on success or (null, failure reason) on failure
  @Nonnull
  private Pair<Runnable, Object> commitUnderProgress(@Nonnull final CommitTask task, final boolean synchronously) {
    if (synchronously) {
      assert !task.isCanceled();
    }

    final Document document = task.getDocument();
    final Project project = task.project;
    final PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
    final List<BooleanRunnable> finishProcessors = new SmartList<>();
    List<BooleanRunnable> reparseInjectedProcessors = new SmartList<>();
    Runnable runnable = () -> {
      ApplicationManager.getApplication().assertReadAccessAllowed();
      if (project.isDisposed()) return;

      Lock lock = getDocumentLock(document);
      if (!lock.tryLock()) {
        task.cancel("Can't obtain document lock", true);
        return;
      }

      boolean canceled = false;
      try {
        if (documentManager.isCommitted(document)) return;

        if (!task.isStillValid()) {
          canceled = true;
          return;
        }

        FileViewProvider viewProvider = documentManager.getCachedViewProvider(document);
        if (viewProvider == null) {
          finishProcessors.add(handleCommitWithoutPsi(documentManager, task));
          return;
        }

        for (PsiFile file : viewProvider.getAllFiles()) {
          FileASTNode oldFileNode = file.getNode();
          ProperTextRange changedPsiRange = ChangedPsiRangeUtil.getChangedPsiRange(file, task.document, task.myLastCommittedText, document.getImmutableCharSequence());
          if (changedPsiRange != null) {
            BooleanRunnable finishProcessor = doCommit(task, file, oldFileNode, changedPsiRange, reparseInjectedProcessors);
            finishProcessors.add(finishProcessor);
          }
        }
      }
      finally {
        lock.unlock();
        if (canceled) {
          task.cancel("Task invalidated", false);
        }
      }
    };

    ApplicationEx app = (ApplicationEx)ApplicationManager.getApplication();
    if (!app.tryRunReadAction(runnable)) {
      log(project, "Could not start read action", task, app.isReadAccessAllowed(), Thread.currentThread());
      return new Pair<>(null, "Could not start read action");
    }

    boolean canceled = task.isCanceled();
    assert !synchronously || !canceled;
    if (canceled) {
      return new Pair<>(null, "Indicator was canceled");
    }

    Runnable result = createFinishCommitInEDTRunnable(task, synchronously, finishProcessors, reparseInjectedProcessors);
    return Pair.create(result, null);
  }

  @Nonnull
  private Runnable createFinishCommitInEDTRunnable(@Nonnull final CommitTask task,
                                                   final boolean synchronously,
                                                   @Nonnull List<? extends BooleanRunnable> finishProcessors,
                                                   @Nonnull List<? extends BooleanRunnable> reparseInjectedProcessors) {
    return () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      Document document = task.getDocument();
      Project project = task.project;
      PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(project);
      boolean committed = project.isDisposed() || documentManager.isCommitted(document);
      synchronized (lock) {
        documentsToApplyInEDT.remove(task);
        if (committed) {
          log(project, "Marked as already committed in EDT apply queue, return", task);
          return;
        }
      }

      boolean changeStillValid = task.isStillValid();
      boolean success = changeStillValid && documentManager.finishCommit(document, finishProcessors, reparseInjectedProcessors, synchronously, task.reason);
      if (synchronously) {
        assert success;
      }
      if (!changeStillValid) {
        log(project, "document changed; ignore", task);
        return;
      }
      if (synchronously || success) {
        assert !documentManager.isInUncommittedSet(document);
      }
      if (success) {
        log(project, "Commit finished", task);
      }
      else {
        // add document back to the queue
        commitAsynchronously(project, document, "Re-added back", task.myCreationContext);
      }
    };
  }

  @Nonnull
  private BooleanRunnable handleCommitWithoutPsi(@Nonnull final PsiDocumentManagerBase documentManager, @Nonnull final CommitTask task) {
    return () -> {
      log(task.project, "Finishing without PSI", task);
      Document document = task.getDocument();
      if (!task.isStillValid() || documentManager.getCachedViewProvider(document) != null) {
        return false;
      }

      documentManager.handleCommitWithoutPsi(document);
      return true;
    };
  }

  boolean isEnabled() {
    synchronized (lock) {
      return myEnabled;
    }
  }

  @Override
  public String toString() {
    return "Document commit thread; application: " + ApplicationManager.getApplication() + "; isDisposed: " + isDisposed + "; myEnabled: " + isEnabled();
  }

  @TestOnly
  @VisibleForTesting
  // waits for all tasks in 'documentsToCommit' queue to be finished, i.e. wait
  // - for 'commitUnderProgress' executed for all documents from there,
  // - for (potentially) a number of documents added to 'documentsToApplyInEDT'
  // - for these apply tasks (created in 'createFinishCommitInEDTRunnable') executed in EDT
  // NB: failures applying EDT tasks are not handled - i.e. failed documents are added back to the queue and the method returns
  public void waitForAllCommits(long timeout, @Nonnull TimeUnit timeUnit) throws ExecutionException, InterruptedException, TimeoutException {
    ApplicationManager.getApplication().assertIsDispatchThread();
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();

    ((BoundedTaskExecutor)executor).waitAllTasksExecuted(timeout, timeUnit);
    UIUtil.dispatchAllInvocationEvents();
    disable("waitForAllCommits() called in the tearDown()");
  }

  private static final Key<Object> CANCEL_REASON = Key.create("CANCEL_REASON");

  private class CommitTask {
    @Nonnull
    private final Document document;
    @Nonnull
    final Project project;
    private final int modificationSequence; // store initial document modification sequence here to check if it changed later before commit in EDT

    // when queued it's not started
    // when dequeued it's started
    // when failed it's canceled
    @Nonnull
    final ProgressIndicator indicator; // progress to commit this doc under.
    @Nonnull
    final Object reason;
    @Nullable
    final TransactionId myCreationContext;
    private final CharSequence myLastCommittedText;
    private volatile boolean dead; // the task was explicitly removed from the queue; no attempts to re-queue should be made

    CommitTask(@Nonnull final Project project,
               @Nonnull final Document document,
               @Nonnull ProgressIndicator indicator,
               @Nonnull Object reason,
               @Nullable TransactionId context,
               @Nonnull CharSequence lastCommittedText) {
      this.document = document;
      this.project = project;
      this.indicator = indicator;
      this.reason = reason;
      myCreationContext = context;
      myLastCommittedText = lastCommittedText;
      modificationSequence = ((DocumentEx)document).getModificationSequence();
    }

    @NonNls
    @Override
    public String toString() {
      Document document = getDocument();
      String indicatorInfo = isCanceled() ? " (Canceled: " + ((UserDataHolder)indicator).getUserData(CANCEL_REASON) + ")" : "";
      String removedInfo = dead ? " (dead)" : "";
      String reasonInfo = " task reason: " +
                          StringUtil.first(String.valueOf(reason), 180, true) +
                          (isStillValid() ? "" : "; changed: old seq=" + modificationSequence + ", new seq=" + ((DocumentEx)document).getModificationSequence());
      String contextInfo = " Context: " + myCreationContext;
      return System.identityHashCode(this) + "; " + indicatorInfo + removedInfo + contextInfo + reasonInfo;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof CommitTask)) return false;

      CommitTask task = (CommitTask)o;

      return Comparing.equal(getDocument(), task.getDocument()) && project.equals(task.project);
    }

    @Override
    public int hashCode() {
      int result = getDocument().hashCode();
      result = 31 * result + project.hashCode();
      return result;
    }

    boolean isStillValid() {
      Document document = getDocument();
      return ((DocumentEx)document).getModificationSequence() == modificationSequence;
    }

    private void cancel(@Nonnull Object reason, boolean canReQueue) {
      dead |= !canReQueue; // set the flag before cancelling indicator
      if (!isCanceled()) {
        log(project, "cancel", this, reason);

        indicator.cancel();
        ((UserDataHolder)indicator).putUserData(CANCEL_REASON, reason);

        synchronized (lock) {
          documentsToCommit.remove(this);
          documentsToApplyInEDT.remove(this);
        }
      }
    }

    @Nonnull
    Document getDocument() {
      return document;
    }

    private boolean isCanceled() {
      return indicator.isCanceled();
    }
  }

  // returns runnable to execute under write action in AWT to finish the commit, updates "outChangedRange"
  @Nonnull
  private static BooleanRunnable doCommit(@Nonnull final CommitTask task,
                                          @Nonnull final PsiFile file,
                                          @Nonnull final FileASTNode oldFileNode,
                                          @Nonnull ProperTextRange changedPsiRange,
                                          @Nonnull List<? super BooleanRunnable> outReparseInjectedProcessors) {
    Document document = task.getDocument();
    final CharSequence newDocumentText = document.getImmutableCharSequence();

    final Boolean data = document.getUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY);
    if (data != null) {
      document.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, data);
    }

    PsiDocumentManagerBase documentManager = (PsiDocumentManagerBase)PsiDocumentManager.getInstance(task.project);

    DiffLog diffLog;
    try (BlockSupportImpl.ReparseResult result = BlockSupportImpl.reparse(file, oldFileNode, changedPsiRange, newDocumentText, task.indicator, task.myLastCommittedText)) {
      diffLog = result.log;


      List<BooleanRunnable> injectedRunnables = documentManager.reparseChangedInjectedFragments(document, file, changedPsiRange, task.indicator, result.oldRoot, result.newRoot);
      outReparseInjectedProcessors.addAll(injectedRunnables);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return () -> {
        documentManager.forceReload(file.getViewProvider().getVirtualFile(), file.getViewProvider());
        return true;
      };
    }

    return () -> {
      FileViewProvider viewProvider = file.getViewProvider();
      Document document1 = task.getDocument();
      if (!task.isStillValid() || ((PsiDocumentManagerBase)PsiDocumentManager.getInstance(file.getProject())).getCachedViewProvider(document1) != viewProvider) {
        return false; // optimistic locking failed
      }

      if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
        VirtualFile vFile = viewProvider.getVirtualFile();
        LOG.error("Write action expected" +
                  "; document=" +
                  document1 +
                  "; file=" +
                  file +
                  " of " +
                  file.getClass() +
                  "; file.valid=" +
                  file.isValid() +
                  "; file.eventSystemEnabled=" +
                  viewProvider.isEventSystemEnabled() +
                  "; viewProvider=" +
                  viewProvider +
                  " of " +
                  viewProvider.getClass() +
                  "; language=" +
                  file.getLanguage() +
                  "; vFile=" +
                  vFile +
                  " of " +
                  vFile.getClass() +
                  "; free-threaded=" +
                  AbstractFileViewProvider.isFreeThreaded(viewProvider));
      }

      diffLog.doActualPsiChange(file);

      assertAfterCommit(document1, file, (FileElement)oldFileNode);

      return true;
    };
  }

  private static void assertAfterCommit(@Nonnull Document document, @Nonnull final PsiFile file, @Nonnull FileElement oldFileNode) {
    if (oldFileNode.getTextLength() != document.getTextLength()) {
      final String documentText = document.getText();
      String fileText = file.getText();
      boolean sameText = Comparing.equal(fileText, documentText);
      String errorMessage = "commitDocument() left PSI inconsistent: " +
                            DebugUtil.diagnosePsiDocumentInconsistency(file, document) +
                            "; node.length=" +
                            oldFileNode.getTextLength() +
                            "; doc.text" +
                            (sameText ? "==" : "!=") +
                            "file.text" +
                            "; file name:" +
                            file.getName() +
                            "; type:" +
                            file.getFileType() +
                            "; lang:" +
                            file.getLanguage();
      //PluginException.logPluginError(LOG, errorMessage, null, file.getLanguage().getClass());
      LOG.error(errorMessage);

      file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, Boolean.TRUE);
      try {
        BlockSupport blockSupport = BlockSupport.getInstance(file.getProject());
        final DiffLog diffLog = blockSupport.reparseRange(file, file.getNode(), new TextRange(0, documentText.length()), documentText, createProgressIndicator(), oldFileNode.getText());
        diffLog.doActualPsiChange(file);

        if (oldFileNode.getTextLength() != document.getTextLength()) {
          LOG.error("PSI is broken beyond repair in: " + file);
          //PluginException.logPluginError(LOG, "PSI is broken beyond repair in: " + file, null, file.getLanguage().getClass());
        }
      }
      finally {
        file.putUserData(BlockSupport.DO_NOT_REPARSE_INCREMENTALLY, null);
      }
    }
  }

  /**
   * @return an internal lock object to prevent read & write phases of commit from running simultaneously for free-threaded PSI
   */
  private static Lock getDocumentLock(Document document) {
    Lock lock = document.getUserData(DOCUMENT_LOCK);
    return lock != null ? lock : ((UserDataHolderEx)document).putUserDataIfAbsent(DOCUMENT_LOCK, new ReentrantLock());
  }

  private static final Key<Lock> DOCUMENT_LOCK = Key.create("DOCUMENT_LOCK");

  void cancelTasksOnProjectDispose(@Nonnull final Project project) {
    synchronized (lock) {
      cancelTasksOnProjectDispose(project, documentsToCommit);
      cancelTasksOnProjectDispose(project, documentsToApplyInEDT);
    }
  }

  private void cancelTasksOnProjectDispose(@Nonnull Project project, @Nonnull HashSetQueue<CommitTask> queue) {
    for (HashSetQueue.PositionalIterator<CommitTask> iterator = queue.iterator(); iterator.hasNext(); ) {
      CommitTask commitTask = iterator.next();
      if (commitTask.project == project) {
        iterator.remove();
        commitTask.cancel("project is disposed", false);
      }
    }
  }
}
