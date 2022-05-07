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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class SystemAlertWidgetTest extends WidgetBase {

  @Test
  void execute() {
    String siteHeaderLine1 = "Welcome to the site";
    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.header.line1", siteHeaderLine1);

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadAsMap(anyString())).thenReturn(sitePropertyMap);

      SystemAlertWidget widget = new SystemAlertWidget();
      widget.execute(widgetContext);

      Assertions.assertEquals(SystemAlertWidget.JSP, widgetContext.getJsp());
      Map requestSitePropertyMap = (Map) request.getAttribute("sitePropertyMap");
      Assertions.assertEquals(1, requestSitePropertyMap.size());
      Assertions.assertEquals(siteHeaderLine1, requestSitePropertyMap.get("site.header.line1"));
    }
  }
}