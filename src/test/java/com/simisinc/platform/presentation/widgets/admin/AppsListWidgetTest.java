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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.WidgetBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class AppsListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"apisList\">\n" +
        "  <title>APIs</title>\n" +
        "</widget>");

    // Execute the widget
    ApisListWidget widget = new ApisListWidget();
    widget.execute(widgetContext);

    // Verify
    Assertions.assertEquals(ApisListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("APIs", request.getAttribute("title"));

    List apiListRequest = (List) request.getAttribute("apiList");
    Assertions.assertNotNull(apiListRequest);
  }
}