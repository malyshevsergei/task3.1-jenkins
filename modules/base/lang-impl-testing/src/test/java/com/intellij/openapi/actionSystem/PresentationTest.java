/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem;

import com.intellij.ide.ui.UISettings;
import consulo.localize.LocalizeValue;
import consulo.testFramework.MockApplicationTestCase;

/**
 * Created in IntelliJ IDEA.
 * By: Alexander.Chernikov
 * When: 10.10.2006, 20:19:35
 */
public abstract class PresentationTest extends MockApplicationTestCase {

  private final String[] inputTextsUnderscores =
          new String[]{"No mnemonic", "_First char", "S_econd char", "Pre-last and not unique ch_ar", "Last cha_r", "Too late_", "Do__uble", "Dou_&ble", "Complete double__", "Complete double_&",
                  "Repea_te_d", "Re_peate&d"
    /*,
    "Alphanumeric only _! _@ _# _$ _% _^ _& _* _( _) _- __ _= _+ _| _\\ _/ _0"*/};
  private final String[] inputTextsAmpersands =
          new String[]{"No mnemonic", "&First char", "S&econd char", "Pre-last and not unique ch&ar", "Last cha&r", "Too late&", "Do&_uble", "Dou&&ble", "Complete double&_", "Complete double&&",
                  "Repea&te_d", "Re&peate&d"
    /*,
    "Alphanumeric only &! &@ &# &$ &% &^ && &* &( &) &- &_ &= &+ &| &\\ &/ &0"*/};
  private final String[] menuTexts =
          new String[]{"No mnemonic", "First char", "Second char", "Pre-last and not unique char", "Last char", "Too late", "Do_uble", "Dou&ble", "Complete double_", "Complete double&", "Repeate_d",
                  "Repeate&d"};
  private final int[] mnemonics = new int[]{0, 'F', 'E', 'A', 'R', 0, 0, 0, 0, 0, 'T', 'P'};
  private final int[] indeces = new int[]{-1, 0, 1, 26, 8, -1, -1, -1, -1, -1, 5, 2};
  private final String[] fullMenuTexts =
          new String[]{"No mnemonic", "_First char", "S_econd char", "Pre-last and not unique ch_ar", "Last cha_r", "Too late", "Do_uble", "Dou&ble", "Complete double_", "Complete double&",
                  "Repea_te_d", "Re_peate&d"};

  @Override
  public void setUp() throws Exception {
    super.setUp();
    UISettings.getInstance().setDisableMnemonics(false);
    UISettings.getInstance().setDisableMnemonicsInControls(false);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      UISettings defaults = new UISettings();
      UISettings.getInstance().setDisableMnemonics(defaults.getDisableMnemonics());
      UISettings.getInstance().setDisableMnemonicsInControls(defaults.getDisableMnemonicsInControls());
    }
    finally {
      super.tearDown();
    }
  }

  public void _testPresentationSetText() {
    for (int i = 0; i < inputTextsUnderscores.length; i++) {
      Presentation p = new Presentation();
      p.setTextValue(LocalizeValue.of(inputTextsUnderscores[i]));
      assertEquals(menuTexts[i], p.getText());
      assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());

      p.setTextValue(LocalizeValue.of(inputTextsAmpersands[i]));
      assertEquals(menuTexts[i], p.getText());
      assertEquals(fullMenuTexts[i], p.getTextWithMnemonic());
    }
  }
}
