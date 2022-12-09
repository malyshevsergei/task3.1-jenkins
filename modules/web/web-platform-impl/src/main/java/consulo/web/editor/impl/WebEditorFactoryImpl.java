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
package consulo.web.editor.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorKind;
import com.intellij.openapi.editor.impl.EditorFactoryImpl;
import com.intellij.openapi.project.Project;
import consulo.editor.internal.EditorInternal;
import consulo.ui.web.internal.ex.WebEditorImpl;

import javax.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 06/12/2020
 */
public class WebEditorFactoryImpl extends EditorFactoryImpl {
  public WebEditorFactoryImpl(Application application) {
    super(application);
  }

  @Nonnull
  @Override
  protected EditorInternal createEditorImpl(@Nonnull Document document, boolean isViewer, Project project, @Nonnull EditorKind kind) {
    return new WebEditorImpl(document, isViewer, project, kind);
  }
}
