/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.util;

import com.intellij.ide.GeneralSettings;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.JBDimension;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class TipPanel extends JPanel implements DialogWrapper.DoNotAskOption {
  private static final int DEFAULT_WIDTH = 400;
  private static final int DEFAULT_HEIGHT = 200;

  private final JEditorPane myBrowser;
  private final JLabel myPoweredByLabel;
  private final List<TipAndTrickBean> myTips = ContainerUtil.newArrayList();

  public TipPanel() {
    setLayout(new BorderLayout());
    myBrowser = TipUIUtil.createTipBrowser();
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBrowser, true);
    add(scrollPane, BorderLayout.CENTER);

    JPanel southPanel = new JPanel(new BorderLayout());

    myPoweredByLabel = new JBLabel();
    myPoweredByLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    myPoweredByLabel.setForeground(SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES.getFgColor());

    southPanel.add(myPoweredByLabel, BorderLayout.EAST);
    add(southPanel, BorderLayout.SOUTH);

    myTips.addAll(TipAndTrickBean.EP_NAME.getExtensionList());
  }

  @Override
  public Dimension getPreferredSize() {
    return new JBDimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
  }

  public void prevTip() {
    if (myTips.size() == 0) {
      myBrowser.setText(IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName()));
      return;
    }
    final GeneralSettings settings = GeneralSettings.getInstance();
    int lastTip = settings.getLastTip();

    final TipAndTrickBean tip;
    lastTip--;
    if (lastTip <= 0) {
      tip = myTips.get(myTips.size() - 1);
      lastTip = myTips.size();
    }
    else {
      tip = myTips.get(lastTip - 1);
    }

    setTip(tip, lastTip, myBrowser, settings);
  }

  private void setTip(TipAndTrickBean tip, int lastTip, JEditorPane browser, GeneralSettings settings) {
    TipUIUtil.openTipInBrowser(tip, browser);
    myPoweredByLabel.setText(TipUIUtil.getPoweredByText(tip));
    settings.setLastTip(lastTip);
  }

  public void nextTip() {
    if (myTips.size() == 0) {
      myBrowser.setText(IdeBundle.message("error.tips.not.found", ApplicationNamesInfo.getInstance().getFullProductName()));
      return;
    }
    GeneralSettings settings = GeneralSettings.getInstance();
    int lastTip = settings.getLastTip();
    TipAndTrickBean tip;
    lastTip++;
    if (lastTip - 1 >= myTips.size()) {
      tip = myTips.get(0);
      lastTip = 1;
    }
    else {
      tip = myTips.get(lastTip - 1);
    }

    setTip(tip, lastTip, myBrowser, settings);
  }

  @Override
  public boolean isToBeShown() {
    return !GeneralSettings.getInstance().isShowTipsOnStartup();
  }

  @Override
  public void setToBeShown(boolean toBeShown, int exitCode) {
    GeneralSettings.getInstance().setShowTipsOnStartup(!toBeShown);
  }

  @Override
  public boolean canBeHidden() {
    return true;
  }

  @Override
  public boolean shouldSaveOptionsOnCancel() {
    return true;
  }

  @Override
  public String getDoNotShowMessage() {
    return IdeBundle.message("checkbox.show.tips.on.startup");
  }
}