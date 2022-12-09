/*
 * Copyright 2013-2019 consulo.io
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
package consulo.ui.desktop.internal.window;

import consulo.awt.TargetAWT;
import consulo.awt.impl.FromSwingWindowWrapper;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.Window;

import javax.annotation.Nonnull;
import javax.swing.*;

/**
 * @author VISTALL
 * @since 2019-02-16
 */
public class JWindowAsUIWindow extends JWindow implements FromSwingWindowWrapper {
  private WindowOverAWTWindow myWindowOverAWTWindow;

  public JWindowAsUIWindow(Window window) {
    super(TargetAWT.to(window));

    myWindowOverAWTWindow = new WindowOverAWTWindow(this) {
      @RequiredUIAccess
      @Override
      public void setTitle(@Nonnull String title) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @Nonnull
  @Override
  public Window toUIWindow() {
    return myWindowOverAWTWindow;
  }
}
