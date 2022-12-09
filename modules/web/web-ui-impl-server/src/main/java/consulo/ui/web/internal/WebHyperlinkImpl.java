/*
 * Copyright 2013-2019 consulo.io
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
package consulo.ui.web.internal;

import consulo.ui.Component;
import consulo.ui.Hyperlink;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.HyperlinkEvent;
import consulo.ui.event.HyperlinkListener;
import consulo.ui.image.Image;
import consulo.ui.web.internal.base.VaadinComponent;
import consulo.ui.web.internal.base.VaadinComponentDelegate;
import consulo.ui.web.servlet.WebImageMapper;
import consulo.web.gwt.shared.ui.state.button.ButtonRpc;
import consulo.web.gwt.shared.ui.state.button.ButtonState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2019-02-17
 */
public class WebHyperlinkImpl extends VaadinComponentDelegate<WebHyperlinkImpl.Vaadin> implements Hyperlink {
  public static class Vaadin extends VaadinComponent {
    private final ButtonRpc myRpc = new ButtonRpc() {
      @Override
      public void onClick() {
        Component component = toUIComponent();
        component.getListenerDispatcher(HyperlinkListener.class).navigate(new HyperlinkEvent(component, ""));
      }
    };

    private Image myImage;

    public Vaadin() {
      registerRpc(myRpc);
    }


    @Override
    public ButtonState getState() {
      return (ButtonState)super.getState();
    }

    @Override
    public void beforeClientResponse(boolean initial) {
      super.beforeClientResponse(initial);

      getState().myImageState = myImage == null ? null : WebImageMapper.map(myImage).getState();
    }

    private void setText(String text) {
      getState().caption = text;
    }

    private void setImage(Image image) {
      myImage = image;
      markAsDirty();
    }
  }

  @Nonnull
  @Override
  public Vaadin createVaadinComponent() {
    return new Vaadin();
  }

  @Nonnull
  @Override
  public String getText() {
    return getVaadinComponent().getState().caption;
  }

  @RequiredUIAccess
  @Override
  public void setText(@Nonnull String text) {
    getVaadinComponent().setText(text);
  }

  @Override
  public void setImage(@Nullable Image icon) {
    getVaadinComponent().setImage(icon);
  }

  @Nullable
  @Override
  public Image getImage() {
    return getVaadinComponent().myImage;
  }
}
