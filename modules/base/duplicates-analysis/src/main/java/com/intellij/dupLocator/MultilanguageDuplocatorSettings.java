// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dupLocator;

import com.intellij.lang.Language;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.SkipDefaultValuesSerializationFilters;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import javax.annotation.Nonnull;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "MultiLanguageDuplocatorSettings",
  storages = @Storage("duplocatorSettings.xml")
)
public class MultilanguageDuplocatorSettings implements PersistentStateComponent<Element> {
  private final Map<String, ExternalizableDuplocatorState> mySettingsMap = new TreeMap<>();

  public static MultilanguageDuplocatorSettings getInstance() {
    return ServiceManager.getService(MultilanguageDuplocatorSettings.class);
  }

  public void registerState(@Nonnull Language language, @Nonnull ExternalizableDuplocatorState state) {
    synchronized (mySettingsMap) {
      mySettingsMap.put(language.getDisplayName(), state);
    }
  }

  public ExternalizableDuplocatorState getState(@Nonnull Language language) {
    synchronized (mySettingsMap) {
      return mySettingsMap.get(language.getDisplayName());
    }
  }

  @Override
  public Element getState() {
    synchronized (mySettingsMap) {
      Element state = new Element("state");
      if (mySettingsMap.isEmpty()) {
        return state;
      }

      SkipDefaultValuesSerializationFilters filter = new SkipDefaultValuesSerializationFilters();
      for (String name : mySettingsMap.keySet()) {
        Element child = XmlSerializer.serializeIfNotDefault(mySettingsMap.get(name), filter);
        if (child != null) {
          child.setName("object");
          child.setAttribute("language", name);
          state.addContent(child);
        }
      }
      return state;
    }
  }

  @Override
  public void loadState(@Nonnull Element state) {
    synchronized (mySettingsMap) {
      if (state == null) {
        return;
      }

      for (Element objectElement : state.getChildren("object")) {
        String language = objectElement.getAttributeValue("language");
        if (language != null) {
          ExternalizableDuplocatorState stateObject = mySettingsMap.get(language);
          if (stateObject != null) {
            XmlSerializer.deserializeInto(stateObject, objectElement);
          }
        }
      }
    }
  }
}
