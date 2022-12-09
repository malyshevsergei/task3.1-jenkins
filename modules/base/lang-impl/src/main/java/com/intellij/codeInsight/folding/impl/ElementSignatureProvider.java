/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author yole
 */
public interface ElementSignatureProvider {
  ExtensionPointName<ElementSignatureProvider> EP_NAME = ExtensionPointName.create("com.intellij.elementSignatureProvider");

  @Nullable
  String getSignature(@Nonnull PsiElement element);

  @Nullable
  PsiElement restoreBySignature(@Nonnull PsiFile file, @Nonnull String signature, @Nullable StringBuilder processingInfoStorage);
}
