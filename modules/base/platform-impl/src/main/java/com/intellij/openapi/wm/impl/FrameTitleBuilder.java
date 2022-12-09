/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import consulo.annotation.access.RequiredReadAction;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public abstract class FrameTitleBuilder {
  public static FrameTitleBuilder getInstance() {
    return ServiceManager.getService(FrameTitleBuilder.class);
  }

  @Nonnull
  @RequiredReadAction
  public abstract String getProjectTitle(@Nonnull final Project project);

  @Nonnull
  @RequiredReadAction
  public abstract String getFileTitle(@Nonnull final Project project, @Nonnull final VirtualFile file);
}
