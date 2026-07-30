/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.presentation.controller.WidgetContext;

class PowerBiWidgetTest extends WidgetBase {

  private static final String VALID_URL = "https://app.powerbi.com/view?r=eyJrIjoiYWJjMTIzIn0%3D";

  @Test
  void rendersTheIframeWhenConfigured() {
    preferences.put("embedUrl", VALID_URL);

    WidgetContext result = new PowerBiWidget().execute(widgetContext);

    assertEquals(PowerBiWidget.JSP, result.getJsp());
    assertEquals(VALID_URL, result.getRequest().getAttribute("embedUrl"));
    assertEquals("300px", result.getRequest().getAttribute("height"));
  }

  @Test
  void usesTheConfiguredHeight() {
    preferences.put("embedUrl", VALID_URL);
    preferences.put("height", "600px");

    WidgetContext result = new PowerBiWidget().execute(widgetContext);

    assertEquals("600px", result.getRequest().getAttribute("height"));
  }

  @Test
  void doesNotRenderWhenTheUrlIsMissing() {
    WidgetContext result = new PowerBiWidget().execute(widgetContext);

    assertNull(result.getJsp());
    assertNull(result.getRequest().getAttribute("embedUrl"));
  }

  @Test
  void doesNotRenderWhenTheUrlIsNotARealPowerBiEmbed() {
    preferences.put("embedUrl", "https://evil.example.com/view?r=abc123");

    WidgetContext result = new PowerBiWidget().execute(widgetContext);

    assertNull(result.getJsp());
    assertNull(result.getRequest().getAttribute("embedUrl"));
  }
}
