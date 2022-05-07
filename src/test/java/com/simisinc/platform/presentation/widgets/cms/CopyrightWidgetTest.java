/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

class CopyrightWidgetTest extends WidgetBase {

  @Test
  void execute() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("site.name")).thenReturn("My Site");

      CopyrightWidget copyrightWidget = new CopyrightWidget();
      copyrightWidget.execute(widgetContext);

      String html = widgetContext.getHtml();
      Assertions.assertNotNull(html);
      Assertions.assertTrue(html.contains("My Site."));
      Assertions.assertTrue(html.contains("All Rights Reserved."));
    }
  }

  @Test
  void executeWithTag() {
    // Set widget preferences
    preferences.put("tag", "Additional terms apply.");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("site.name")).thenReturn("My Site" + ".");

      CopyrightWidget copyrightWidget = new CopyrightWidget();
      copyrightWidget.execute(widgetContext);

      String html = widgetContext.getHtml();
      Assertions.assertNotNull(html);
      Assertions.assertTrue(html.contains("My Site."));
      Assertions.assertFalse(html.contains("All Rights Reserved."));
    }
  }
}