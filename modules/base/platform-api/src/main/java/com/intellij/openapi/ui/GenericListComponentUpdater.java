// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import javax.annotation.Nonnull;

import java.util.List;

public interface GenericListComponentUpdater<T> {
  void replaceModel(@Nonnull List<? extends T> data);

  void paintBusy(boolean paintBusy);
}
