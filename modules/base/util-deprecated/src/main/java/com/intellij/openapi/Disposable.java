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
package com.intellij.openapi;

import consulo.annotation.DeprecationInfo;
import consulo.disposer.Disposer;

/**
 * This class marks classes, which require some work done for cleaning up.
 * As a general policy you shouldn't call the {@link #dispose} method directly,
 * but register your object to be chained with a parent disposable via {@link Disposer#register(consulo.disposer.Disposable, consulo.disposer.Disposable)}.
 * If you're 100% sure that you should control disposion of your object manually,
 * do not call the {@link #dispose} method either. Use {@link Disposer#dispose(consulo.disposer.Disposable)} instead, since
 * there might be any object registered in chain.
 */
@Deprecated
@DeprecationInfo("Use consulo.disposer.Disposable")
public interface Disposable extends consulo.disposer.Disposable {
  void dispose();

  interface Parent extends consulo.disposer.Disposable.Parent {
    void beforeTreeDispose();
  }
}
