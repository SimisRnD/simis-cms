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
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/9/2022 7:00 AM
 */
class SiteStatsWidgetTest extends WidgetBase {

  @Test
  void executeActiveMailingListSubscribers() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Active Subscribers</title>\n" +
            "  <report>active-mailing-list-subscribers</report>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::countActiveSubscribers).thenReturn(280L);

      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("280", request.getAttribute("numberValue"));
  }

  @Test
  void executeUnsubscribedMailingListMembers() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Unsubscribed</title>\n" +
            "  <report>unsubscribed-mailing-list-members</report>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::countUnsubscribed).thenReturn(51L);

      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("51", request.getAttribute("numberValue"));
  }

  @Test
  void executeMonthlyMailingListSubscriptions() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Monthly Mailing List Subscriptions</title>\n" +
            "  <report>monthly-mailing-list-subscriptions</report>\n" +
            "  <type>bar</type>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);

    StatisticsData point = new StatisticsData();
    point.setLabel("2026-07-01");
    point.setValue("12");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(() -> MailingListMemberRepository.findMonthlySubscriptions(anyInt())).thenReturn(List.of(point));

      new SiteStatsWidget().execute(widgetContext);

      repository.verify(() -> MailingListMemberRepository.findMonthlySubscriptions(12));
    }

    Assertions.assertEquals(SiteStatsWidget.BAR_CHART_JSP, widgetContext.getJsp());
    Assertions.assertEquals(List.of(point), request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeDailyMailingListSubscriptions() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Daily Mailing List Subscriptions</title>\n" +
            "  <report>daily-mailing-list-subscriptions</report>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(() -> MailingListMemberRepository.findDailySubscriptions(anyInt())).thenReturn(List.of());

      new SiteStatsWidget().execute(widgetContext);

      repository.verify(() -> MailingListMemberRepository.findDailySubscriptions(30));
    }

    Assertions.assertEquals(SiteStatsWidget.LINE_CHART_JSP, widgetContext.getJsp());
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