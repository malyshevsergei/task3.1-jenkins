/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.table;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.ui.ComboBoxTableRenderer;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.profile.codeInspection.SeverityProvider;
import com.intellij.profile.codeInspection.ui.LevelChooserAction;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import consulo.awt.TargetAWT;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.SortedSet;

/**
 * @author Dmitry Batkovich
 */
public class SeverityRenderer extends ComboBoxTableRenderer<SeverityState> {
  private final Runnable myOnClose;
  private final Image myDisabledIcon;

  public SeverityRenderer(final SeverityState[] values, @Nullable final Runnable onClose) {
    super(values);
    myOnClose = onClose;
    myDisabledIcon = HighlightDisplayLevel.createIconByMask(TargetAWT.from(UIUtil.getLabelDisabledForeground()));
  }

  public static SeverityRenderer create(final InspectionProfileImpl inspectionProfile, @Nullable final Runnable onClose) {
    final SortedSet<HighlightSeverity> severities = LevelChooserAction.getSeverities(((SeverityProvider)inspectionProfile.getProfileManager()).getOwnSeverityRegistrar());
    return new SeverityRenderer(ContainerUtil.map2Array(severities, new SeverityState[severities.size()], severity -> new SeverityState(severity, true, false)), onClose);
  }

  @Override
  protected void customizeComponent(SeverityState value, JTable table, boolean isSelected) {
    super.customizeComponent(value, table, isSelected);
    setPaintArrow(value.isEnabledForEditing());
    setEnabled(!value.isDisabled());
    setDisabledIcon(TargetAWT.to(myDisabledIcon));
  }

  @Override
  protected String getTextFor(@Nonnull final SeverityState value) {
    return SingleInspectionProfilePanel.renderSeverity(value.getSeverity());
  }

  @Override
  protected Image getIconFor(@Nonnull final SeverityState value) {
    return HighlightDisplayLevel.find(value.getSeverity()).getIcon();
  }

  @Override
  public boolean isCellEditable(final EventObject event) {
    return !(event instanceof MouseEvent) || ((MouseEvent)event).getClickCount() >= 1;
  }

  @Override
  public void onClosed(LightweightWindowEvent event) {
    super.onClosed(event);
    if (myOnClose != null) {
      myOnClose.run();
    }
  }
}
