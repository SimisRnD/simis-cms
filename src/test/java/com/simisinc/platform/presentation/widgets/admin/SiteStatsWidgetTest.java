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
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.sql.Timestamp;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/9/2022 7:00 AM
 */
class SiteStatsWidgetTest extends WidgetBase {

  private void executeCardReport(String report, String title) {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>" + title + "</title>\n" +
            "  <report>" + report + "</report>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);
    new SiteStatsWidget().execute(widgetContext);
  }

  @Test
  void executeRealSessionsToday() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.countDistinctSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(42L);

      executeCardReport("real-sessions-today", "Real Sessions Today");

      Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
      Assertions.assertEquals("42", request.getAttribute("numberValue"));
    }
  }

  @Test
  void executeBotSessionsToday() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.countBotSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(8L);

      executeCardReport("bot-sessions-today", "Bot Sessions Today");

      Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
      Assertions.assertEquals("8", request.getAttribute("numberValue"));
    }
  }

  @Test
  void executeBotTrafficPercentageRounds() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      // 25 of 100 sessions are bots -- expect 25%
      sessionRepository.when(() -> SessionRepository.countDistinctSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(75L);
      sessionRepository.when(() -> SessionRepository.countBotSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(25L);

      executeCardReport("bot-traffic-percentage", "Bot Traffic %");

      Assertions.assertEquals("25", request.getAttribute("numberValue"));
    }
  }

  @Test
  void executeBotTrafficPercentageWithNoSessionsIsZeroNotADivisionError() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.countDistinctSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(0L);
      sessionRepository.when(() -> SessionRepository.countBotSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(0L);

      executeCardReport("bot-traffic-percentage", "Bot Traffic %");

      Assertions.assertEquals("0", request.getAttribute("numberValue"));
    }
  }

  @Test
  void executeDailyRealSessions() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      StatisticsData point = new StatisticsData();
      point.setLabel("2026-07-28");
      point.setValue("5");
      sessionRepository.when(() -> SessionRepository.findDailySessionsByBotStatus(anyInt(), anyBoolean()))
          .thenReturn(List.of(point));

      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"siteStats\" class=\"stats card\">\n" +
              "  <title>Daily Real Sessions</title>\n" +
              "  <report>daily-real-sessions</report>\n" +
              "  <type>line</type>\n" +
              "</widget>");
      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);

      sessionRepository.verify(() -> SessionRepository.findDailySessionsByBotStatus(30, false));
      Assertions.assertEquals(SiteStatsWidget.LINE_CHART_JSP, widgetContext.getJsp());
      Assertions.assertEquals(List.of(point), request.getAttribute("statisticsDataList"));
    }
  }

  @Test
  void executeDailyBotSessions() {
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.findDailySessionsByBotStatus(anyInt(), anyBoolean()))
          .thenReturn(List.of());

      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"siteStats\" class=\"stats card\">\n" +
              "  <title>Daily Bot Sessions</title>\n" +
              "  <report>daily-bot-sessions</report>\n" +
              "  <type>line</type>\n" +
              "</widget>");
      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);

      sessionRepository.verify(() -> SessionRepository.findDailySessionsByBotStatus(30, true));
      Assertions.assertEquals(SiteStatsWidget.LINE_CHART_JSP, widgetContext.getJsp());
    }
  }

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