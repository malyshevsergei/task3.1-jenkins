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
package com.intellij.diff.impl;

import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.NoDiffRequest;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.openapi.project.Project;
import consulo.disposer.Disposer;
import consulo.util.dataholder.Key;
import com.intellij.util.ui.UIUtil;
import consulo.ui.annotation.RequiredUIAccess;
import javax.annotation.Nonnull;

import javax.swing.*;
import java.awt.*;

public class DiffRequestPanelImpl implements DiffRequestPanel {
  @Nonnull
  private final JPanel myPanel;
  @Nonnull
  private final MyDiffRequestProcessor myProcessor;

  public DiffRequestPanelImpl(@javax.annotation.Nullable Project project, @javax.annotation.Nullable Window window) {
    myProcessor = new MyDiffRequestProcessor(project, window);
    myProcessor.putContextUserData(DiffUserDataKeys.DO_NOT_CHANGE_WINDOW_TITLE, true);

    myPanel = new JPanel(new BorderLayout()) {
      @Override
      public void addNotify() {
        super.addNotify();
        myProcessor.updateRequest();
      }
    };
    myPanel.add(myProcessor.getComponent());
  }

  @Override
  public void setRequest(@javax.annotation.Nullable DiffRequest request) {
    setRequest(request, null);
  }

  @Override
  public void setRequest(@javax.annotation.Nullable DiffRequest request, @javax.annotation.Nullable Object identity) {
    myProcessor.setRequest(request, identity);
  }

  @Override
  public <T> void putContextHints(@Nonnull Key<T> key, @javax.annotation.Nullable T value) {
    myProcessor.putContextUserData(key, value);
  }

  @Nonnull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @javax.annotation.Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myProcessor.getPreferredFocusedComponent();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myProcessor);
  }

  private static class MyDiffRequestProcessor extends DiffRequestProcessor {
    @javax.annotation.Nullable
    private final Window myWindow;

    @Nonnull
    private DiffRequest myRequest = NoDiffRequest.INSTANCE;
    @javax.annotation.Nullable
    private Object myRequestIdentity = null;

    public MyDiffRequestProcessor(@javax.annotation.Nullable Project project, @javax.annotation.Nullable Window window) {
      super(project);
      myWindow = window;
    }

    public synchronized void setRequest(@javax.annotation.Nullable DiffRequest request, @javax.annotation.Nullable Object identity) {
      if (myRequestIdentity != null && identity != null && myRequestIdentity.equals(identity)) return;

      myRequest = request != null ? request : NoDiffRequest.INSTANCE;
      myRequestIdentity = identity;

      UIUtil.invokeLaterIfNeeded(() -> updateRequest());
    }

    @Override
    @RequiredUIAccess
    public synchronized void updateRequest(boolean force, @javax.annotation.Nullable DiffUserDataKeysEx.ScrollToPolicy scrollToChangePolicy) {
      applyRequest(myRequest, force, scrollToChangePolicy);
    }

    @Override
    protected void setWindowTitle(@Nonnull String title) {
      if (myWindow == null) return;
      if (myWindow instanceof JDialog) ((JDialog)myWindow).setTitle(title);
      if (myWindow instanceof JFrame) ((JFrame)myWindow).setTitle(title);
    }
  }
}
