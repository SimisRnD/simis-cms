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

import java.sql.Timestamp;
import java.util.List;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

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

  @Test
  void executeTotalFormSubmissions() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Total Form Submissions</title>\n" +
            "  <report>total-form-submissions</report>\n" +
            "</widget>");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(FormDataRepository::countTotalSubmissions).thenReturn(42L);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("42", request.getAttribute("numberValue"));
  }

  @Test
  void executeFormSubmissionsSpamFlagged() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Spam-Flagged Submissions (30d)</title>\n" +
            "  <report>form-submissions-spam-flagged</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.countSpamFlagged(Mockito.any(), Mockito.any())).thenReturn(7L);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("7", request.getAttribute("numberValue"));
  }

  @Test
  void executeDailyFormSubmissions() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Daily Form Submissions</title>\n" +
            "  <report>daily-form-submissions</report>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("2026-07-28", "3"));
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findDailySubmissions(30)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.LINE_CHART_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeMonthlyFormSubmissions() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Monthly Form Submissions</title>\n" +
            "  <report>monthly-form-submissions</report>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("2026-07", "12"));
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findMonthlySubmissions(12)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.LINE_CHART_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeTopForms() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Top Forms by Submission Volume</title>\n" +
            "  <report>top-forms</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("contact-us", "20"));
    try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.findSubmissionCountsByForm(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Form", request.getAttribute("label"));
    Assertions.assertEquals("Submissions", request.getAttribute("value"));
  }

  @Test
  void executeFormFailuresByReason() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Rejected Submissions by Reason</title>\n" +
            "  <report>form-failures-by-reason</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("captcha_failed", "9"));
    try (MockedStatic<FormSubmissionFailureRepository> repositoryMockedStatic = mockStatic(FormSubmissionFailureRepository.class)) {
      repositoryMockedStatic.when(() -> FormSubmissionFailureRepository.findFailureCountsByReason(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeConversionRate() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>% Conversion (30d)</title>\n" +
            "  <report>conversion-rate</report>\n" +
            "  <pagePath>/contact-us</pagePath>\n" +
            "  <formUniqueId>contact-us</formUniqueId>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    try (MockedStatic<WebPageHitRepository> webPageHitRepositoryMockedStatic = mockStatic(WebPageHitRepository.class);
         MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
      webPageHitRepositoryMockedStatic.when(() -> WebPageHitRepository.countPageViews(
          Mockito.eq("/contact-us"), Mockito.any(Timestamp.class), Mockito.any(Timestamp.class))).thenReturn(200L);
      formDataRepositoryMockedStatic.when(() -> FormDataRepository.countSubmissions(
          Mockito.eq("contact-us"), Mockito.any(Timestamp.class), Mockito.any(Timestamp.class))).thenReturn(25L);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    // 25 submissions / 200 page views = 12.5%
    Assertions.assertEquals("12.5", request.getAttribute("numberValue"));
  }

  @Test
  void executeConversionRateWithoutPageOrFormConfiguredSkipsTheReport() {
    // No pagePath/formUniqueId preferences -- this report can't run without an admin-configured pairing,
    // and must not blow up or silently query with a null page/form.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>% Conversion (30d)</title>\n" +
            "  <report>conversion-rate</report>\n" +
            "</widget>");

    setRoles(widgetContext, ADMIN);
    SiteStatsWidget widget = new SiteStatsWidget();
    widget.execute(widgetContext);

    Assertions.assertNull(widgetContext.getJsp());
  }

  private static StatisticsData statistic(String label, String value) {
    StatisticsData data = new StatisticsData();
    data.setLabel(label);
    data.setValue(value);
    return data;
  }
}