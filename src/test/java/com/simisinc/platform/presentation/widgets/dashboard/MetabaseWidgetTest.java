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

package com.simisinc.platform.presentation.widgets.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.dashboards.MetabaseEmbedCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

class MetabaseWidgetTest extends WidgetBase {

  @Test
  void rendersTheIframeWhenConfigured() {
    preferences.put("dashboardValue", "12");

    try (MockedStatic<MetabaseEmbedCommand> mock = mockStatic(MetabaseEmbedCommand.class)) {
      mock.when(() -> MetabaseEmbedCommand.generateDashboardIframeUrl("12", "bordered=true&titled=true"))
          .thenReturn("https://metabase.example.com/embed/dashboard/signed-token");

      WidgetContext result = new MetabaseWidget().execute(widgetContext);

      assertEquals(MetabaseWidget.JSP, result.getJsp());
      assertEquals("https://metabase.example.com/embed/dashboard/signed-token", result.getRequest().getAttribute("iframeUrl"));
      assertEquals("300px", result.getRequest().getAttribute("height"));
    }
  }

  @Test
  void hidesTheChartTitleWhenRequested() {
    preferences.put("dashboardValue", "12");
    preferences.put("hideChartTitle", "true");

    try (MockedStatic<MetabaseEmbedCommand> mock = mockStatic(MetabaseEmbedCommand.class)) {
      mock.when(() -> MetabaseEmbedCommand.generateDashboardIframeUrl("12", "bordered=true&titled=false"))
          .thenReturn("https://metabase.example.com/embed/dashboard/signed-token");

      new MetabaseWidget().execute(widgetContext);

      mock.verify(() -> MetabaseEmbedCommand.generateDashboardIframeUrl("12", "bordered=true&titled=false"));
    }
  }

  @Test
  void doesNotRenderWhenTheCommandReturnsNull() {
    preferences.put("dashboardValue", "12");

    try (MockedStatic<MetabaseEmbedCommand> mock = mockStatic(MetabaseEmbedCommand.class)) {
      mock.when(() -> MetabaseEmbedCommand.generateDashboardIframeUrl("12", "bordered=true&titled=true")).thenReturn(null);

      WidgetContext result = new MetabaseWidget().execute(widgetContext);

      assertNull(result.getJsp());
      assertNull(result.getRequest().getAttribute("iframeUrl"));
    }
  }

  @Test
  void passesThroughAMissingDashboardValueForTheCommandToReject() {
    try (MockedStatic<MetabaseEmbedCommand> mock = mockStatic(MetabaseEmbedCommand.class)) {
      mock.when(() -> MetabaseEmbedCommand.generateDashboardIframeUrl(isNull(), eq("bordered=true&titled=true"))).thenReturn(null);

      WidgetContext result = new MetabaseWidget().execute(widgetContext);

      assertNull(result.getJsp());
    }
  }
}
