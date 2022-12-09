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
package com.intellij.openapi.options;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Document;
import org.jdom.JDOMException;
import org.jdom.Parent;
import javax.annotation.Nonnull;

import java.io.IOException;

public interface SchemeProcessor<T extends ExternalizableScheme> {
  T readScheme(@Nonnull Document schemeContent) throws InvalidDataException, IOException, JDOMException;

  Parent writeScheme(@Nonnull T scheme) throws WriteExternalException;

  boolean shouldBeSaved(@Nonnull T scheme);

  void initScheme(@Nonnull T scheme);

  void onSchemeAdded(@Nonnull T scheme);

  void onSchemeDeleted(@Nonnull T scheme);

  void onCurrentSchemeChanged(final T oldCurrentScheme);
}
