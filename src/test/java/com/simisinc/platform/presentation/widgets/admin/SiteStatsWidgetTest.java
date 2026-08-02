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
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.VisitorRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * @author matt rajkowski
 * @created 5/9/2022 7:00 AM
 */
class SiteStatsWidgetTest extends WidgetBase {

  private void assertCardReport(String report, String title, Runnable stubMock, long expectedValue) {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <icon>fa-users</icon>\n" +
            "  <title>" + title + "</title>\n" +
            "  <report>" + report + "</report>\n" +
            "</widget>");
    setRoles(widgetContext, ADMIN);
    stubMock.run();

    SiteStatsWidget widget = new SiteStatsWidget();
    widget.execute(widgetContext);

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals(title, request.getAttribute("title"));
    Assertions.assertEquals(String.valueOf(expectedValue), request.getAttribute("numberValue"));
  }

  @Test
  void executeEnabledAccounts() {
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      assertCardReport("enabled-accounts", "Enabled Accounts",
          () -> userRepository.when(UserRepository::countEnabledAccounts).thenReturn(150L), 150L);
    }
  }

  @Test
  void executeValidatedAccounts() {
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      assertCardReport("validated-accounts", "Validated Accounts",
          () -> userRepository.when(UserRepository::countValidatedAccounts).thenReturn(120L), 120L);
    }
  }

  @Test
  void executeNewRegistrationsThisMonth() {
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      assertCardReport("new-registrations-this-month", "New This Month",
          () -> userRepository.when(UserRepository::countNewRegistrationsThisMonth).thenReturn(7L), 7L);
    }
  }

  @Test
  void executeAdminStaffAccounts() {
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      assertCardReport("admin-staff-accounts", "Admin/Staff Accounts",
          () -> userRepository.when(UserRepository::countAccountsWithAnyRole).thenReturn(12L), 12L);
    }
  }

  @Test
  void executePublicAccounts() {
    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      assertCardReport("public-accounts", "Public Accounts",
          () -> userRepository.when(UserRepository::countPublicAccounts).thenReturn(177L), 177L);
    }
  }

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

  @Test
  void executeFailedLogins24hIsCriticalWhenAnyOccurred() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Failed Logins (24h)</title>\n" +
            "  <report>failed-logins-24h</report>\n" +
            "</widget>");

    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      // The real count comes from DataConstraints being mutated as a side effect of the DB call
      // (see DB.selectAllFrom); replicate that here rather than mocking a return value that this
      // widget code never actually reads.
      auditLogRepository
          .when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenAnswer(invocation -> {
            DataConstraints constraints = invocation.getArgument(1);
            constraints.setTotalRecordCount(3);
            return List.of();
          });

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("3", request.getAttribute("numberValue"));
    Assertions.assertEquals("critical", request.getAttribute("severity"));
  }

  @Test
  void executeFailedLogins24hIsOkWhenNoneOccurred() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Failed Logins (24h)</title>\n" +
            "  <report>failed-logins-24h</report>\n" +
            "</widget>");

    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      auditLogRepository
          .when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenAnswer(invocation -> {
            DataConstraints constraints = invocation.getArgument(1);
            constraints.setTotalRecordCount(0);
            return List.of();
          });

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals("0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeLockedAccounts() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Locked Accounts</title>\n" +
            "  <report>locked-accounts</report>\n" +
            "</widget>");

    try (MockedStatic<UserRepository> userRepository = mockStatic(UserRepository.class)) {
      userRepository.when(UserRepository::countLockedAccounts).thenReturn(2L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("2", request.getAttribute("numberValue"));
    Assertions.assertEquals("critical", request.getAttribute("severity"));
  }

  @Test
  void executeDraftsAwaitingReview() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Drafts Awaiting Review</title>\n" +
            "  <report>drafts-awaiting-review</report>\n" +
            "</widget>");

    try (MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class)) {
      contentRepository.when(() -> ContentRepository.countByDraftStatus("submitted")).thenReturn(4L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals("4", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  @Test
  void executeScheduledNotLive() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Scheduled, Not Yet Live</title>\n" +
            "  <report>scheduled-not-live</report>\n" +
            "</widget>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::countScheduledNotYetLive).thenReturn(1L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals("1", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  @Test
  void executeExpiringSoon() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Expiring Soon</title>\n" +
            "  <report>expiring-soon</report>\n" +
            "</widget>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::countExpiringSoon).thenReturn(2L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("2", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  @Test
  void executeExpiringSoonIsOkWhenNoneAreExpiring() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Expiring Soon</title>\n" +
            "  <report>expiring-soon</report>\n" +
            "</widget>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      webPageRepository.when(WebPageRepository::countExpiringSoon).thenReturn(0L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals("0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeSubmissionsAwaitingReview() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Submissions Awaiting Review</title>\n" +
            "  <report>submissions-awaiting-review</report>\n" +
            "</widget>");

    try (MockedStatic<FormDataRepository> formDataRepository = mockStatic(FormDataRepository.class)) {
      formDataRepository.when(FormDataRepository::countAwaitingReview).thenReturn(0L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals("0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeBotSessionsTodayIsNeverAnAlert() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Bot Sessions Today</title>\n" +
            "  <report>bot-sessions-today</report>\n" +
            "</widget>");

    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.countDistinctBotSessions(any(Timestamp.class), any(Timestamp.class)))
          .thenReturn(57L);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    // Bot traffic is informational, never a red/yellow alert regardless of volume
    Assertions.assertEquals("57", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeRecentAdminActionsReturnsTheDedicatedListJsp() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Recent Admin Activity</title>\n" +
            "  <report>recent-admin-actions</report>\n" +
            "</widget>");

    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      auditLogRepository.when(() -> AuditLogRepository.findAll(any(AuditLogSpecification.class), any(DataConstraints.class)))
          .thenReturn(List.of());

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.RECENT_ACTIONS_JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("recentActionsList"));
  }

  @Test
  void findRecentAdminActionsMergesSortsAndTruncatesAcrossCategories() {
    AuditLog oldest = eventAt("content", 1_000L);
    AuditLog middle = eventAt("configuration", 2_000L);
    AuditLog newest = eventAt("user_management", 3_000L);

    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      auditLogRepository.when(() -> AuditLogRepository.findAll(
          argThat(spec -> "content".equals(spec.getEventCategory())), any(DataConstraints.class)))
          .thenReturn(List.of(oldest));
      auditLogRepository.when(() -> AuditLogRepository.findAll(
          argThat(spec -> "configuration".equals(spec.getEventCategory())), any(DataConstraints.class)))
          .thenReturn(List.of(middle));
      auditLogRepository.when(() -> AuditLogRepository.findAll(
          argThat(spec -> "user_management".equals(spec.getEventCategory())), any(DataConstraints.class)))
          .thenReturn(List.of(newest));

      List<AuditLog> result = SiteStatsWidget.findRecentAdminActions(2);

      // Newest first, and truncated to the requested limit even though 3 records were found
      Assertions.assertEquals(2, result.size());
      Assertions.assertEquals(newest, result.get(0));
      Assertions.assertEquals(middle, result.get(1));
    }
  }

  private static AuditLog eventAt(String category, long epochMilli) {
    AuditLog auditLog = new AuditLog();
    auditLog.setEventCategory(category);
    auditLog.setEventType(category + ".test");
    auditLog.setOccurred(new Timestamp(epochMilli));
    return auditLog;
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
  void executeMailingListClassificationBreakdown() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Email Deliverability</title>\n" +
            "  <report>mailing-list-classification-breakdown</report>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("valid", "42"), statistic("unclassified", "8"));
    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::findClassificationBreakdown).thenReturn(data);
      repository.when(MailingListMemberRepository::findLastClassifiedAt)
          .thenReturn(Timestamp.valueOf("2026-07-28 12:00:00"));

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Status", request.getAttribute("label"));
    Assertions.assertEquals("Subscribers", request.getAttribute("value"));
    Assertions.assertNotNull(request.getAttribute("asOfDate"));
  }

  @Test
  void executeMailingListClassificationBreakdownOmitsAsOfDateWhenNeverClassified() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Email Deliverability</title>\n" +
            "  <report>mailing-list-classification-breakdown</report>\n" +
            "</widget>");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::findClassificationBreakdown).thenReturn(List.of());
      repository.when(MailingListMemberRepository::findLastClassifiedAt).thenReturn(null);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertNull(request.getAttribute("asOfDate"));
  }

  @Test
  void executeMailingListQualityScore() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Mailing List Quality Score</title>\n" +
            "  <report>mailing-list-quality-score</report>\n" +
            "</widget>");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::findQualityScorePercent).thenReturn(87.5);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("87.5", request.getAttribute("numberValue"));
  }

  @Test
  void executeMailingListSpamRateAlertIsOkBelowThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Mailing List Spam Rate</title>\n" +
            "  <report>mailing-list-spam-rate-alert</report>\n" +
            "</widget>");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      // 95% quality -> 5% spam rate, below the default 10% threshold
      repository.when(MailingListMemberRepository::findQualityScorePercent).thenReturn(95.0);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.quarantine.alertThresholdPercent"))
          .thenReturn("10");
      repository.when(() -> MailingListMemberRepository.resolveQuarantineAlertThresholdPercent("10")).thenReturn(10);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("5.0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeMailingListSpamRateAlertIsWarningAboveThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Mailing List Spam Rate</title>\n" +
            "  <report>mailing-list-spam-rate-alert</report>\n" +
            "</widget>");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      // 70% quality -> 30% spam rate, above the default 10% threshold
      repository.when(MailingListMemberRepository::findQualityScorePercent).thenReturn(70.0);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("mailing-list.quarantine.alertThresholdPercent"))
          .thenReturn("10");
      repository.when(() -> MailingListMemberRepository.resolveQuarantineAlertThresholdPercent("10")).thenReturn(10);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("30.0", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  @Test
  void executeZeroResultSearchAlertIsOkBelowThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Zero-Result Searches (24h)</title>\n" +
            "  <report>zero-result-search-alert</report>\n" +
            "</widget>");

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SearchAnalyticsRepository.countZeroResultSearches(1)).thenReturn(5L);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("search.zeroResultAlertThreshold"))
          .thenReturn("20");
      repository.when(() -> SearchAnalyticsRepository.resolveZeroResultAlertThreshold("20")).thenReturn(20);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("5", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeZeroResultSearchAlertIsWarningAboveThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Zero-Result Searches (24h)</title>\n" +
            "  <report>zero-result-search-alert</report>\n" +
            "</widget>");

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> SearchAnalyticsRepository.countZeroResultSearches(1)).thenReturn(35L);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("search.zeroResultAlertThreshold"))
          .thenReturn("20");
      repository.when(() -> SearchAnalyticsRepository.resolveZeroResultAlertThreshold("20")).thenReturn(20);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("35", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  @Test
  void executeFacetAdoptionRateComputesAPercentage() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Faceted Search Adoption % (7d)</title>\n" +
            "  <report>facet-adoption-rate</report>\n" +
            "  <days>7</days>\n" +
            "</widget>");

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.countSearches(7)).thenReturn(200L);
      repository.when(() -> SearchAnalyticsRepository.countSearchesWithFacetApplied(7)).thenReturn(50L);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("25.0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeFacetAdoptionRateIsZeroWhenThereAreNoSearches() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Faceted Search Adoption % (7d)</title>\n" +
            "  <report>facet-adoption-rate</report>\n" +
            "  <days>7</days>\n" +
            "</widget>");

    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.countSearches(7)).thenReturn(0L);
      repository.when(() -> SearchAnalyticsRepository.countSearchesWithFacetApplied(7)).thenReturn(0L);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("0.0", request.getAttribute("numberValue"),
        "a zero-search denominator must not divide by zero");
  }

  @Test
  void executeFacetUsageBreakdown() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Facet Usage Breakdown</title>\n" +
            "  <report>facet-usage-breakdown</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("categoryId", "12"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findFacetUsageBreakdown(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Facet", request.getAttribute("label"));
    Assertions.assertEquals("Searches", request.getAttribute("value"));
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

  @Test
  void executeSolutionTypeTraffic() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Traffic by Solution Type</title>\n" +
            "  <report>solution-type-traffic</report>\n" +
            "  <type>bar</type>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("government-solution", "120"), statistic("careers", "40"));
    try (MockedStatic<WebPageHitRepository> repository = mockStatic(WebPageHitRepository.class)) {
      repository.when(() -> WebPageHitRepository.findTrafficBySolutionType(30)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);

      repository.verify(() -> WebPageHitRepository.findTrafficBySolutionType(30));
    }

    Assertions.assertEquals(SiteStatsWidget.BAR_CHART_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeSolutionTypeEngagement() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Engagement by Solution Type</title>\n" +
            "  <report>solution-type-engagement</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("government-solution", "2.50"));
    try (MockedStatic<WebPageHitRepository> repository = mockStatic(WebPageHitRepository.class)) {
      repository.when(() -> WebPageHitRepository.findEngagementBySolutionType(30)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);

      repository.verify(() -> WebPageHitRepository.findEngagementBySolutionType(30));
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Solution Type", request.getAttribute("label"));
    Assertions.assertEquals("Avg Page Views / Session", request.getAttribute("value"));
  }

  @Test
  void executeSolutionTypeEngagementHonorsConfiguredLabelAndValue() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Engagement by Solution Type</title>\n" +
            "  <report>solution-type-engagement</report>\n" +
            "  <label>Solution</label>\n" +
            "  <value>Depth</value>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    try (MockedStatic<WebPageHitRepository> repository = mockStatic(WebPageHitRepository.class)) {
      repository.when(() -> WebPageHitRepository.findEngagementBySolutionType(30)).thenReturn(List.of());

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals("Solution", request.getAttribute("label"));
    Assertions.assertEquals("Depth", request.getAttribute("value"));
  }

  @Test
  void executePagesPerSession() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Pages per Session</title>\n" +
            "  <report>pages-per-session</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    try (MockedStatic<WebPageHitRepository> webPageHitRepositoryMockedStatic = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepositoryMockedStatic.when(() -> WebPageHitRepository.findAvgPagesPerSession(30)).thenReturn(2.375);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("2.4", request.getAttribute("numberValue"));
  }

  @Test
  void executeReturnVisitorRate() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Return Visitor Rate</title>\n" +
            "  <report>return-visitor-rate</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    try (MockedStatic<VisitorRepository> visitorRepositoryMockedStatic = mockStatic(VisitorRepository.class)) {
      visitorRepositoryMockedStatic.when(() -> VisitorRepository.findReturnVisitorRatePercent(30)).thenReturn(33.333);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("33.3", request.getAttribute("numberValue"));
  }

  @Test
  void executeAvgTimeOnPage() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Avg Time on Page</title>\n" +
            "  <report>avg-time-on-page</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("/contact-us", "42.3s"));
    try (MockedStatic<WebPageHitRepository> webPageHitRepositoryMockedStatic = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepositoryMockedStatic.when(() -> WebPageHitRepository.findAvgTimeOnPageByPath(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Page", request.getAttribute("label"));
    Assertions.assertEquals("Avg Time", request.getAttribute("value"));
  }

  @Test
  void executeHighTrafficLowEngagement() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>High Traffic, Low Engagement</title>\n" +
            "  <report>high-traffic-low-engagement</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("/popular", "8 hits, 2.0s avg"));
    try (MockedStatic<WebPageHitRepository> webPageHitRepositoryMockedStatic = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepositoryMockedStatic.when(() -> WebPageHitRepository.findHighTrafficLowEngagementPages(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Page", request.getAttribute("label"));
    Assertions.assertEquals("Hits / Avg Time", request.getAttribute("value"));
  }

  @Test
  void executeLowTrafficHighEngagement() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Low Traffic, High Engagement</title>\n" +
            "  <report>low-traffic-high-engagement</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("/deep-dive", "5 hits, 120.0s avg"));
    try (MockedStatic<WebPageHitRepository> webPageHitRepositoryMockedStatic = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepositoryMockedStatic.when(() -> WebPageHitRepository.findLowTrafficHighEngagementPages(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Page", request.getAttribute("label"));
    Assertions.assertEquals("Hits / Avg Time", request.getAttribute("value"));
  }

  private static StatisticsData statistic(String label, String value) {
    StatisticsData data = new StatisticsData();
    data.setLabel(label);
    data.setValue(value);
    return data;
  }
}