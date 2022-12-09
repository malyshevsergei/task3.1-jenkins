/*
 * Copyright 2013-2018 consulo.io
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
package consulo.fileChooser.impl.system;

import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
import com.intellij.openapi.project.Project;
import com.intellij.ui.mac.MacFileSaverDialog;
import consulo.platform.Platform;
import consulo.ui.fileOperateDialog.FileSaveDialogProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.*;

/**
 * @author VISTALL
 * @since 2018-06-28
 */
public class MacFileSaveDialogProvider implements FileSaveDialogProvider {
  @Nonnull
  @Override
  public String getId() {
    return "mac-native";
  }

  @Nonnull
  @Override
  public String getName() {
    return "system";
  }

  @Override
  public boolean isAvailable() {
    return Platform.current().os().isMac();
  }

  @Nonnull
  @Override
  public FileSaverDialog createSaveFileDialog(@Nonnull FileSaverDescriptor descriptor, @Nullable Project project, @Nullable Component parent) {
    if(parent != null) {
      return new MacFileSaverDialog(descriptor, parent);
    }
    return new MacFileSaverDialog(descriptor, project);
  }
}
