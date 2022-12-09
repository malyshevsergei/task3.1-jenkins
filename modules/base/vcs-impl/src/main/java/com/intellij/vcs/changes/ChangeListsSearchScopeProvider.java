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
package com.intellij.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.search.DefaultSearchScopeProviders;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.SearchScopeProvider;
import com.intellij.psi.search.scope.packageSet.NamedScope;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * @author VISTALL
 * @since 12/12/2021
 */
public class ChangeListsSearchScopeProvider implements SearchScopeProvider {
  @Override
  public String getDisplayName() {
    return "Local Changes";
  }

  @Nonnull
  @Override
  public List<SearchScope> getSearchScopes(@Nonnull Project project) {
    List<SearchScope> result = new ArrayList<>();
    List<NamedScope> changeLists = ChangeListsScopesProvider.getInstance(project).getFilteredScopes();
    if (!changeLists.isEmpty()) {
      for (NamedScope changeListScope : changeLists) {
        result.add(DefaultSearchScopeProviders.wrapNamedScope(project, changeListScope, false));
      }
    }
    return result;
  }
}
