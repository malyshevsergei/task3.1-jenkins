/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.targets;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.PomRenameableTarget;
import com.intellij.psi.DelegatePsiTarget;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class AliasingPsiTarget extends DelegatePsiTarget implements PomRenameableTarget<AliasingPsiTarget>{
  public AliasingPsiTarget(@Nonnull PsiNamedElement element) {
    super(element);
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement().isWritable();
  }

  @Override
  public AliasingPsiTarget setName(@Nonnull String newName) {
    return setAliasName(newName);
  }

   @Override
   @Nonnull
  public String getName() {
    return StringUtil.notNullize(getNameAlias(((PsiNamedElement)getNavigationElement()).getName()));
  }

  @Nonnull
  public AliasingPsiTarget setAliasName(@Nonnull String newAliasName) {
    return this;
  }

  @Nullable
  public String getNameAlias(@javax.annotation.Nullable String delegatePsiTargetName) {
    return delegatePsiTargetName;
  }

  protected void renameTargets(@Nonnull String newDelegateName) {
    final PsiNamedElement namedElement = (PsiNamedElement)getNavigationElement();
    if (!newDelegateName.equals(namedElement.getName())) {
      final RenameRefactoring refactoring =
        RefactoringFactory.getInstance(namedElement.getProject()).createRename(namedElement, newDelegateName);
      refactoring.run();

    }
  }
}
