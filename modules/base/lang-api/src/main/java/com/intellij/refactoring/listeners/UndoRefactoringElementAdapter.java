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
package com.intellij.refactoring.listeners;

import com.intellij.psi.PsiElement;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class UndoRefactoringElementAdapter implements RefactoringElementListener, UndoRefactoringElementListener {
  @Override
  public final void elementMoved(@Nonnull PsiElement newElement) {
    refactored(newElement, null);
  }

  @Override
  public final void elementRenamed(@Nonnull PsiElement newElement) {
    refactored(newElement, null);
  }

  @Override
  public final void undoElementMovedOrRenamed(@Nonnull PsiElement newElement, @Nonnull String oldQualifiedName) {
    refactored(newElement, oldQualifiedName);
  }

  /**
   * oldQualifiedName not-null on undoElementMovedOrRenamed, otherwise null
   */
  protected abstract void refactored(@Nonnull PsiElement element, @Nullable String oldQualifiedName);
}