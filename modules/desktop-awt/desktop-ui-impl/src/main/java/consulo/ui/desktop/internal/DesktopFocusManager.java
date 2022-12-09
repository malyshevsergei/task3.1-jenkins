/*
 * Copyright 2013-2020 consulo.io
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
package consulo.ui.desktop.internal;

import consulo.disposer.Disposable;
import consulo.ui.FocusManager;
import consulo.ui.event.GlobalFocusListener;

import javax.annotation.Nonnull;
import java.awt.*;
import java.beans.PropertyChangeListener;

/**
 * @author VISTALL
 * @since 2020-11-21
 */
public class DesktopFocusManager implements FocusManager {
  public static final DesktopFocusManager ourInstance = new DesktopFocusManager();

  @Nonnull
  @Override
  public Disposable addListener(@Nonnull GlobalFocusListener focusListener) {
    PropertyChangeListener listener = evt -> focusListener.focusChanged();
    KeyboardFocusManager keyboardFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    keyboardFocusManager.addPropertyChangeListener("focusOwner", listener);
    return () -> keyboardFocusManager.removePropertyChangeListener("focusOwner", listener);
  }
}
