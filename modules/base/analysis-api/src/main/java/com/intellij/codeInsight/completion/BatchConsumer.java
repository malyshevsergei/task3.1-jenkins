// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import com.intellij.util.Consumer;

/**
 * Pass an implementation of this interface to {@link CompletionService#performCompletion(CompletionParameters, Consumer)} to receive
 * updates when each batch of completion items generated by a completion contributor is done.
 *
 * @author yole
 */
public interface BatchConsumer<T> extends Consumer<T> {
  default void startBatch() {
  }

  default void endBatch() {
  }
}
