/*
 * Copyright 2013-2020 consulo.io
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

import com.vaadin.client.ConnectorHierarchyChangeEvent;
import com.vaadin.client.communication.StateChangeEvent;
import com.vaadin.shared.ui.Connect;
import consulo.web.gwt.client.ApplicationHolder;
import consulo.web.gwt.client.util.GwtUIUtil;
import consulo.web.gwt.shared.ui.state.ApplicationContainerState;

/**
 * @author VISTALL
 * @since 2020-11-21
 */
@Connect(canonicalName = "consulo.ui.web.internal.WebApplicationContainerImpl.Vaadin")
public class GwtApplicationContainerConnector extends GwtLayoutConnector {
  @Override
  public void onConnectorHierarchyChange(ConnectorHierarchyChangeEvent event) {
    getWidget().build(GwtUIUtil.remapWidgets(this));
  }

  @Override
  public void onStateChanged(StateChangeEvent stateChangeEvent) {
    super.onStateChanged(stateChangeEvent);

    ApplicationHolder.INSTANCE.setApplicationState(getState().myApplicationState);
  }

  @Override
  public ApplicationContainerState getState() {
    return (ApplicationContainerState)super.getState();
  }

  @Override
  public GwtApplicationContainer getWidget() {
    return (GwtApplicationContainer)super.getWidget();
  }
}