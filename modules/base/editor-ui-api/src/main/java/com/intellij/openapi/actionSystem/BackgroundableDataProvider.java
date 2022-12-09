// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import consulo.util.dataholder.Key;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface BackgroundableDataProvider extends DataProvider {

  /**
   * Called on UI thread, should be fast: just get the information from Swing components needed to create the actual asynchronous data provider.
   *
   * @return a data provider that might be called in a background thread (so shouldn't access any Swing hierarchy).
   */
  @Nullable
  DataProvider createBackgroundDataProvider();

  @Nullable
  @Override
  default Object getData(@Nonnull Key<?> dataId) {
    DataProvider async = createBackgroundDataProvider();
    return async == null ? null : async.getData(dataId);
  }
}
