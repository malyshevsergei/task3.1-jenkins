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
package consulo.web.gwt.shared.ui.state.image;

import java.io.Serializable;

/**
 * @author VISTALL
 * @since 11-Sep-17
 */
public class MultiImageState implements Serializable {
  private static final long serialVersionUID = 8759685748025803776L;

  public ImageState myImageState;

  public FoldedImageState myFoldedImageState;

  public float myAlpha = 1;

  public int myHeight;

  public int myWidth;
}
