/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.ActionPromoter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.vcs.ui.Refreshable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class VcsActionPromoter implements ActionPromoter {
  @Override
  public List<AnAction> promote(List<AnAction> actions, DataContext context) {
    if (context.getData(Refreshable.PANEL_KEY) != null) {
      for (AnAction action : actions) {
        if (action instanceof ShowMessageHistoryAction) {
          return Arrays.asList(action);
        }
      }
    }
    return Collections.emptyList();
  }
}
