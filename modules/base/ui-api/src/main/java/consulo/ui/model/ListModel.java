/*
 * Copyright 2013-2016 consulo.io
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
package consulo.ui.model;

import consulo.annotation.DeprecationInfo;
import consulo.ui.internal.UIInternal;

import javax.annotation.Nonnull;
import java.util.Collection;

/**
 * @author VISTALL
 * @since 12-Jun-16
 */
public interface ListModel<E> extends Iterable<E> {
  @Nonnull
  static <T> ListModel<T> of(@Nonnull Collection<? extends T> items) {
    return UIInternal.get()._ListModel_create(items);
  }

  @Nonnull
  @Deprecated
  @DeprecationInfo("Use #of()")
  static <T> ListModel<T> create(@Nonnull Collection<? extends T> items) {
    return UIInternal.get()._ListModel_create(items);
  }

  int getSize();

  @Nonnull
  E get(int index);

  int indexOf(@Nonnull E value);
}
