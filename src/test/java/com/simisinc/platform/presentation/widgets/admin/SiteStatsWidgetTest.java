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
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/9/2022 7:00 AM
 */
class SiteStatsWidgetTest extends WidgetBase {

  @Test
  void executeCountOnlineNow() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <icon>fa-globe</icon>\n" +
            "  <title>Online Now</title>\n" +
            "  <label>Sessions</label>\n" +
            "  <label1>Session</label1>\n" +
            "  <report>total-sessions-now</report>\n" +
            "</widget>");

    try (MockedStatic<SessionRepository> sessionRepositoryMockedStatic = mockStatic(SessionRepository.class)) {
      sessionRepositoryMockedStatic.when(SessionRepository::countOnlineNow).thenReturn(100L);

      // Use admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("Online Now", request.getAttribute("title"));
    Assertions.assertEquals("100", request.getAttribute("numberValue"));
  }

  @Test
  void action() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <icon>fa-globe</icon>\n" +
            "  <title>Online Now</title>\n" +
            "  <label>Sessions</label>\n" +
            "  <label1>Session</label1>\n" +
            "  <report>total-sessions-now</report>\n" +
            "</widget>");

    try (MockedStatic<SessionRepository> sessionRepositoryMockedStatic = mockStatic(SessionRepository.class)) {
      sessionRepositoryMockedStatic.when(SessionRepository::countOnlineNow).thenReturn(100L);

      SiteStatsWidget widget = new SiteStatsWidget();
      widget.action(widgetContext);
    }

    Assertions.assertNotNull(widgetContext.getJson());
  }
}