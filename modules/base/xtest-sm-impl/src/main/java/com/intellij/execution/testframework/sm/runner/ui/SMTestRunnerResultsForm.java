/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.TestStateStorage;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.*;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.testframework.export.TestResultsXmlFormatter;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.TestHistoryConfiguration;
import com.intellij.execution.testframework.sm.runner.*;
import com.intellij.execution.testframework.sm.runner.history.ImportedTestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.ui.statistics.StatisticsPanel;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.execution.testframework.ui.TestsProgressAnimator;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.progress.util.ColorProgressBar;
import com.intellij.openapi.project.Project;
import consulo.disposer.Disposer;
import com.intellij.openapi.util.Pass;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.pom.Navigatable;
import com.intellij.ui.JBColor;
import com.intellij.util.Alarm;
import com.intellij.util.OpenSourceUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NonNls;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

/**
 * @author: Roman Chernyatchik
 */
public class SMTestRunnerResultsForm extends TestResultsPanel implements TestFrameworkRunningModel, TestResultsViewer, SMTRunnerEventsListener {
  @NonNls public static final String HISTORY_DATE_FORMAT = "yyyy.MM.dd 'at' HH'h' mm'm' ss's'";
  @NonNls private static final String DEFAULT_SM_RUNNER_SPLITTER_PROPERTY = "SMTestRunner.Splitter.Proportion";

  public static final Color DARK_YELLOW = JBColor.YELLOW.darker();
  private static final Logger LOG = Logger.getInstance(SMTestRunnerResultsForm.class);

  private SMTRunnerTestTreeView myTreeView;

  private TestsProgressAnimator myAnimator;

  /**
   * Fake parent suite for all tests and suites
   */
  private final SMTestProxy.SMRootTestProxy myTestsRootNode;
  private SMTRunnerTreeBuilder myTreeBuilder;

  private final List<EventsListener> myEventListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private PropagateSelectionHandler myShowStatisticForProxyHandler;

  private final Project myProject;

  private int myTotalTestCount = 0;
  private int myStartedTestCount = 0;
  private int myFinishedTestCount = 0;
  private int myFailedTestCount = 0;
  private int myIgnoredTestCount = 0;
  private long myStartTime;
  private long myEndTime;
  private StatisticsPanel myStatisticsPane;

  // custom progress
  private String myCurrentCustomProgressCategory;
  private final Set<String> myMentionedCategories = new LinkedHashSet<String>();
  private boolean myTestsRunning = true;
  private AbstractTestProxy myLastSelected;
  private Alarm myUpdateQueue;
  private Set<Update> myRequests = Collections.synchronizedSet(new HashSet<Update>());
  private boolean myDisposed = false;

  public SMTestRunnerResultsForm(@Nonnull final JComponent console, final TestConsoleProperties consoleProperties) {
    this(console, AnAction.EMPTY_ARRAY, consoleProperties, null);
  }

  public SMTestRunnerResultsForm(@Nonnull final JComponent console,
                                 AnAction[] consoleActions,
                                 final TestConsoleProperties consoleProperties,
                                 @Nullable String splitterPropertyName) {
    super(console, consoleActions, consoleProperties, StringUtil.notNullize(splitterPropertyName, DEFAULT_SM_RUNNER_SPLITTER_PROPERTY), 0.2f);
    myProject = consoleProperties.getProject();

    //Create tests common suite root
    //noinspection HardCodedStringLiteral
    myTestsRootNode = new SMTestProxy.SMRootTestProxy();
    //todo myTestsRootNode.setOutputFilePath(runConfiguration.getOutputFilePath());

    // Fire selection changed and move focus on SHIFT+ENTER
    //TODO[romeo] improve
    /*
    final ArrayList<Component> components = new ArrayList<Component>();
    components.add(myTreeView);
    components.add(myTabs.getComponent());
    myContentPane.setFocusTraversalPolicy(new MyFocusTraversalPolicy(components));
    myContentPane.setFocusCycleRoot(true);
    */
  }

  @Override
  public void initUI() {
    super.initUI();

    if (Registry.is("tests.view.old.statistics.panel")) {
      final KeyStroke shiftEnterKey = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_MASK);
      SMRunnerUtil.registerAsAction(shiftEnterKey, "show-statistics-for-test-proxy", new Runnable() {
        @Override
        public void run() {
          showStatisticsForSelectedProxy();
        }
      }, myTreeView);
    }
  }

  @Override
  protected ToolbarPanel createToolbarPanel() {
    return new SMTRunnerToolbarPanel(myProperties, this, this);
  }

  @Override
  protected JComponent createTestTreeView() {
    myTreeView = new SMTRunnerTestTreeView();

    myTreeView.setLargeModel(true);
    myTreeView.attachToModel(this);
    myTreeView.setTestResultsViewer(this);
    if (Registry.is("tests.view.old.statistics.panel")) {
      addTestsTreeSelectionListener(new TreeSelectionListener() {
        @Override
        public void valueChanged(TreeSelectionEvent e) {
          AbstractTestProxy selectedTest = getTreeView().getSelectedTest();
          if (selectedTest instanceof SMTestProxy) {
            myStatisticsPane.selectProxy(((SMTestProxy)selectedTest), this, false);
          }
        }
      });
    }

    final SMTRunnerTreeStructure structure = new SMTRunnerTreeStructure(myProject, myTestsRootNode);
    myTreeBuilder = new SMTRunnerTreeBuilder(myTreeView, structure);
    myTreeBuilder.setTestsComparator(TestConsoleProperties.SORT_ALPHABETICALLY.value(myProperties));
    Disposer.register(this, myTreeBuilder);

    myAnimator = new TestsProgressAnimator(myTreeBuilder);

    TrackRunningTestUtil.installStopListeners(myTreeView, myProperties, new Pass<AbstractTestProxy>() {
      @Override
      public void pass(AbstractTestProxy testProxy) {
        if (testProxy == null) return;
        final AbstractTestProxy selectedProxy = testProxy;
        //drill to the first leaf
        while (!testProxy.isLeaf()) {
          final List<? extends AbstractTestProxy> children = testProxy.getChildren();
          if (!children.isEmpty()) {
            final AbstractTestProxy firstChild = children.get(0);
            if (firstChild != null) {
              testProxy = firstChild;
              continue;
            }
          }
          break;
        }

        //pretend the selection on the first leaf
        //so if test would be run, tracking would be restarted
        myLastSelected = testProxy;

        //ensure scroll to source on explicit selection only
        if (ScrollToTestSourceAction.isScrollEnabled(SMTestRunnerResultsForm.this)) {
          final Navigatable descriptor = TestsUIUtil.getOpenFileDescriptor(selectedProxy, SMTestRunnerResultsForm.this);
          if (descriptor != null) {
            OpenSourceUtil.navigate(false, descriptor);
          }
        }
      }
    });

    //TODO always hide root node
    //myTreeView.setRootVisible(false);
    myUpdateQueue = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);
    return myTreeView;
  }

  @Override
  protected JComponent createStatisticsPanel() {
    // Statistics tab
    final StatisticsPanel statisticsPane = new StatisticsPanel(myProject, this);
    // handler to select in results viewer by statistics pane events
    statisticsPane.addPropagateSelectionListener(createSelectMeListener());
    // handler to select test statistics pane by result viewer events
    setShowStatisticForProxyHandler(statisticsPane.createSelectMeListener());

    myStatisticsPane = statisticsPane;
    return myStatisticsPane.getContentPane();
  }

  public StatisticsPanel getStatisticsPane() {
    return myStatisticsPane;
  }

  public void addTestsTreeSelectionListener(final TreeSelectionListener listener) {
    myTreeView.getSelectionModel().addTreeSelectionListener(listener);
  }

  /**
   * Is used for navigation from tree view to other UI components
   *
   * @param handler
   */
  @Override
  public void setShowStatisticForProxyHandler(final PropagateSelectionHandler handler) {
    myShowStatisticForProxyHandler = handler;
  }

  /**
   * Returns root node, fake parent suite for all tests and suites
   *
   * @param testsRoot
   * @return
   */
  @Override
  public void onTestingStarted(@Nonnull SMTestProxy.SMRootTestProxy testsRoot) {
    myAnimator.setCurrentTestCase(myTestsRootNode);
    myTreeBuilder.updateFromRoot();

    // Status line
    myStatusLine.setStatusColor(ColorProgressBar.GREEN);

    // Tests tree
    selectAndNotify(myTestsRootNode);

    myStartTime = System.currentTimeMillis();
    boolean printTestingStartedTime = true;
    if (myProperties instanceof SMTRunnerConsoleProperties) {
      printTestingStartedTime = ((SMTRunnerConsoleProperties)myProperties).isPrintTestingStartedTime();
    }
    if (printTestingStartedTime) {
      myTestsRootNode.addSystemOutput("Testing started at " + DateFormatUtil.formatTime(myStartTime) + " ...\n");
    }

    updateStatusLabel(false);

    // TODO : show info - "Loading..." msg

    fireOnTestingStarted();
  }

  @Override
  public void onTestingFinished(@Nonnull SMTestProxy.SMRootTestProxy testsRoot) {
    myEndTime = System.currentTimeMillis();

    if (myTotalTestCount == 0) {
      myTotalTestCount = myStartedTestCount;
      myStatusLine.setFraction(1);
    }

    updateStatusLabel(true);
    updateIconProgress(true);

    myAnimator.stopMovie();
    myTreeBuilder.updateFromRoot();

    LvcsHelper.addLabel(this);


    final Runnable onDone = new Runnable() {
      @Override
      public void run() {
        myTestsRunning = false;
        final boolean sortByDuration = TestConsoleProperties.SORT_BY_DURATION.value(myProperties);
        if (sortByDuration) {
          myTreeBuilder.setStatisticsComparator(myProperties, sortByDuration);
        }
      }
    };
    if (myLastSelected == null) {
      selectAndNotify(myTestsRootNode, onDone);
    }
    else {
      onDone.run();
    }

    fireOnTestingFinished();

    if (testsRoot.wasTerminated() && myStatusLine.getStatusColor() == ColorProgressBar.GREEN) {
      myStatusLine.setStatusColor(JBColor.LIGHT_GRAY);
    }

    if (testsRoot.isEmptySuite() &&
        testsRoot.isTestsReporterAttached() &&
        myProperties instanceof SMTRunnerConsoleProperties &&
        ((SMTRunnerConsoleProperties)myProperties).fixEmptySuite()) {
      return;
    }
    final TestsUIUtil.TestResultPresentation presentation = new TestsUIUtil.TestResultPresentation(testsRoot, myStartTime > 0, null)
            .getPresentation(myFailedTestCount, Math.max(0, myFinishedTestCount - myFailedTestCount - myIgnoredTestCount),
                             myTotalTestCount - myFinishedTestCount, myIgnoredTestCount);
    TestsUIUtil.notifyByBalloon(myProperties.getProject(), testsRoot, myProperties, presentation);
    addToHistory(testsRoot, myProperties, this);
  }

  private void addToHistory(final SMTestProxy.SMRootTestProxy root, TestConsoleProperties consoleProperties, Disposable parentDisposable) {
    final RunProfile configuration = consoleProperties.getConfiguration();
    if (configuration instanceof RunConfiguration &&
        !(consoleProperties instanceof ImportedTestConsoleProperties) &&
        !ApplicationManager.getApplication().isUnitTestMode() &&
        !myDisposed) {
      final MySaveHistoryTask backgroundable = new MySaveHistoryTask(consoleProperties, root, (RunConfiguration)configuration);
      final BackgroundableProcessIndicator processIndicator = new BackgroundableProcessIndicator(backgroundable);
      Disposer.register(parentDisposable, new Disposable() {
        @Override
        public void dispose() {
          processIndicator.cancel();
          backgroundable.dispose();
        }
      });
      Disposer.register(parentDisposable, processIndicator);
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(backgroundable, processIndicator);
    }
  }

  @Override
  public void onTestsCountInSuite(final int count) {
    updateCountersAndProgressOnTestCount(count, false);
  }

  /**
   * Adds test to tree and updates status line.
   * Test proxy should be initialized, proxy parent must be some suite (already added to tree)
   *
   * @param testProxy Proxy
   */
  @Override
  public void onTestStarted(@Nonnull final SMTestProxy testProxy) {
    if (!testProxy.isConfig()) {
      updateOnTestStarted(false);
    }
    _addTestOrSuite(testProxy);
    fireOnTestNodeAdded(testProxy);
  }

  @Override
  public void onSuiteTreeNodeAdded(SMTestProxy testProxy) {
    myTotalTestCount++;
  }

  @Override
  public void onSuiteTreeStarted(SMTestProxy suite) {
  }

  @Override
  public void onTestFailed(@Nonnull final SMTestProxy test) {
    updateOnTestFailed(false);
    if (test.isConfig()) {
      myStartedTestCount++;
      myFinishedTestCount++;
    }
    updateIconProgress(false);

    //still expand failure when user selected another test
    if (myLastSelected != null &&
        TestConsoleProperties.TRACK_RUNNING_TEST.value(myProperties) &&
        TestConsoleProperties.HIDE_PASSED_TESTS.value(myProperties)) {
      myTreeBuilder.expand(test, null);
    }
  }

  @Override
  public void onTestIgnored(@Nonnull final SMTestProxy test) {
    updateOnTestIgnored();
  }

  /**
   * Adds suite to tree
   * Suite proxy should be initialized, proxy parent must be some suite (already added to tree)
   * If parent is null, then suite will be added to tests root.
   *
   * @param newSuite Tests suite
   */
  @Override
  public void onSuiteStarted(@Nonnull final SMTestProxy newSuite) {
    _addTestOrSuite(newSuite);
  }

  @Override
  public void onCustomProgressTestsCategory(@Nullable String categoryName, int testCount) {
    myCurrentCustomProgressCategory = categoryName;
    updateCountersAndProgressOnTestCount(testCount, true);
  }

  @Override
  public void onCustomProgressTestStarted() {
    updateOnTestStarted(true);
  }

  @Override
  public void onCustomProgressTestFailed() {
    updateOnTestFailed(true);
  }

  @Override
  public void onCustomProgressTestFinished() {
    updateOnTestFinished(true);
  }

  @Override
  public void onTestFinished(@Nonnull final SMTestProxy test) {
    if (!test.isConfig()) {
      updateOnTestFinished(false);
    }
    updateIconProgress(false);
  }

  @Override
  public void onSuiteFinished(@Nonnull final SMTestProxy suite) {
    //Do nothing
  }

  @Override
  public SMTestProxy.SMRootTestProxy getTestsRootNode() {
    return myTestsRootNode;
  }

  @Override
  public TestConsoleProperties getProperties() {
    return myProperties;
  }

  @Override
  public void setFilter(final Filter filter) {
    // is used by Test Runner actions, e.g. hide passed, etc
    final SMTRunnerTreeStructure treeStructure = myTreeBuilder.getSMRunnerTreeStructure();
    treeStructure.setFilter(filter);

    // TODO - show correct info if no children are available
    // (e.g no tests found or all tests passed, etc.)
    // treeStructure.getChildElements(treeStructure.getRootElement()).length == 0

    myTreeBuilder.queueUpdate();
  }

  @Override
  public boolean isRunning() {
    return myTestsRunning;
  }

  @Override
  public TestTreeView getTreeView() {
    return myTreeView;
  }

  @Override
  public SMTRunnerTreeBuilder getTreeBuilder() {
    return myTreeBuilder;
  }

  @Override
  public boolean hasTestSuites() {
    return getRoot().getChildren().size() > 0;
  }

  @Override
  @Nonnull
  public AbstractTestProxy getRoot() {
    return myTestsRootNode;
  }

  /**
   * Manual test proxy selection in tests tree. E.g. do select root node on
   * testing started or do select current node if TRACK_RUNNING_TEST is enabled
   * <p>
   * <p>
   * Will select proxy in Event Dispatch Thread. Invocation of this
   * method may be not in event dispatch thread
   *
   * @param testProxy Test or suite
   */
  @Override
  public void selectAndNotify(AbstractTestProxy testProxy) {
    selectAndNotify(testProxy, null);
  }

  private void selectAndNotify(@Nullable final AbstractTestProxy testProxy, @Nullable Runnable onDone) {
    selectWithoutNotify(testProxy, onDone);

    // Is used by Statistic tab to differ use selection in tree
    // from manual selection from API (e.g. test runner events)
    if (Registry.is("tests.view.old.statistics.panel")) {
      showStatisticsForSelectedProxy(testProxy, false);
    }
  }

  @Override
  public void addEventsListener(final EventsListener listener) {
    myEventListeners.add(listener);
    addTestsTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(final TreeSelectionEvent e) {
        //We should fire event only if it was generated by this component,
        //e.g. it is focused. Otherwise it is side effect of selecting proxy in
        //try by other component
        //if (myTreeView.isFocusOwner()) {
        @Nullable final SMTestProxy selectedProxy = (SMTestProxy)getTreeView().getSelectedTest();
        listener.onSelected(selectedProxy, SMTestRunnerResultsForm.this, SMTestRunnerResultsForm.this);
        //}
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
    myShowStatisticForProxyHandler = null;
    myEventListeners.clear();
    myStatisticsPane.doDispose();
    myDisposed = true;
  }

  @Override
  public void showStatisticsForSelectedProxy() {
    TestConsoleProperties.SHOW_STATISTICS.set(myProperties, true);
    final AbstractTestProxy selectedProxy = myTreeView.getSelectedTest();
    showStatisticsForSelectedProxy(selectedProxy, true);
  }

  private void showStatisticsForSelectedProxy(final AbstractTestProxy selectedProxy, final boolean requestFocus) {
    if (selectedProxy instanceof SMTestProxy && myShowStatisticForProxyHandler != null) {
      myShowStatisticForProxyHandler.handlePropagateSelectionRequest((SMTestProxy)selectedProxy, this, requestFocus);
    }
  }

  protected int getTotalTestCount() {
    return myTotalTestCount;
  }

  protected int getStartedTestCount() {
    return myStartedTestCount;
  }

  protected int getFinishedTestCount() {
    return myFinishedTestCount;
  }

  protected int getFailedTestCount() {
    return myFailedTestCount;
  }

  protected int getIgnoredTestCount() {
    return myIgnoredTestCount;
  }

  protected Color getTestsStatusColor() {
    return myStatusLine.getStatusColor();
  }

  public Set<String> getMentionedCategories() {
    return myMentionedCategories;
  }

  protected long getStartTime() {
    return myStartTime;
  }

  protected long getEndTime() {
    return myEndTime;
  }

  private void _addTestOrSuite(@Nonnull final SMTestProxy newTestOrSuite) {

    final SMTestProxy parentSuite = newTestOrSuite.getParent();
    assert parentSuite != null;

    // Tree
    final Update update = new Update(parentSuite) {
      @Override
      public void run() {
        myRequests.remove(this);
        myTreeBuilder.updateTestsSubtree(parentSuite);
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
    }
    else if (myRequests.add(update) && !myUpdateQueue.isDisposed()) {
      myUpdateQueue.addRequest(update, 100);
    }
    myTreeBuilder.repaintWithParents(newTestOrSuite);

    myAnimator.setCurrentTestCase(newTestOrSuite);

    if (TestConsoleProperties.TRACK_RUNNING_TEST.value(myProperties)) {
      if (myLastSelected == null || myLastSelected == newTestOrSuite) {
        myLastSelected = null;
        selectAndNotify(newTestOrSuite);
      }
    }
  }

  private void fireOnTestNodeAdded(final SMTestProxy test) {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestNodeAdded(this, test);
    }
  }

  private void fireOnTestingFinished() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingFinished(this);
    }
  }

  private void fireOnTestingStarted() {
    for (EventsListener eventListener : myEventListeners) {
      eventListener.onTestingStarted(this);
    }
  }

  private void selectWithoutNotify(final AbstractTestProxy testProxy, @Nullable final Runnable onDone) {
    if (testProxy == null) {
      return;
    }

    SMRunnerUtil.runInEventDispatchThread(new Runnable() {
      @Override
      public void run() {
        if (myTreeBuilder.isDisposed()) {
          return;
        }
        myTreeBuilder.select(testProxy, onDone);
      }
    }, ModalityState.NON_MODAL);
  }

  private void updateStatusLabel(final boolean testingFinished) {
    if (myFailedTestCount > 0) {
      myStatusLine.setStatusColor(ColorProgressBar.RED);
    }

    if (testingFinished) {
      if (myTotalTestCount == 0) {
        myStatusLine.setStatusColor(myTestsRootNode.wasLaunched() || !myTestsRootNode.isTestsReporterAttached() ? JBColor.LIGHT_GRAY : ColorProgressBar.RED);
      }
      // else color will be according failed/passed tests
    }

    // launchedAndFinished - is launched and not in progress. If we remove "launched' that onTestingStarted() before
    // initializing will be "launchedAndFinished"
    final boolean launchedAndFinished = myTestsRootNode.wasLaunched() && !myTestsRootNode.isInProgress();
    if (!TestsPresentationUtil.hasNonDefaultCategories(myMentionedCategories)) {
      myStatusLine.formatTestMessage(myTotalTestCount, myFinishedTestCount, myFailedTestCount, myIgnoredTestCount, myTestsRootNode.getDuration(), myEndTime);
    }
    else {
      myStatusLine.setText(TestsPresentationUtil.getProgressStatus_Text(myStartTime, myEndTime, myTotalTestCount, myFinishedTestCount, myFailedTestCount,
                                                                        myMentionedCategories, launchedAndFinished));
    }
  }

  /**
   * for java unit tests
   */
  public void performUpdate() {
    myTreeBuilder.performUpdate();
  }

  private void updateIconProgress(boolean updateWithAttention) {
    final int totalTestCount, doneTestCount;
    if (myTotalTestCount == 0) {
      totalTestCount = 2;
      doneTestCount = 1;
    }
    else {
      totalTestCount = myTotalTestCount;
      doneTestCount = myFinishedTestCount;
    }
    TestsUIUtil.showIconProgress(myProject, doneTestCount, totalTestCount, myFailedTestCount, updateWithAttention);
  }

  /**
   * On event change selection and probably requests focus. Is used when we want
   * navigate from other component to this
   *
   * @return Listener
   */
  public PropagateSelectionHandler createSelectMeListener() {
    return new PropagateSelectionHandler() {
      @Override
      public void handlePropagateSelectionRequest(@javax.annotation.Nullable final SMTestProxy selectedTestProxy, @Nonnull final Object sender, final boolean requestFocus) {
        SMRunnerUtil.addToInvokeLater(new Runnable() {
          @Override
          public void run() {
            selectWithoutNotify(selectedTestProxy, null);

            // Request focus if necessary
            if (requestFocus) {
              //myTreeView.requestFocusInWindow();
              IdeFocusManager.getInstance(myProject).requestFocus(myTreeView, true);
            }
          }
        });
      }
    };
  }


  private void updateCountersAndProgressOnTestCount(final int count, final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    //This is for better support groups of TestSuites
    //Each group notifies about it's size
    myTotalTestCount += count;
    updateStatusLabel(false);
  }

  private void updateOnTestStarted(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;

    // for mixed tests results : mention category only if it contained tests
    myMentionedCategories.add(myCurrentCustomProgressCategory != null ? myCurrentCustomProgressCategory : TestsPresentationUtil.DEFAULT_TESTS_CATEGORY);

    myStartedTestCount++;

    // fix total count if it is corrupted
    // but if test count wasn't set at all let's process such case separately
    if (myStartedTestCount > myTotalTestCount && myTotalTestCount != 0) {
      myTotalTestCount = myStartedTestCount;
    }

    updateStatusLabel(false);
  }

  private void updateProgressOnTestDone() {
    int doneTestCount = myFinishedTestCount;
    // update progress
    if (myTotalTestCount != 0) {
      // if total is set
      myStatusLine.setFraction((double)doneTestCount / myTotalTestCount);
    }
    else {
      // if at least one test was launcher than just set progress in the middle to show user that tests are running
      myStatusLine.setFraction(doneTestCount > 0 ? 0.5 : 0);
    }
  }

  private void updateOnTestFailed(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFailedTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private void updateOnTestFinished(final boolean isCustomMessage) {
    if (!isModeConsistent(isCustomMessage)) return;
    myFinishedTestCount++;
    updateProgressOnTestDone();
  }

  private void updateOnTestIgnored() {
    myIgnoredTestCount++;
    updateProgressOnTestDone();
    updateStatusLabel(false);
  }

  private boolean isModeConsistent(boolean isCustomMessage) {
    // check that we are in consistent mode
    return isCustomMessage != (myCurrentCustomProgressCategory == null);
  }

  private static class MySaveHistoryTask extends Task.Backgroundable {

    private final TestConsoleProperties myConsoleProperties;
    private SMTestProxy.SMRootTestProxy myRoot;
    private RunConfiguration myConfiguration;
    private File myOutputFile;

    public MySaveHistoryTask(TestConsoleProperties consoleProperties, SMTestProxy.SMRootTestProxy root, RunConfiguration configuration) {
      super(consoleProperties.getProject(), "Save Test Results", true);
      myConsoleProperties = consoleProperties;
      myRoot = root;
      myConfiguration = configuration;
    }

    @Override
    public void run(@Nonnull ProgressIndicator indicator) {
      writeState();
      DaemonCodeAnalyzer.getInstance(getProject()).restart();
      try {
        SAXTransformerFactory transformerFactory = (SAXTransformerFactory)TransformerFactory.newInstance();
        TransformerHandler handler = transformerFactory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "yes");
        handler.getTransformer().setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        final String configurationNameIncludedDate = PathUtil.suggestFileName(myConfiguration.getName()) + " - " +
                                                     new SimpleDateFormat(HISTORY_DATE_FORMAT).format(new Date());

        myOutputFile = new File(TestStateStorage.getTestHistoryRoot(myProject), configurationNameIncludedDate + ".xml");
        FileUtilRt.createParentDirs(myOutputFile);
        handler.setResult(new StreamResult(new FileWriter(myOutputFile)));
        final SMTestProxy.SMRootTestProxy root = myRoot;
        final RunConfiguration configuration = myConfiguration;
        if (root != null && configuration != null) {
          TestResultsXmlFormatter.execute(root, configuration, myConsoleProperties, handler);
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception e) {
        LOG.info("Export to history failed", e);
      }
    }

    private void writeState() {
      // read action to prevent project (and storage) from being disposed
      ApplicationManager.getApplication().runReadAction(() -> {
        Project project = getProject();
        if (project.isDisposed()) return;
        TestStateStorage storage = TestStateStorage.getInstance(project);
        List<SMTestProxy> tests = myRoot.getAllTests();
        for (SMTestProxy proxy : tests) {
          String url = proxy instanceof SMTestProxy.SMRootTestProxy ? ((SMTestProxy.SMRootTestProxy)proxy).getRootLocation() : proxy.getLocationUrl();
          if (url != null) {
            String configurationName = myConfiguration != null ? myConfiguration.getName() : null;
            storage.writeState(url,
                               new TestStateStorage.Record(proxy.getMagnitude(), new Date(), configurationName == null ? 0 : configurationName.hashCode()));
          }
        }
      });
    }

    @Override
    public void onSuccess() {
      if (myOutputFile != null && myOutputFile.exists()) {
        AbstractImportTestsAction.adjustHistory(myProject);
        TestHistoryConfiguration.getInstance(myProject)
                .registerHistoryItem(myOutputFile.getName(), myConfiguration.getName(), myConfiguration.getType().getId());
      }
    }

    public void dispose() {
      myConfiguration = null;
      myRoot = null;
      myOutputFile = null;
    }
  }
}
