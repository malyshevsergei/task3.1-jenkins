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

package com.intellij.codeInsight.template;

import com.intellij.codeInsight.lookup.LookupElement;
import javax.annotation.Nullable;

public abstract class Expression {

  @Nullable
  public abstract Result calculateResult(ExpressionContext context);

  @Nullable
  public Result calculateQuickResult(ExpressionContext context) {
    return calculateResult(context);
  }
  
  @Nullable
  public abstract LookupElement[] calculateLookupItems(ExpressionContext context);

  @Nullable
  public String getAdvertisingText() {
    return null;
  }

  /**
   * @return true if {@link Expression#calculateResult} or {@link Expression#calculateQuickResult}
   * require committed PSI for their calculation or false otherwise
   */
  public boolean requiresCommittedPSI() {
    return true;
  }
}

