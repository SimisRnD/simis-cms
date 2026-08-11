/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.persistence.cms.ServiceErrorRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SystemHealthCheckRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only view of current and recent system health check status (database, filesystem), backed
 * by the history SystemHealthJob persists every minute. Also offers a manual "Run Check Now" action
 * for an admin who wants a fresh reading without waiting for the next scheduled run (issue #466).
 *
 * @author SimIS
 * @created 7/30/2026
 */
public class HealthDashboardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/health-dashboard.jsp";

  private static final int UPTIME_WINDOW_HOURS = 24;
  private static final int RECENT_ERROR_LIMIT = 50;

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    loadDashboardData(context);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    if ("runCheckNow".equals(command)) {
      for (HealthCommand.CheckResult result : HealthCommand.runAllChecks()) {
        SystemHealthCheck record = new SystemHealthCheck();
        record.setServiceName(result.getServiceName());
        record.setStatus(result.isUp() ? SystemHealthCheck.STATUS_UP : SystemHealthCheck.STATUS_DOWN);
        record.setResponseTimeMs((int) result.getResponseTimeMs());
        record.setErrorMessage(result.getErrorMessage());
        SystemHealthCheckRepository.save(record);
      }
    }

    context.setRedirect("/admin/health-dashboard");
    return context;
  }

  private void loadDashboardData(WidgetContext context) {
    List<SystemHealthCheck> latestChecks = SystemHealthCheckRepository.findLatestPerService();
    context.getRequest().setAttribute("latestChecks", latestChecks);

    Map<String, Double> uptimeByService = new HashMap<>();
    for (SystemHealthCheck check : latestChecks) {
      Double uptime = SystemHealthCheckRepository.findUptimePercent(check.getServiceName(), UPTIME_WINDOW_HOURS);
      uptimeByService.put(check.getServiceName(), uptime);
    }
    context.getRequest().setAttribute("uptimeByService", uptimeByService);
    context.getRequest().setAttribute("uptimeWindowHours", UPTIME_WINDOW_HOURS);

    List<ServiceError> recentErrors = ServiceErrorRepository.findRecent(RECENT_ERROR_LIMIT);
    context.getRequest().setAttribute("recentErrors", recentErrors);
    context.getRequest().setAttribute("recentErrorLimit", RECENT_ERROR_LIMIT);

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
  }
}
