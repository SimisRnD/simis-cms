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
import java.time.Duration;
import java.util.List;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.dashboard.BotIdentityStats;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FunnelEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileDownloadRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import java.util.LinkedHashMap;
import java.util.Map;

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

  /**
   * Session.latitude/longitude are primitive doubles defaulting to 0/0 when never resolved.
   * findDailyUniqueLocations() already excludes anonymous sessions, but an authenticated one can
   * still reach here unresolved -- e.g. when MaxMind resolves a city/subdivision without its
   * separate location sub-record. The map must drop those rather than plotting them at literal
   * (0,0) ("Null Island").
   */
  @Test
  void executeLocationsMapExcludesSessionsWithUnresolvedZeroZeroCoordinates() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Visitor Locations</title>\n" +
            "  <report>locations-map</report>\n" +
            "</widget>");

    Session unresolved = new Session(0, 0);
    Session realLocation = new Session(36.7282, -76.5836); // Suffolk, VA
    Session onThePrimeMeridianButNotTheEquator = new Session(0, 5.5); // a genuine (0, non-zero) location

    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class);
        MockedStatic<FindMapTilesCredentialsCommand> mapTilesCommand = mockStatic(FindMapTilesCredentialsCommand.class)) {
      sessionRepository.when(() -> SessionRepository.findDailyUniqueLocations(anyInt()))
          .thenReturn(List.of(unresolved, realLocation, onThePrimeMeridianButNotTheEquator));
      mapTilesCommand.when(FindMapTilesCredentialsCommand::getCredentials)
          .thenReturn(new MapCredentials("openstreetmap", null));

      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.LOCATIONS_MAP_JSP, widgetContext.getJsp());
    @SuppressWarnings("unchecked")
    List<Session> plottedSessions = (List<Session>) request.getAttribute("sessionList");
    Assertions.assertEquals(2, plottedSessions.size(), "the (0,0) session must be dropped, the other two kept");
    Assertions.assertTrue(plottedSessions.contains(realLocation));
    Assertions.assertTrue(plottedSessions.contains(onThePrimeMeridianButNotTheEquator));
  }

  @Test
  void executeLocationsMapReturnsNullWhenMapServiceIsNotConfigured() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\">\n" +
            "  <title>Visitor Locations</title>\n" +
            "  <report>locations-map</report>\n" +
            "</widget>");

    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class);
        MockedStatic<FindMapTilesCredentialsCommand> mapTilesCommand = mockStatic(FindMapTilesCredentialsCommand.class)) {
      sessionRepository.when(() -> SessionRepository.findDailyUniqueLocations(anyInt()))
          .thenReturn(List.of(new Session(36.7282, -76.5836)));
      mapTilesCommand.when(FindMapTilesCredentialsCommand::getCredentials).thenReturn(null);

      new SiteStatsWidget().execute(widgetContext);
    }

    // execute() itself always returns the (non-null) context -- only the internal runReport()
    // helper returns null, which lands here as a null jsp.
    Assertions.assertNull(widgetContext.getJsp());
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
      auditLogRepository.when(() -> AuditLogRepository.findRecentActivity(
          any(), any(Timestamp.class), any(), any(DataConstraints.class)))
          .thenReturn(List.of());

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.RECENT_ACTIONS_JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("recentActionsList"));
  }

  @Test
  void findRecentAdminActionsDelegatesToFindRecentActivityAcrossAllCategoriesWithATrailingWindow() {
    // Issue #1006: this tile used to run one findAll query per category (content/configuration/
    // user_management only, no time window) and merge the results in Java. It now delegates to the
    // general-purpose findRecentActivity query -- unconstrained category set (null = all 6, including
    // the authentication/authorization/data_access categories the old version omitted) and a real
    // trailing window, matching the /admin/activity feed's default.
    AuditLog newest = eventAt("authentication", 3_000L);

    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      auditLogRepository.when(() -> AuditLogRepository.findRecentActivity(
          eq(null), any(Timestamp.class), eq(null), any(DataConstraints.class)))
          .thenReturn(List.of(newest));

      List<AuditLog> result = SiteStatsWidget.findRecentAdminActions(5);

      Assertions.assertEquals(1, result.size());
      Assertions.assertEquals(newest, result.get(0));

      // The window passed must be roughly "now minus the shared default trailing window", not unbounded
      ArgumentCaptor<Timestamp> sinceCaptor = ArgumentCaptor.forClass(Timestamp.class);
      auditLogRepository.verify(() -> AuditLogRepository.findRecentActivity(
          eq(null), sinceCaptor.capture(), eq(null), any(DataConstraints.class)));
      long expectedMillisAgo = Duration.ofDays(AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS).toMillis();
      long actualMillisAgo = System.currentTimeMillis() - sinceCaptor.getValue().getTime();
      Assertions.assertTrue(Math.abs(actualMillisAgo - expectedMillisAgo) < 5_000,
          "expected the cutoff to be ~" + AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS + " days ago, was " + sinceCaptor.getValue());
    }
  }

  @Test
  void findRecentAdminActionsReturnsAnEmptyListRatherThanNullWhenNothingIsFound() {
    try (MockedStatic<AuditLogRepository> auditLogRepository = mockStatic(AuditLogRepository.class)) {
      auditLogRepository.when(() -> AuditLogRepository.findRecentActivity(
          eq(null), any(Timestamp.class), eq(null), any(DataConstraints.class)))
          .thenReturn(null);

      List<AuditLog> result = SiteStatsWidget.findRecentAdminActions(5);

      Assertions.assertNotNull(result);
      Assertions.assertTrue(result.isEmpty());
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
  void executeFileDownloadsPassesTheSelectedWindowThrough() {
    // The tab a reader picks has to reach the query. A report that ignored its interval would look
    // perfectly healthy on screen -- a populated table, a highlighted tab -- while showing the same
    // numbers for every window.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Top Downloads</title>\n" +
            "  <report>file-downloads</report>\n" +
            "  <interval>30d</interval>\n" +
            "  <limit>20</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("NAICS Codes.pdf", "11"));
    try (MockedStatic<FileDownloadRepository> repository = mockStatic(FileDownloadRepository.class)) {
      repository.when(() -> FileDownloadRepository.findTopDownloads(30, 'd', 20)).thenReturn(data);

      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("File", request.getAttribute("label"));
    Assertions.assertEquals("Downloads", request.getAttribute("value"));
  }

  @Test
  void executeFileDownloadsUsesTheHoursWindowForTheTodayTab() {
    // "Today" is 12h, an hours interval rather than days. Passing the unit through matters: 12
    // interpreted as days would silently widen the shortest tab to a fortnight.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Top Downloads</title>\n" +
            "  <report>file-downloads</report>\n" +
            "  <interval>12h</interval>\n" +
            "  <limit>20</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("tiny-test.pdf", "3"));
    try (MockedStatic<FileDownloadRepository> repository = mockStatic(FileDownloadRepository.class)) {
      repository.when(() -> FileDownloadRepository.findTopDownloads(12, 'h', 20)).thenReturn(data);

      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
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
  void executeRequestRateSpikeAlertIsOkBelowThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Peak Requests / IP (1h)</title>\n" +
            "  <report>request-rate-spike-alert</report>\n" +
            "</widget>");

    try (MockedStatic<WebPageHitRepository> repository = mockStatic(WebPageHitRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> WebPageHitRepository.findMaxHitsFromSingleIp(1)).thenReturn(120L);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.ipRequestRateAlertThreshold"))
          .thenReturn("300");
      repository.when(() -> WebPageHitRepository.resolveIpRequestRateAlertThreshold("300")).thenReturn(300);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("120", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeRequestRateSpikeAlertIsWarningAboveThreshold() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Peak Requests / IP (1h)</title>\n" +
            "  <report>request-rate-spike-alert</report>\n" +
            "</widget>");

    try (MockedStatic<WebPageHitRepository> repository = mockStatic(WebPageHitRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repository.when(() -> WebPageHitRepository.findMaxHitsFromSingleIp(1)).thenReturn(450L);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.ipRequestRateAlertThreshold"))
          .thenReturn("300");
      repository.when(() -> WebPageHitRepository.resolveIpRequestRateAlertThreshold("300")).thenReturn(300);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("450", request.getAttribute("numberValue"));
    Assertions.assertEquals("warning", request.getAttribute("severity"));
  }

  /** The recent window's startDate is always much closer to "now" than the baseline window's. */
  private static boolean isRecentWindowStart(Timestamp startDate) {
    return startDate.after(new Timestamp(System.currentTimeMillis() - Duration.ofDays(2).toMillis()));
  }

  @Test
  void executeGeoAnomalyAlertIsOkWhenNoNewCountryAppears() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>New Countries in Top 5</title>\n" +
            "  <report>geo-anomaly-alert</report>\n" +
            "</widget>");

    StatisticsData canada = new StatisticsData();
    canada.setLabel("Canada");
    canada.setValue("10");
    StatisticsData mexico = new StatisticsData();
    mexico.setLabel("Mexico");
    mexico.setValue("5");

    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.geoAnomalyBaselineDays")).thenReturn("30");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.geoAnomalyRecentHours")).thenReturn("24");
      sessionRepository.when(() -> SessionRepository.resolveGeoAnomalyBaselineDays("30")).thenReturn(30);
      sessionRepository.when(() -> SessionRepository.resolveGeoAnomalyRecentHours("24")).thenReturn(24);
      // The recent window's top 5 (Canada) is a subset of the baseline's (Canada, Mexico) -- no new country.
      sessionRepository.when(() -> SessionRepository.findTopCountriesByCount(
          argThat(SiteStatsWidgetTest::isRecentWindowStart), any(Timestamp.class), eq(5)))
          .thenReturn(List.of(canada));
      sessionRepository.when(() -> SessionRepository.findTopCountriesByCount(
          argThat(startDate -> !isRecentWindowStart(startDate)), any(Timestamp.class), eq(5)))
          .thenReturn(List.of(canada, mexico));

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("0", request.getAttribute("numberValue"));
    Assertions.assertEquals("ok", request.getAttribute("severity"));
  }

  @Test
  void executeGeoAnomalyAlertIsWarningWhenANewCountryAppearsInTheTop5() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>New Countries in Top 5</title>\n" +
            "  <report>geo-anomaly-alert</report>\n" +
            "</widget>");

    StatisticsData canada = new StatisticsData();
    canada.setLabel("Canada");
    canada.setValue("10");
    StatisticsData newCountry = new StatisticsData();
    newCountry.setLabel("Elbonia");
    newCountry.setValue("8");
    StatisticsData mexico = new StatisticsData();
    mexico.setLabel("Mexico");
    mexico.setValue("5");

    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.geoAnomalyBaselineDays")).thenReturn("30");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.geoAnomalyRecentHours")).thenReturn("24");
      sessionRepository.when(() -> SessionRepository.resolveGeoAnomalyBaselineDays("30")).thenReturn(30);
      sessionRepository.when(() -> SessionRepository.resolveGeoAnomalyRecentHours("24")).thenReturn(24);
      // Elbonia is in the recent top 5 but never appeared in the baseline top 5 -- one new country.
      sessionRepository.when(() -> SessionRepository.findTopCountriesByCount(
          argThat(SiteStatsWidgetTest::isRecentWindowStart), any(Timestamp.class), eq(5)))
          .thenReturn(List.of(canada, newCountry));
      sessionRepository.when(() -> SessionRepository.findTopCountriesByCount(
          argThat(startDate -> !isRecentWindowStart(startDate)), any(Timestamp.class), eq(5)))
          .thenReturn(List.of(canada, mexico));

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.ALERT_CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("1", request.getAttribute("numberValue"));
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
  void executeSearchVolumeByType() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Search Volume by Content Type</title>\n" +
            "  <report>search-volume-by-type</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("pages", "42"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findSearchVolumeByType(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Content Type", request.getAttribute("label"));
    Assertions.assertEquals("Searches", request.getAttribute("value"));
  }

  @Test
  void executeZeroResultRateByType() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Zero-Result Rate by Content Type</title>\n" +
            "  <report>zero-result-rate-by-type</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("items", "33.3"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findZeroResultRateByType(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Content Type", request.getAttribute("label"));
    Assertions.assertEquals("Zero-Result Rate %", request.getAttribute("value"));
  }

  @Test
  void executeTopSearchPaths() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Top Pages Generating Searches</title>\n" +
            "  <report>top-search-paths</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("/products", "17"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findTopSearchPaths(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Page", request.getAttribute("label"));
    Assertions.assertEquals("Searches", request.getAttribute("value"));
  }

  @Test
  void executeTopZeroResultSearchPaths() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Top Pages Generating Zero-Result Searches</title>\n" +
            "  <report>top-zero-result-search-paths</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("/catalog", "9"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findTopZeroResultPaths(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Page", request.getAttribute("label"));
    Assertions.assertEquals("Zero-Result Searches", request.getAttribute("value"));
  }

  @Test
  void executeNearMissSearchTerms() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Near-Miss Search Terms</title>\n" +
            "  <report>near-miss-search-terms</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<StatisticsData> data = List.of(statistic("widgets", "4"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class)) {
      repository.when(() -> SearchAnalyticsRepository.findNearMissTerms(30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Search Term", request.getAttribute("label"));
    Assertions.assertEquals("Low-Result Searches", request.getAttribute("value"));
  }

  @Test
  void executeHighValueSearchTerms() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>High-Value Search Terms</title>\n" +
            "  <report>high-value-search-terms</report>\n" +
            "  <days>30</days>\n" +
            "  <limit>10</limit>\n" +
            "</widget>");

    List<String> parsedTerms = List.of("pricing", "demo");
    List<StatisticsData> data = List.of(statistic("pricing", "4"));
    try (MockedStatic<SearchAnalyticsRepository> repository = mockStatic(SearchAnalyticsRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("search.highValueTerms"))
          .thenReturn("Pricing, Demo");
      repository.when(() -> SearchAnalyticsRepository.parseHighValueTerms("Pricing, Demo")).thenReturn(parsedTerms);
      repository.when(() -> SearchAnalyticsRepository.findHighValueTermActivity(parsedTerms, 30, 10)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
    Assertions.assertEquals("Search Term", request.getAttribute("label"));
    Assertions.assertEquals("Successful Searches", request.getAttribute("value"));
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
  void executeContactFormFunnel() {
    // Issue #565 phase 1: per-stage counts with drop-off shown as a percentage of the previous stage
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Contact Form Funnel (30d)</title>\n" +
            "  <report>contact-form-funnel</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    Map<String, Long> stageCounts = new LinkedHashMap<>();
    stageCounts.put(FunnelEventCommand.STAGE_VIEW, 100L);
    stageCounts.put(FunnelEventCommand.STAGE_SUBMITTED, 40L);
    stageCounts.put(FunnelEventCommand.STAGE_PROCESSED, 30L);

    try (MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      repository.when(() -> FunnelEventRepository.countStagesInRange(
          Mockito.eq(FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY), Mockito.any(Timestamp.class), Mockito.any(Timestamp.class)))
          .thenReturn(stageCounts);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    List<StatisticsData> statisticsDataList = (List<StatisticsData>) request.getAttribute("statisticsDataList");
    Assertions.assertEquals(3, statisticsDataList.size());
    Assertions.assertEquals("Page Views", statisticsDataList.get(0).getLabel());
    Assertions.assertEquals("100", statisticsDataList.get(0).getValue());
    // 40 / 100 = 40.0% of views
    Assertions.assertEquals("Form Submitted (40.0% of views)", statisticsDataList.get(1).getLabel());
    Assertions.assertEquals("40", statisticsDataList.get(1).getValue());
    // 30 / 40 = 75.0% of submitted
    Assertions.assertEquals("Processed (75.0% of submitted)", statisticsDataList.get(2).getLabel());
    Assertions.assertEquals("30", statisticsDataList.get(2).getValue());
  }

  @Test
  void executeContactFormFunnelWithNoEventsYetShowsZeroesWithoutPercentages() {
    // Funnel tracking not yet configured/no traffic yet -- must render a valid all-zero table, not
    // divide by zero or otherwise blow up
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Contact Form Funnel (30d)</title>\n" +
            "  <report>contact-form-funnel</report>\n" +
            "</widget>");

    try (MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      repository.when(() -> FunnelEventRepository.countStagesInRange(
          Mockito.eq(FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY), Mockito.any(Timestamp.class), Mockito.any(Timestamp.class)))
          .thenReturn(new LinkedHashMap<>());

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.TABLE_JSP, widgetContext.getJsp());
    List<StatisticsData> statisticsDataList = (List<StatisticsData>) request.getAttribute("statisticsDataList");
    Assertions.assertEquals("Page Views", statisticsDataList.get(0).getLabel());
    Assertions.assertEquals("0", statisticsDataList.get(0).getValue());
    Assertions.assertEquals("Form Submitted", statisticsDataList.get(1).getLabel());
    Assertions.assertEquals("0", statisticsDataList.get(1).getValue());
    Assertions.assertEquals("Processed", statisticsDataList.get(2).getLabel());
    Assertions.assertEquals("0", statisticsDataList.get(2).getValue());
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

  @Test
  void executeBotTrafficByIdentity() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Bot Traffic by Identity</title>\n" +
            "  <report>bot-traffic-by-identity</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");

    BotIdentityStats googlebot = new BotIdentityStats();
    googlebot.setIdentity("Googlebot");
    googlebot.setSessionCount(42);
    googlebot.setFirstSeen("Aug 1, 2026 9:00 AM");
    googlebot.setLastSeen("Aug 11, 2026 2:00 PM");
    googlebot.setTopPage("/home");
    googlebot.setTopPageHits(30);
    List<BotIdentityStats> data = List.of(googlebot);
    try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      sessionRepository.when(() -> SessionRepository.findBotSessionStatsByIdentity(30)).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      SiteStatsWidget widget = new SiteStatsWidget();
      widget.execute(widgetContext);
    }

    Assertions.assertEquals(SiteStatsWidget.BOT_IDENTITY_TABLE_JSP, widgetContext.getJsp());
    Assertions.assertEquals(data, request.getAttribute("botIdentityStatsList"));
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  // --- Time-range control on the session charts (these four reports previously ignored the
  // widget's interval entirely and queried a hardcoded 30-day / 12-month window) ---

  @Test
  void executeDailySessionsUsesTheConfiguredWindow() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Daily Sessions</title>\n" +
            "  <report>daily-sessions</report>\n" +
            "  <days>90</days>\n" +
            "</widget>");
    List<StatisticsData> data = List.of(statistic("2026-08-01", "5"));
    try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepository.when(() -> WebPageHitRepository.findDailySessions(90, 'd')).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeMonthlySessionsUsesTheConfiguredWindowInMonths() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Monthly Sessions</title>\n" +
            "  <report>monthly-sessions</report>\n" +
            "  <interval>24m</interval>\n" +
            "</widget>");
    List<StatisticsData> data = List.of(statistic("2026-08-01", "50"));
    try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepository.when(() -> WebPageHitRepository.findMonthlySessions(24, 'm')).thenReturn(data);

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }
    Assertions.assertEquals(data, request.getAttribute("statisticsDataList"));
  }

  @Test
  void executeDailyRealAndBotSessionsUseTheConfiguredWindow() {
    for (String report : List.of("daily-real-sessions", "daily-bot-sessions")) {
      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"siteStats\" class=\"stats card\">\n" +
              "  <report>" + report + "</report>\n" +
              "  <days>90</days>\n" +
              "</widget>");
      boolean isBot = "daily-bot-sessions".equals(report);
      List<StatisticsData> data = List.of(statistic("2026-08-01", "3"));
      try (MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
        sessionRepository.when(() -> SessionRepository.findDailySessionsByBotStatus(90, 'd', isBot)).thenReturn(data);

        setRoles(widgetContext, ADMIN);
        new SiteStatsWidget().execute(widgetContext);
      }
      Assertions.assertEquals(data, request.getAttribute("statisticsDataList"), report);
    }
  }

  @Test
  void executePublishesTheRenderedWindowSoTheRightTabHighlights() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <report>monthly-sessions</report>\n" +
            "  <interval>12m</interval>\n" +
            "  <options>\n" +
            "    <option name=\"6 Months\" value=\"6m\" />\n" +
            "    <option name=\"12 Months\" value=\"12m\" />\n" +
            "  </options>\n" +
            "</widget>");
    try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepository.when(() -> WebPageHitRepository.findMonthlySessions(12, 'm')).thenReturn(List.of());

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().execute(widgetContext);
    }
    Assertions.assertEquals("12m", request.getAttribute("currentValue"));
    Assertions.assertNotNull(request.getAttribute("optionsList"));
  }

  @Test
  void actionUsesTheRequestedWindow() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <report>daily-sessions</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");
    addQueryParameter(widgetContext, "value", "7d");
    try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepository.when(() -> WebPageHitRepository.findDailySessions(7, 'd'))
          .thenReturn(List.of(statistic("2026-08-01", "5")));

      setRoles(widgetContext, ADMIN);
      new SiteStatsWidget().action(widgetContext);
    }
    Assertions.assertEquals("[{\"label\":\"2026-08-01\",\"value\":\"5\"}]", widgetContext.getJson());
  }

  @Test
  void actionFallsBackToTheWidgetDefaultRatherThanFailingOnAJunkWindow() {
    // "value" is a user-supplied query parameter; it used to go straight into Integer.parseInt
    for (String junk : List.of("abc", "d", "-5d", "99999999d", "7x")) {
      addPreferencesFromWidgetXml(widgetContext,
          "<widget name=\"siteStats\" class=\"stats card\">\n" +
              "  <report>daily-sessions</report>\n" +
              "  <days>30</days>\n" +
              "</widget>");
      addQueryParameter(widgetContext, "value", junk);
      try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
        webPageHitRepository.when(() -> WebPageHitRepository.findDailySessions(30, 'd'))
            .thenReturn(List.of(statistic("2026-08-01", "5")));

        setRoles(widgetContext, ADMIN);
        Assertions.assertDoesNotThrow(() -> new SiteStatsWidget().action(widgetContext), junk);
      }
      Assertions.assertEquals("[{\"label\":\"2026-08-01\",\"value\":\"5\"}]", widgetContext.getJson(), junk);
    }
  }

  @Test
  void actionQueriesOnlyTheRequestedWindow() {
    // action() used to end with "return execute(context)", which re-ran the whole report with the
    // widget's configured window -- so every range-tab click cost two queries, and the extra one
    // wasn't even the range that was asked for. Its result was discarded: setJson() had already
    // been called, and WebContainerCommand returns as soon as it sees hasJson()
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <report>daily-sessions</report>\n" +
            "  <days>30</days>\n" +
            "</widget>");
    addQueryParameter(widgetContext, "value", "7d");
    try (MockedStatic<WebPageHitRepository> webPageHitRepository = mockStatic(WebPageHitRepository.class)) {
      webPageHitRepository.when(() -> WebPageHitRepository.findDailySessions(7, 'd'))
          .thenReturn(List.of(statistic("2026-08-01", "5")));

      setRoles(widgetContext, ADMIN);
      WidgetContext result = new SiteStatsWidget().action(widgetContext);

      webPageHitRepository.verify(() -> WebPageHitRepository.findDailySessions(7, 'd'));
      webPageHitRepository.verify(() -> WebPageHitRepository.findDailySessions(30, 'd'), Mockito.never());
      Assertions.assertSame(widgetContext, result);
    }
    Assertions.assertEquals("[{\"label\":\"2026-08-01\",\"value\":\"5\"}]", widgetContext.getJson());
  }

  @Test
  void actionWithoutAReportPreferenceStillAnswersWithJson() {
    // A misconfigured widget used to reach execute()'s "report preference was not specified" guard
    // by falling through to it; action() makes the check itself now. The response still has to be
    // JSON -- a targeted request that sets none gets treated as a form post and redirected
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"siteStats\" class=\"stats card\">\n" +
            "  <title>Daily Sessions</title>\n" +
            "</widget>");

    setRoles(widgetContext, ADMIN);
    WidgetContext result = new SiteStatsWidget().action(widgetContext);

    Assertions.assertSame(widgetContext, result);
    Assertions.assertEquals("[]", widgetContext.getJson());
  }

  @Test
  void intervalParsesTheDropDownSyntaxAndFallsBackOnAnythingElse() {
    SiteStatsWidget.Interval fallback = new SiteStatsWidget.Interval(7, 'd');
    Assertions.assertEquals(90, SiteStatsWidget.Interval.parse("90d", fallback).value);
    Assertions.assertEquals('d', SiteStatsWidget.Interval.parse("90d", fallback).type);
    Assertions.assertEquals('w', SiteStatsWidget.Interval.parse("2w", fallback).type);
    Assertions.assertEquals('m', SiteStatsWidget.Interval.parse("6M", fallback).type, "case-insensitive");
    Assertions.assertEquals('y', SiteStatsWidget.Interval.parse("1y", fallback).type);
    Assertions.assertEquals('h', SiteStatsWidget.Interval.parse("12h", fallback).type);

    // A bare number keeps <days>' historical meaning
    SiteStatsWidget.Interval bare = SiteStatsWidget.Interval.parse("30", fallback);
    Assertions.assertEquals(30, bare.value);
    Assertions.assertEquals('d', bare.type);

    for (String bad : List.of("", "   ", "abc", "d", "-5d", "0d", "99999999d", "7x")) {
      SiteStatsWidget.Interval parsed = SiteStatsWidget.Interval.parse(bad, fallback);
      Assertions.assertEquals(7, parsed.value, bad);
      Assertions.assertEquals('d', parsed.type, bad);
    }
    Assertions.assertEquals(7, SiteStatsWidget.Interval.parse(null, fallback).value);
  }

  private static StatisticsData statistic(String label, String value) {
    StatisticsData data = new StatisticsData();
    data.setLabel(label);
    data.setValue(value);
    return data;
  }
}