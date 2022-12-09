/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log;

import javax.annotation.Nonnull;

/**
 * Specifies the log filter by text.
 */
public interface VcsLogTextFilter extends VcsLogDetailsFilter {

  /**
   * Only commits containing the returned text it their commit messages should match the filter.
   */
  @Nonnull
  String getText();

  /**
   * Check whether this pattern represents a regular expression.
   *
   * @return true if this pattern represents a regular expression, false otherwise.
   */
  boolean isRegex();

  /**
   * Check whether the filter should be case sensitive.
   *
   * @return true if case sensitive, false otherwise.
   */
  boolean matchesCase();
}
