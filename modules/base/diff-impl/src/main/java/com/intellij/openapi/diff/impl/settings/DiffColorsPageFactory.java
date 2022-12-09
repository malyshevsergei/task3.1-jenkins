/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.settings;

import com.intellij.application.options.colors.*;
import com.intellij.diff.util.DiffLineSeparatorRenderer;
import com.intellij.diff.util.TextDiffTypeFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.OptionsBundle;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorAndFontDescriptorsProvider;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import javax.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

public class DiffColorsPageFactory implements ColorAndFontPanelFactory, ColorAndFontDescriptorsProvider {
  @Override
  @Nonnull
  public NewColorAndFontPanel createPanel(@Nonnull ColorAndFontOptions options) {
    final SchemesPanel schemesPanel = new SchemesPanel(options);

    CompositeColorDescriptionPanel descriptionPanel = new CompositeColorDescriptionPanel();
    descriptionPanel.addDescriptionPanel(new ColorAndFontDescriptionPanel(), it -> it instanceof ColorAndFontDescription);
    descriptionPanel.addDescriptionPanel(new DiffColorDescriptionPanel(options), it -> it instanceof TextAttributesDescription);

    final OptionsPanelImpl optionsPanel = new OptionsPanelImpl(options, schemesPanel, getDiffGroup(), descriptionPanel);
    final DiffPreviewPanel previewPanel = new DiffPreviewPanel();

    schemesPanel.addListener(new ColorAndFontSettingsListener.Abstract() {
      @Override
      public void schemeChanged(@Nonnull final Object source) {
        previewPanel.setColorScheme(options.getSelectedScheme());
        optionsPanel.updateOptionsList();
      }
    });

    return new NewColorAndFontPanel(schemesPanel, optionsPanel, previewPanel, getDiffGroup(), null, null);
  }

  @Override
  @Nonnull
  public AttributesDescriptor[] getAttributeDescriptors() {
    TextDiffTypeFactory.TextDiffTypeImpl[] diffTypes = TextDiffTypeFactory.getInstance().getAllDiffTypes();
    return ContainerUtil.map2Array(diffTypes, AttributesDescriptor.class,
                                   type -> new AttributesDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.diff.type.tag.prefix") + type.getName(), type.getKey()));
  }

  @Override
  @Nonnull
  public ColorDescriptor[] getColorDescriptors() {
    List<ColorDescriptor> descriptors = new ArrayList<>();

    descriptors
            .add(new ColorDescriptor(OptionsBundle.message("options.general.color.descriptor.vcs.diff.separator.background"), DiffLineSeparatorRenderer.BACKGROUND, ColorDescriptor.Kind.BACKGROUND));

    return descriptors.toArray(ColorDescriptor.EMPTY_ARRAY);
  }

  @Override
  @Nonnull
  public String getPanelDisplayName() {
    return getDiffGroup();
  }

  @Nonnull
  @Override
  public String getDisplayName() {
    return getDiffGroup();
  }

  @Nls
  public static String getDiffGroup() {
    return ApplicationBundle.message("title.diff");
  }
}
