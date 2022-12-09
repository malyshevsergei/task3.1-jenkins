/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.vcs.history;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsHistoryProvider;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface VcsHistoryProviderEx extends VcsHistoryProvider {
  @javax.annotation.Nullable
  VcsFileRevision getLastRevision(FilePath filePath) throws VcsException;

  void reportAppendableHistory(@Nonnull FilePath path,
                               @Nullable VcsRevisionNumber startingRevision,
                               @Nonnull VcsAppendableHistorySessionPartner partner) throws VcsException;
}
