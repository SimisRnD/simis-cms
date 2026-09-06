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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.domain.model.dashboard.BotIdentityStats;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.VisitorRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FunnelEventRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileDownloadRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SearchAnalyticsRepository;
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
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  public static String BOT_IDENTITY_TABLE_JSP = "/admin/site-stats-bot-identity-table.jsp";

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
    Interval interval = configuredInterval(context);
    int intervalValue = interval.value;
    char intervalType = interval.type;
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
      // Which option matches the days/interval actually being rendered, so the JSP can highlight the
      // right tab regardless of the options' list order (the list order doesn't have to put the
      // default value first)
      context.getRequest().setAttribute("currentValue", intervalValue + String.valueOf(intervalType));
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
    if (report == null) {
      // A report preference is required. execute() makes the same check for the rendered path;
      // this one used to be reached only because action() ended by calling execute()
      LOG.error("DEV: A report preference was not specified");
      context.setJson("[]");
      return context;
    }
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));

    // Base the option value on the request (7d, 1y, ...), falling back to the widget's own default
    // when the parameter is absent or malformed -- this is a user-supplied query parameter, and the
    // previous Integer.parseInt on it turned any junk value into a 500 rather than a chart
    Interval interval = Interval.parse(context.getParameter("value"), configuredInterval(context));
    int intervalValue = interval.value;
    char intervalType = interval.type;

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
    // The response is the JSON above and nothing else: WebContainerCommand returns as soon as it
    // sees hasJson(), before any JSP include, so execute()'s request attributes and setJsp() would
    // never be read. Calling it here only ran the report a second time -- with the widget's
    // configured default window rather than the one just asked for -- and threw that result away.
    // Every other widget's action() returns the context directly (see WebVitalsWidget)
    return context;
  }

  /**
   * A report's time window -- how far back, in which unit. Widget preferences and the report
   * drop-down's option values share one syntax for this ("7d", "2w", "6m", "1y"), so both parse
   * through {@link #parse(String, Interval)}.
   */
  static class Interval {
    /** Widget default when no preference and no parameter say otherwise. */
    static final Interval DEFAULT = new Interval(7, 'd');
    /** Matches SessionRepository.resolveRetentionDays' ceiling, so a report cannot outrun retention by orders of magnitude. */
    static final int MAX_VALUE = 3650;

    final int value;
    final char type;

    Interval(int value, char type) {
      this.value = value;
      this.type = type;
    }

    /**
     * Parses "7d"/"2w"/"6m"/"1y"/"12h", or a bare number meaning days (which is what a plain
     * &lt;days&gt; preference has always meant). Anything else -- blank, junk, a negative or
     * absurd count -- yields {@code defaultInterval} rather than an exception, because one of the
     * two callers is parsing a user-supplied query parameter.
     */
    static Interval parse(String text, Interval defaultInterval) {
      if (StringUtils.isBlank(text)) {
        return defaultInterval;
      }
      String trimmed = text.trim();
      char type = 'd';
      String number = trimmed;
      char last = Character.toLowerCase(trimmed.charAt(trimmed.length() - 1));
      if ("hdwmy".indexOf(last) > -1) {
        type = last;
        number = trimmed.substring(0, trimmed.length() - 1);
      }
      int value;
      try {
        value = Integer.parseInt(number);
      } catch (NumberFormatException e) {
        return defaultInterval;
      }
      if (value < 1 || value > MAX_VALUE) {
        return defaultInterval;
      }
      return new Interval(value, type);
    }
  }

  /**
   * The window this widget was configured with. &lt;interval&gt; carries the unit explicitly
   * ("12m"); the older &lt;days&gt; is still honoured and still means days.
   */
  private static Interval configuredInterval(WidgetContext context) {
    String configured = context.getPreferences().get("interval");
    if (StringUtils.isBlank(configured)) {
      configured = context.getPreferences().get("days");
    }
    return Interval.parse(configured, Interval.DEFAULT);
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
    } else if ("mailing-list-classification-breakdown".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = MailingListMemberRepository.findClassificationBreakdown();
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Status"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Subscribers"));
      Timestamp lastClassifiedAt = MailingListMemberRepository.findLastClassifiedAt();
      if (lastClassifiedAt != null) {
        context.getRequest().setAttribute("asOfDate", new SimpleDateFormat("MMM d, yyyy h:mm a").format(lastClassifiedAt));
      }
      return TABLE_JSP;
    } else if ("mailing-list-quality-score".equalsIgnoreCase(report)) {
      double score = MailingListMemberRepository.findQualityScorePercent();
      context.getRequest().setAttribute("numberValue", String.format("%.1f", score));
      return CARD_JSP;
    } else if ("mailing-list-spam-rate-alert".equalsIgnoreCase(report)) {
      // Same underlying metric as mailing-list-quality-score, just its complement -- one query,
      // two presentations, so the two tiles can never drift out of sync with each other.
      double spamRatePercent = 100 - MailingListMemberRepository.findQualityScorePercent();
      int thresholdPercent = MailingListMemberRepository.resolveQuarantineAlertThresholdPercent(
          LoadSitePropertyCommand.loadByName("mailing-list.quarantine.alertThresholdPercent"));
      context.getRequest().setAttribute("numberValue", String.format("%.1f", spamRatePercent));
      context.getRequest().setAttribute("severity", spamRatePercent > thresholdPercent ? "warning" : "ok");
      return ALERT_CARD_JSP;
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
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findDailySessions(intervalValue, intervalType);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("monthly-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findMonthlySessions(intervalValue, intervalType);
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
      List<StatisticsData> statisticsDataList = SessionRepository.findDailySessionsByBotStatus(intervalValue, intervalType, false);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("daily-bot-sessions".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SessionRepository.findDailySessionsByBotStatus(intervalValue, intervalType, true);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("bot-traffic-by-identity".equalsIgnoreCase(report)) {
      List<BotIdentityStats> botIdentityStatsList = SessionRepository.findBotSessionStatsByIdentity(intervalValue);
      context.getRequest().setAttribute("botIdentityStatsList", botIdentityStatsList);
      // Also under the generic name so the shared tab-switcher AJAX path (action(), which always
      // reads "statisticsDataList") can serialize it -- BOT_IDENTITY_TABLE_JSP itself reads the
      // richer "botIdentityStatsList" attribute for the initial server-rendered table.
      context.getRequest().setAttribute("statisticsDataList", botIdentityStatsList);
      return BOT_IDENTITY_TABLE_JSP;
    } else if ("locations-list".equalsIgnoreCase(report)) {
      List<Session> sessionList = SessionRepository.findDailyUniqueLocations(intervalValue);
      context.getRequest().setAttribute("sessionList", sessionList);
      return LOCATIONS_JSP;
    } else if ("locations-map".equalsIgnoreCase(report)) {
      List<Session> sessionList = SessionRepository.findDailyUniqueLocations(intervalValue);
      // Session.latitude/longitude are primitive doubles, defaulting to 0/0 when never resolved.
      // findDailyUniqueLocations() already excludes anonymous sessions, but an authenticated
      // session can still reach here with unset coordinates -- MaxMind's city/subdivision data
      // can resolve without its separate location sub-record (GeoIPCommand.getLocation()).
      // Plotting those at literal (0,0) puts them in the Gulf of Guinea ("Null Island") instead
      // of just being absent from the map.
      List<Session> plottableSessionList = new ArrayList<>();
      for (Session session : sessionList) {
        if (session.getLatitude() != 0 || session.getLongitude() != 0) {
          plottableSessionList.add(session);
        }
      }
      context.getRequest().setAttribute("sessionList", plottableSessionList);
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
    } else if ("pages-per-session".equalsIgnoreCase(report)) {
      double avgPagesPerSession = WebPageHitRepository.findAvgPagesPerSession(intervalValue);
      context.getRequest().setAttribute("numberValue", String.format("%.1f", avgPagesPerSession));
      return CARD_JSP;
    } else if ("return-visitor-rate".equalsIgnoreCase(report)) {
      double returnVisitorRatePercent = VisitorRepository.findReturnVisitorRatePercent(intervalValue);
      context.getRequest().setAttribute("numberValue", String.format("%.1f", returnVisitorRatePercent));
      return CARD_JSP;
    } else if ("avg-time-on-page".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findAvgTimeOnPageByPath(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Avg Time"));
      return TABLE_JSP;
    } else if ("high-traffic-low-engagement".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findHighTrafficLowEngagementPages(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits / Avg Time"));
      return TABLE_JSP;
    } else if ("low-traffic-high-engagement".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findLowTrafficHighEngagementPages(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits / Avg Time"));
      return TABLE_JSP;
    } else if ("file-downloads".equalsIgnoreCase(report)) {
      // Every tab reads the same dated rows. An "all time" tab backed by files.download_count was
      // tempting -- the counter predates this log, so it would have had history on day one -- but
      // it counts downloads the log does not, and a reader comparing that tab against a windowed
      // one would find numbers that cannot be reconciled and nothing on screen explaining why. One
      // source, consistent meaning; the cumulative counter is still shown in the folder listings.
      List<StatisticsData> statisticsDataList =
          FileDownloadRepository.findTopDownloads(intervalValue, intervalType, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "File"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Downloads"));
      return TABLE_JSP;
    } else if ("web-urls".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findTopPaths(intervalValue, intervalType, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Link"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Hits"));
      return TABLE_JSP;
    } else if ("solution-type-traffic".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findTrafficBySolutionType(intervalValue);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      return JSP;
    } else if ("solution-type-engagement".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebPageHitRepository.findEngagementBySolutionType(intervalValue);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Solution Type"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Avg Page Views / Session"));
      return TABLE_JSP;
    } else if ("search-terms".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = WebSearchRepository.findTopSearchTerms(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches"));
      return TABLE_JSP;
    } else if ("zero-result-terms".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findZeroResultTerms(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Zero-Result Searches"));
      return TABLE_JSP;
    } else if ("zero-result-search-alert".equalsIgnoreCase(report)) {
      long count = SearchAnalyticsRepository.countZeroResultSearches(1);
      int threshold = SearchAnalyticsRepository.resolveZeroResultAlertThreshold(
          LoadSitePropertyCommand.loadByName("search.zeroResultAlertThreshold"));
      context.getRequest().setAttribute("numberValue", String.valueOf(count));
      context.getRequest().setAttribute("severity", count > threshold ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("trending-search-terms".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findTrendingTerms(limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches This Week"));
      return TABLE_JSP;
    } else if ("facet-adoption-rate".equalsIgnoreCase(report)) {
      // issue #638: what fraction of searches were narrowed by a facet/filter. Only ItemsSearchResultsWidget
      // sets facet_key today -- the other five search-results widgets have no facet concept yet, so this
      // is necessarily a rate over all searches, not just faceted-capable ones.
      long totalSearches = SearchAnalyticsRepository.countSearches(intervalValue);
      long facetedSearches = SearchAnalyticsRepository.countSearchesWithFacetApplied(intervalValue);
      double adoptionRate = totalSearches == 0 ? 0.0 : (100.0 * facetedSearches / totalSearches);
      context.getRequest().setAttribute("numberValue", String.valueOf(Math.round(adoptionRate * 10) / 10.0));
      context.getRequest().setAttribute("severity", "ok");
      return ALERT_CARD_JSP;
    } else if ("facet-usage-breakdown".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findFacetUsageBreakdown(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Facet"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches"));
      return TABLE_JSP;
    } else if ("search-volume-by-type".equalsIgnoreCase(report)) {
      // issue #1014: search_type is populated by every widget today and wasn't read back by any
      // report -- which of the six search surfaces (pages/content/blog/wiki/items/calendar) gets
      // used at all, not just which terms are searched.
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findSearchVolumeByType(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Content Type"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches"));
      return TABLE_JSP;
    } else if ("zero-result-rate-by-type".equalsIgnoreCase(report)) {
      // issue #1014: which search surface fails visitors most often, as a rate rather than a raw
      // zero-result count, so a low-volume surface with a bad hit rate isn't hidden by a high-volume
      // surface's larger raw count.
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findZeroResultRateByType(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Content Type"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Zero-Result Rate %"));
      return TABLE_JSP;
    } else if ("top-search-paths".equalsIgnoreCase(report)) {
      // issue #1014: page_path is populated by every search widget today and wasn't read back by
      // any report -- which pages visitors are searching from, a candidate list for on-page
      // navigation/content review.
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findTopSearchPaths(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Searches"));
      return TABLE_JSP;
    } else if ("top-zero-result-search-paths".equalsIgnoreCase(report)) {
      // issue #1014: narrower than top-search-paths -- which pages send visitors into a search that
      // comes up empty, the sharpest candidate list for a content gap or on-page navigation fix.
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findTopZeroResultPaths(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Page"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Zero-Result Searches"));
      return TABLE_JSP;
    } else if ("near-miss-search-terms".equalsIgnoreCase(report)) {
      // issue #1014: terms that technically found something (1-3 results) but few enough that
      // recall is suspect -- worth a look even though they wouldn't show up on the hard-failure
      // zero-result-terms report. See SearchAnalyticsRepository.findNearMissTerms's javadoc for the
      // fixed threshold.
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findNearMissTerms(intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Low-Result Searches"));
      return TABLE_JSP;
    } else if ("high-value-search-terms".equalsIgnoreCase(report)) {
      // Admin-curated watchlist (search.highValueTerms) of business-critical terms -- unlike
      // zero-result-terms/near-miss-search-terms (which surface terms that are failing), this
      // confirms these specific terms ARE being found and tracks their search volume.
      List<String> highValueTerms = SearchAnalyticsRepository.parseHighValueTerms(
          LoadSitePropertyCommand.loadByName("search.highValueTerms"));
      List<StatisticsData> statisticsDataList = SearchAnalyticsRepository.findHighValueTermActivity(highValueTerms, intervalValue, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Search Term"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Successful Searches"));
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
    } else if ("contact-form-funnel".equalsIgnoreCase(report)) {
      // Issue #565 phase 1: view -> submitted -> processed, with per-stage counts and drop-off
      // between consecutive stages. Stage order is a property of this funnel definition, not the
      // funnel_events table, so it's fixed here rather than driven by a stored column.
      Timestamp startDate = new Timestamp(System.currentTimeMillis() - (long) intervalValue * 24 * 60 * 60 * 1000);
      Timestamp endDate = new Timestamp(System.currentTimeMillis());
      Map<String, Long> stageCounts = FunnelEventRepository.countStagesInRange(
          FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY, startDate, endDate);
      long viewed = stageCounts.getOrDefault(FunnelEventCommand.STAGE_VIEW, 0L);
      long submitted = stageCounts.getOrDefault(FunnelEventCommand.STAGE_SUBMITTED, 0L);
      long processed = stageCounts.getOrDefault(FunnelEventCommand.STAGE_PROCESSED, 0L);

      List<StatisticsData> statisticsDataList = new ArrayList<>();
      StatisticsData viewedData = new StatisticsData();
      viewedData.setLabel("Page Views");
      viewedData.setValue(String.valueOf(viewed));
      statisticsDataList.add(viewedData);

      StatisticsData submittedData = new StatisticsData();
      submittedData.setLabel(withDropOffPercent("Form Submitted", submitted, viewed, "of views"));
      submittedData.setValue(String.valueOf(submitted));
      statisticsDataList.add(submittedData);

      StatisticsData processedData = new StatisticsData();
      processedData.setLabel(withDropOffPercent("Processed", processed, submitted, "of submitted"));
      processedData.setValue(String.valueOf(processed));
      statisticsDataList.add(processedData);

      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Stage"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Count"));
      return TABLE_JSP;
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
    } else if ("expiring-soon".equalsIgnoreCase(report)) {
      long count = WebPageRepository.countExpiringSoon();
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
    } else if ("request-rate-spike-alert".equalsIgnoreCase(report)) {
      // Issue #569 slice 1: the admin alert-delivery mechanism, demonstrated with one concrete
      // traffic-quality signal (peak hits from a single non-bot IP in the last hour) reusing the
      // existing alert-card pattern, rather than speculative infrastructure with nothing real to
      // alert on. Geographic/referrer-abuse/behavioral/VPN detection are deliberately deferred --
      // see the issue.
      long peakHitsPerIp = WebPageHitRepository.findMaxHitsFromSingleIp(1);
      int threshold = WebPageHitRepository.resolveIpRequestRateAlertThreshold(
          LoadSitePropertyCommand.loadByName("security.ipRequestRateAlertThreshold"));
      context.getRequest().setAttribute("numberValue", String.valueOf(peakHitsPerIp));
      context.getRequest().setAttribute("severity", peakHitsPerIp > threshold ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("geo-anomaly-alert".equalsIgnoreCase(report)) {
      // Issue #569 slice 2: a geographic-anomaly signal -- a country appearing in the top 5 by
      // session count during a short recent window that was NOT in the top 5 during a longer
      // baseline window immediately preceding it. The baseline is non-overlapping with the recent
      // window on purpose: if it were the same "last N days including today" window, a real recent
      // spike would already be counted in its own baseline and could never look anomalous. Windows
      // are configurable via security.geoAnomalyBaselineDays/security.geoAnomalyRecentHours.
      int baselineDays = SessionRepository.resolveGeoAnomalyBaselineDays(
          LoadSitePropertyCommand.loadByName("security.geoAnomalyBaselineDays"));
      int recentHours = SessionRepository.resolveGeoAnomalyRecentHours(
          LoadSitePropertyCommand.loadByName("security.geoAnomalyRecentHours"));
      java.time.Instant nowInstant = java.time.Instant.now();
      Timestamp recentStart = Timestamp.from(nowInstant.minus(Duration.ofHours(recentHours)));
      Timestamp baselineStart = Timestamp.from(
          nowInstant.minus(Duration.ofHours(recentHours)).minus(Duration.ofDays(baselineDays)));
      List<StatisticsData> recentTopCountries = SessionRepository.findTopCountriesByCount(recentStart, Timestamp.from(nowInstant), 5);
      List<StatisticsData> baselineTopCountries = SessionRepository.findTopCountriesByCount(baselineStart, recentStart, 5);
      Set<String> baselineCountryNames = new HashSet<>();
      for (StatisticsData data : baselineTopCountries) {
        baselineCountryNames.add(data.getLabel());
      }
      long newCountryCount = recentTopCountries.stream()
          .filter(data -> !baselineCountryNames.contains(data.getLabel()))
          .count();
      context.getRequest().setAttribute("numberValue", String.valueOf(newCountryCount));
      context.getRequest().setAttribute("severity", newCountryCount > 0 ? "warning" : "ok");
      return ALERT_CARD_JSP;
    } else if ("recent-admin-actions".equalsIgnoreCase(report)) {
      context.getRequest().setAttribute("recentActionsList", findRecentAdminActions(5));
      return RECENT_ACTIONS_JSP;
    } else {
      return null;
    }
  }

  /**
   * "Form Submitted (40.0% of views)" -- omits the percentage entirely rather than showing 0.0% or
   * dividing by zero when the previous stage has no events yet (e.g. funnel tracking was just turned on).
   */
  static String withDropOffPercent(String stageName, long stageCount, long previousStageCount, String ofWhat) {
    if (previousStageCount <= 0) {
      return stageName;
    }
    double percent = (stageCount * 100.0) / previousStageCount;
    return String.format(java.util.Locale.US, "%s (%.1f%% %s)", stageName, percent, ofWhat);
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
   * The dashboard's "Recent Admin Activity" tile. Issue #1006 generalized this: it used to run one small
   * query per category (content/configuration/user_management only) and merge in Java, because
   * AuditLogSpecification filtered on a single event_category and widening that shared where-clause for
   * one dashboard tile wasn't worth it. AuditLogRepository#findRecentActivity now does a real
   * {@code event_category IN (...)} query, so this tile gets all 6 categories (including the
   * security-relevant authentication/authorization/data_access it previously omitted entirely) and a real
   * trailing time window in one query, rather than "however far back this admin's most recent 5 actions
   * happen to reach" -- the same window the /admin/activity feed defaults to, so the two surfaces describe
   * "recent" the same way.
   */
  static List<AuditLog> findRecentAdminActions(int limit) {
    Timestamp since = Timestamp.from(
        java.time.Instant.now().minus(AuditLogRepository.DEFAULT_TRAILING_WINDOW_DAYS, java.time.temporal.ChronoUnit.DAYS));
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(limit);
    constraints.setUseCount(false);
    List<AuditLog> recentActivity = AuditLogRepository.findRecentActivity(null, since, null, constraints);
    return recentActivity != null ? recentActivity : new ArrayList<>();
  }
}
