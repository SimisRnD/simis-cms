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

package com.simisinc.platform.presentation.widgets.admin.ecommerce;

import com.simisinc.platform.application.gis.GISCommand;
import com.simisinc.platform.application.maps.FindMapTilesCredentialsCommand;
import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.StatisticsData;
import com.simisinc.platform.domain.model.maps.MapCredentials;
import com.simisinc.platform.infrastructure.persistence.ecommerce.OrderRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

import java.util.List;

import static com.simisinc.platform.presentation.widgets.admin.SiteStatsWidget.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/11/2020 4:01 PM
 */
public class EcommerceStatsWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Different kinds of stats and preferences...
    int days = Integer.parseInt(context.getPreferences().getOrDefault("days", "7"));
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "10"));
    String type = context.getPreferences().get("type");
    String JSP = LINE_CHART_JSP;
    if ("bar".equals(type)) {
      JSP = BAR_CHART_JSP;
    }

    String report = context.getPreferences().get("report");
    if (report == null) {
      LOG.error("DEV: A report preference was not specified");
      return null;
    }

    context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Dataset"));
    context.getRequest().setAttribute("label1", context.getPreferences().get("label1"));

    // Reports
    if ("daily-items-sold".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = OrderRepository.findDailyItemsSold(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.setJsp(JSP);
    } else if ("daily-amount-sold".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = OrderRepository.findDailyAmountSold(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.setJsp(JSP);
    } else if ("daily-orders".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = OrderRepository.findDailyOrdersCount(30);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.setJsp(JSP);
    } else if ("total-orders".equalsIgnoreCase(report)) {
      Long totalOrderCount = OrderRepository.countTotalOrders();
      context.getRequest().setAttribute("numberValue", String.valueOf(totalOrderCount));
      context.setJsp(CARD_JSP);
    } else if ("total-orders-today".equalsIgnoreCase(report)) {
      Long totalOrderCount = OrderRepository.countTotalOrders(1);
      context.getRequest().setAttribute("numberValue", String.valueOf(totalOrderCount));
      context.setJsp(CARD_JSP);
    } else if ("total-not-shipped".equalsIgnoreCase(report)) {
      Long totalOrderCount = OrderRepository.countTotalOrdersNotShipped();
      context.getRequest().setAttribute("numberValue", String.valueOf(totalOrderCount));
      context.setJsp(CARD_JSP);
    } else if ("total-shipped".equalsIgnoreCase(report)) {
      Long totalOrderCount = OrderRepository.countTotalOrdersShipped();
      context.getRequest().setAttribute("numberValue", String.valueOf(totalOrderCount));
      context.setJsp(CARD_JSP);
    } else if ("locations-list".equalsIgnoreCase(report)) {
      List<Session> sessionList = OrderRepository.findDailyUniqueLocations(days);
      context.getRequest().setAttribute("sessionList", sessionList);
      context.getRequest().setAttribute("showContinent", "false");
      context.setJsp(LOCATIONS_JSP);
    } else if ("locations-map".equalsIgnoreCase(report)) {
      List<Session> sessionList = OrderRepository.findDailyUniqueLocations(days);
      context.getRequest().setAttribute("sessionList", sessionList);
      // Determine the center point
      Session centerPoint = GISCommand.center(sessionList);
      context.getRequest().setAttribute("centerPoint", centerPoint);
      // Determine the mapping service
      MapCredentials mapCredentials = FindMapTilesCredentialsCommand.getCredentials();
      if (mapCredentials == null) {
        LOG.debug("Skipping - map service not defined");
        return context;
      }
      context.getRequest().setAttribute("mapCredentials", mapCredentials);
      // Determine optional map and marker info
      String mapHeight = context.getPreferences().getOrDefault("mapHeight", "290");
      context.getRequest().setAttribute("mapHeight", mapHeight);
      context.setJsp(LOCATIONS_MAP_JSP);
    } else if ("top-locations".equalsIgnoreCase(report)) {
      List<StatisticsData> statisticsDataList = OrderRepository.findTopLocations(days, limit);
      context.getRequest().setAttribute("statisticsDataList", statisticsDataList);
      context.getRequest().setAttribute("label", context.getPreferences().getOrDefault("label", "Location"));
      context.getRequest().setAttribute("value", context.getPreferences().getOrDefault("value", "Orders"));
      context.setJsp(TABLE_JSP);
    } else {
      return null;
    }
    return context;
  }
}
