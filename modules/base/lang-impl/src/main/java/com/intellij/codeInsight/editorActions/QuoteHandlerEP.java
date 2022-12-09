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

package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.LazyInstance;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class QuoteHandlerEP extends AbstractExtensionPointBean implements KeyedLazyInstance<QuoteHandler> {
  public static final ExtensionPointName<QuoteHandlerEP> EP_NAME = ExtensionPointName.create("com.intellij.quoteHandler");

  // these must be public for scrambling compatibility
  @Attribute("fileType")
  public String fileType;
  @Attribute("className")
  public String className;

  private final LazyInstance<QuoteHandler> myHandler = LazyInstance.createInstance(() -> findClass(className));

  public QuoteHandler getHandler() {
    return myHandler.getValue();
  }

  @Override
  public String getKey() {
    return fileType;
  }

  @Override
  public QuoteHandler getInstance() {
    return myHandler.getValue();
  }
}