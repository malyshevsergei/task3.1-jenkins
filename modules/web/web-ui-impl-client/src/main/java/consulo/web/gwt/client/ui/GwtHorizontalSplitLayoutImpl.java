/*
 * Copyright 2013-2017 consulo.io
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
package consulo.web.gwt.client.ui;

import com.google.gwt.user.client.ui.Widget;

/**
 * @author VISTALL
 * @since 13-Sep-17
 */
public class GwtHorizontalSplitLayoutImpl extends GwtSplitLayoutImpl {
  public GwtHorizontalSplitLayoutImpl() {
    this(1);
  }

  public GwtHorizontalSplitLayoutImpl(int splitterSize) {
    super(splitterSize);
    setStyleName("ui-horizontal-split-panel");
  }

  @Override
  protected int getElementSize(Widget widget) {
    return widget.getOffsetWidth();
  }

  @Override
  protected Direction getDirection() {
    return Direction.WEST;
  }
}
