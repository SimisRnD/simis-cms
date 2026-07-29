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

import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebSearchRepository;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.simisinc.platform.presentation.widgets.dashboard.StatisticCardWidget.valueForColor;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/7/18 2:10 PM
 */
public class SiteStatsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public static String LINE_CHART_JSP = "/admin/site-stats-line-chart.jsp";
  public static String BAR_CHART_JSP = "/admin/site-stats-bar-chart.jsp";
  public static String TABLE_JSP = "/admin/site-stats-table.jsp";
  public static String CARD_JSP = "/admin/site-stats-card.jsp";
  public static String LOCATIONS_JSP = "/admin/site-stats-locations-table.jsp";
  public static String LOCATIONS_MAP_JSP = "/admin/site-stats-locations-map.jsp";
  public static String ALERT_CARD_JSP = "/admin/site-stats-alert-card.jsp";
  public static String RECENT_ACTIONS_JSP = "/admin/site-stats-recent-actions.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the report to run
    String report = context.getPreferences().get("report");
    if (report == null) {
      // A report preference is required
      LOG.error("DEV: A report preference was not specified");
      return null;
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Different kinds of stats and preferences...
    String outputType = context.getPreferences().get("type");
    int intervalValue = Integer.parseInt(context.getPreferences().getOrDefault("days", "7"));
    char intervalType = 'd';
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));

    // Determine the Chart preference (reports can override)
    String JSP = LINE_CHART_JSP;
    if ("bar".equals(outputType)) {
      JSP = BAR_CHART_JSP;
    }
    context.getRequest().setAttribute("label", context.getPreferences().get("label"));
    context.getRequest().setAttribute("label1", context.getPreferences().get("label1"));
    context.getRequest().setAttribute("link", context.getPreferences().get("link"));
    context.getRequest().setAttribute("iconColor", valueForColor(context.getPreferences().getOrDefault("iconColor", null)));

    // Check for report drop-down menu options
    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("options");
    if (!entriesList.isEmpty()) {
      Map<String, String> optionsList = new LinkedHashMap<>();
      for (Map<String, String> valueMap : entriesList) {
        String name = valueMap.get("name");
        String value = valueMap.get("value");
        optionsList.put(name, value);
      }
      context.getRequest().setAttribute("optionsList", optionsList);
    }

    // Run the report
    JSP = runReport(context, report, JSP, intervalValue, intervalType, limit);
    if (JSP == null) {
      return context;
    }
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    LOG.debug("Got widget JSON action... " + context.getUniqueId());

    // Use the preferences
    String report = context.getPreferences().get("report");
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    int intervalValue = Integer.parseInt(context.getPreferences().getOrDefault("days", "7"));
    char intervalType = 'd';

    // Base the option value on the request
    String value = context.getParameter("value");
    if (StringUtils.isNotBlank(value)) {
      // 7d,1y
      intervalValue = Integer.parseInt(value.substring(0, value.length() - 1));
      intervalType = value.charAt(value.length() - 1);
    }

    // Output JSON
    String json = "[]";
    String success = runReport(context, report, "json", intervalValue, intervalType, limit);
    if (success != null) {
      List<StatisticsData> statisticsDataList = (List) context.getRequest().getAttribute("statisticsDataList");
      if (statisticsDataList != null) {
        try (Jsonb jsonb = JsonbBuilder.create()) {
          json = jsonb.toJson(statisticsDataList);
        } catch (Exception e) {
          LOG.error(e);
          return null;
        }
      }
    }
    context.setJson(json);
    return execute(context);
  }

  private String runReport(WidgetContext context, String report, String JSP, int intervalValue, char intervalType, int limit) {
    // Run the report
    if ("dau".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = UserLoginRepository.findUniqueDailyLogins(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("mau".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = UserLoginRepository.findUniqueMonthlyLogins(12);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("monthly-user-registrations".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = UserRepository.findMonthlyUserRegistrations(12);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("daily-user-registrations".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = UserRepository.findDailyUserRegistrations(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("total-users".equalsIgnoreCase(report)) {
      Long totalUserCount = UserRepository.countTotalUsers();
      context.getRequest().setAttribute("numberValue", String.valueOf(totalUserCount));
      return CARD_JSP;
    } else if ("active-mailing-list-subscribers".equalsIgnoreCase(report)) {
      long count = MailingListMemberRepository.countActiveSubscribers();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("unsubscribed-mailing-list-members".equalsIgnoreCase(report)) {
      long count = MailingListMemberRepository.countUnsubscribed();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("monthly-mailing-list-subscriptions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = MailingListMemberRepository.findMonthlySubscriptions(12);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("daily-mailing-list-subscriptions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = MailingListMemberRepository.findDailySubscriptions(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("enabled-accounts".equalsIgnoreCase(report)) {
      long count = UserRepository.countEnabledAccounts();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("validated-accounts".equalsIgnoreCase(report)) {
      long count = UserRepository.countValidatedAccounts();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("new-registrations-this-month".equalsIgnoreCase(report)) {
      long count = UserRepository.countNewRegistrationsThisMonth();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("admin-staff-accounts".equalsIgnoreCase(report)) {
      long count = UserRepository.countAccountsWithAnyRole();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("public-accounts".equalsIgnoreCase(report)) {
      long count = UserRepository.countPublicAccounts();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("daily-hits".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findDailyWebHits(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("daily-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findDailySessions(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("monthly-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findMonthlySessions(12);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("total-sessions-today".equalsIgnoreCase(report)) {
      Long count = SessionRepository.countSessionsToday();
      if (count == -1) {
        return null;
      }
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("total-sessions-now".equalsIgnoreCase(report)) {
      Long count = SessionRepository.countOnlineNow();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("real-sessions-today".equalsIgnoreCase(report)) {
      long count = SessionRepository.countDistinctSessions(startOfToday(), now());
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("bot-sessions-today-total".equalsIgnoreCase(report)) {
      long count = SessionRepository.countBotSessions(startOfToday(), now());
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("bot-traffic-percentage".equalsIgnoreCase(report)) {
      Timestamp start = startOfToday();
      Timestamp end = now();
      long real = SessionRepository.countDistinctSessions(start, end);
      long bot = SessionRepository.countBotSessions(start, end);
      long total = real + bot;
      long percentage = total == 0 ? 0 : Math.round((bot * 100.0) / total);
      context.getRequest().setAttribute("numberValue", String.valueOf(percentage));
      return CARD_JSP;
    } else if ("daily-real-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SessionRepository.findDailySessionsByBotStatus(30, false);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("daily-bot-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SessionRepository.findDailySessionsByBotStatus(30, true);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("locations-list".equalsIgnoreCase(report)) {
      List<Session> sessionList = SessionRepository.findDailyUniqueLocations(intervalValue);
      context.getRequest().setAttribute("sessionList", sessionList);
      return LOCATIONS_JSP;
    } else if ("locations-map".equalsIgnoreCase(report)) {
      List<Session> sessionList = SessionRepository.findDailyUniqueLocations(intervalValue);
      context.getRequest().setAttribute("sessionList", sessionList);
      // Determine the mapping service
      MapCredentials mapCredentials = FindMapTilesCredentialsCommand.getCredentials();
      if (mapCredentials == null) {
        LOG.debug("Skipping - map service not defined");
        return null;
      }
      context.getRequest().setAttribute("mapCredentials", mapCredentials);
      // Determine optional map and marker info
      String mapHeight = context.getPreferences().getOrDefault("mapHeight", "290");
      context.getRequest().setAttribute("mapHeight", mapHeight);
      return LOCATIONS_MAP_JSP;
    } else if ("referrals".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SessionRepository.findTopReferrals(intervalValue, intervalType, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "From"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits"));
      return TABLE_JSP;
    } else if ("web-pages".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findTopWebPages(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits"));
      return TABLE_JSP;
    } else if ("web-urls".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findTopPaths(intervalValue, intervalType, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Link"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits"));
      return TABLE_JSP;
    } else if ("search-terms".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebSearchRepository.findTopSearchTerms(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches"));
      return TABLE_JSP;
    } else if ("total-form-submissions".equalsIgnoreCase(report)) {
      Long count = FormDataRepository.countTotalSubmissions();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("form-submissions-spam-flagged".equalsIgnoreCase(report)) {
      Timestamp startDate = new Timestamp(System.currentTimeMillis() - (long) intervalValue * 24 * 60 * 60 * 1000);
      Timestamp endDate = new Timestamp(System.currentTimeMillis());
      Long count = FormDataRepository.countSpamFlagged(startDate, endDate);
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      return CARD_JSP;
    } else if ("daily-form-submissions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = FormDataRepository.findDailySubmissions(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("monthly-form-submissions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = FormDataRepository.findMonthlySubmissions(12);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("top-forms".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = FormDataRepository.findSubmissionCountsByForm(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Form"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Submissions"));
      return TABLE_JSP;
    } else if ("form-failures-by-reason".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = FormSubmissionFailureRepository.findFailureCountsByReason(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Reason"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Rejections"));
      return TABLE_JSP;
    } else if ("conversion-rate".equalsIgnoreCase(report)) {
      // Admin-configured pairing -- there's no reliable automatic link between a page and the form(s)
      // embedded on it, so the widget instance must say which page path and which formUniqueId to compare.
      String pagePath = context.getPreferences().get("pagePath");
      String formUniqueId = context.getPreferences().get("formUniqueId");
      if (StringUtils.isBlank(pagePath) || StringUtils.isBlank(formUniqueId)) {
        LOG.warn("DEV: conversion-rate report requires pagePath and formUniqueId preferences");
        return null;
      }
      Timestamp startDate = new Timestamp(System.currentTimeMillis() - (long) intervalValue * 24 * 60 * 60 * 1000);
      Timestamp endDate = new Timestamp(System.currentTimeMillis());
      long pageViews = WebPageHitRepository.countPageViews(pagePath, startDate, endDate);
      long submissions = FormDataRepository.countSubmissions(formUniqueId, startDate, endDate);
      double rate = pageViews > 0 ? (submissions * 100.0 / pageViews) : 0;
      // Plain numeric value -- CARD_JSP feeds this straight into <fmt:formatNumber>, which requires a
      // parseable number (no "%" suffix, no "N/A" fallback); the "%" is conveyed via the tile's label instead.
      context.getRequest().setAttribute("numberValue", String.format("%.1f", rate));
      return CARD_JSP;
    } else if ("failed-logins-24h".equalsIgnoreCase(report)) {
      AuditLogSpecification spec = new AuditLogSpecification();
      spec.setEventType("authentication.login.failure");
      spec.setOccurredAfter(last24Hours());
      DataConstraints countOnly = new DataConstraints();
      countOnly.setPageSize(1);
      AuditLogRepository.findAll(spec, countOnly);
      long count = countOnly.getTotalRecordCount();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > 0 ? "critical" : "ok");
      return ALERT_CARD_JSP;
    } else if ("locked-accounts".equalsIgnoreCase(report)) {
      long count = UserRepository.countLockedAccounts();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > 0 ? "critical" : "ok");
      return ALERT_CARD_JSP;
    } else if ("drafts-awaiting-review".equalsIgnoreCase(report)) {
      long count = ContentRepository.countByDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > 0 ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("scheduled-not-live".equalsIgnoreCase(report)) {
      long count = WebPageRepository.countScheduledNotYetLive();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > 0 ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("submissions-awaiting-review".equalsIgnoreCase(report)) {
      long count = FormDataRepository.countAwaitingReview();
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > 0 ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("bot-sessions-today".equalsIgnoreCase(report)) {
      long count = SessionRepository.countDistinctBotSessions(startOfToday(), now());
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", "ok");
      return ALERT_CARD_JSP;
    } else if ("recent-admin-actions".equalsIgnoreCase(report)) {
      context.getRequest().setAttribute("recentActionsList", findRecentAdminActions(5));
      return RECENT_ACTIONS_JSP;
    } else {
      return null;
    }
  }

  private static Timestamp startOfToday() {
    return Timestamp.valueOf(java.time.LocalDate.now().atStartOfDay());
  }

  private static Timestamp now() {
    return new Timestamp(System.currentTimeMillis());
  }

  private static Timestamp last24Hours() {
    return Timestamp.from(java.time.Instant.now().minus(Duration.ofHours(24)));
  }

  /**
   * Merges the most recent audit events across the categories an admin dashboard cares about.
   * AuditLogSpecification filters on a single event_category, not a list, so this runs one small
   * query per category and merges in Java rather than widening the shared specification/where-clause
   * for a single dashboard tile.
   */
  static List<AuditLog> findRecentAdminActions(int limit) {
    List<AuditLog> merged = new ArrayList<>();
    for (String category : new String[] { "content", "configuration", "user_management" }) {
      AuditLogSpecification spec = new AuditLogSpecification();
      spec.setEventCategory(category);
      DataConstraints constraints = new DataConstraints();
      constraints.setPageSize(limit);
      constraints.setUseCount(false);
      List<AuditLog> categoryRecords = AuditLogRepository.findAll(spec, constraints);
      if (categoryRecords != null) {
        merged.addAll(categoryRecords);
      }
    }
    merged.sort(Comparator.comparing(AuditLog::getOccurred).reversed());
    return merged.subList(0, Math.min(limit, merged.size()));
  }
}
