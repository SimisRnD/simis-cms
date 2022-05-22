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

import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageHitRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserLoginRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.lang3.StringUtils;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Dataset"));
    context.getRequest().setAttribute("label1", context.getPreferences().get("label1"));

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
        Jsonb jsonb = JsonbBuilder.create();
        json = jsonb.toJson(statisticsDataList);
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
    } else {
      return null;
    }
  }
}
