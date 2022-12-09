/*
 * Copyright 2013-2021 consulo.io
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
package consulo.ui.web.internal;

import consulo.ui.ToggleSwitch;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 01/08/2021
 */
public class WebToggleSwitchImpl extends WebCheckBoxImpl implements ToggleSwitch {
  public WebToggleSwitchImpl(boolean selected) {
    setValue(selected);
  }

  @RequiredUIAccess
  @Override
  public void setValue(@Nonnull Boolean value) {
    WebToggleSwitchImpl.this.setValue(value, true);
  }
}
