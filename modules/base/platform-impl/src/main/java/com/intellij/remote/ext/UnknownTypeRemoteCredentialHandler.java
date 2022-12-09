/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remote.ext;

import org.jdom.Element;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Irina.Chernushina on 7/29/2016.
 */
public class UnknownTypeRemoteCredentialHandler extends RemoteCredentialsHandlerBase<UnknownCredentialsHolder> {
  public UnknownTypeRemoteCredentialHandler(UnknownCredentialsHolder credentials) {
    super(credentials);
  }

  @Override
  public String getId() {
    return getCredentials().getInterpreterPath();
  }

  @Override
  public void save(@Nonnull Element rootElement) {
    getCredentials().save(rootElement);
  }

  @Override
  public String getPresentableDetails(String interpreterPath) {
    return getId();
  }

  @Override
  public void load(@Nullable Element rootElement) {
    getCredentials().load(rootElement);
  }
}
