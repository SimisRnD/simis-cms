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

import java.util.Set;

import com.simisinc.platform.infrastructure.persistence.DatabaseMaintenanceRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Admin-only database maintenance dashboard (issue #469): database/table/index size and activity
 * introspection via PostgreSQL's own stats catalogs, plus a VACUUM (ANALYZE) trigger per table.
 *
 * <p>
 * Deliberately scoped to what's low-risk and needs no extra Postgres extension -- REINDEX,
 * query-plan/slow-query analysis, bloat estimation, per-table autovacuum tuning, and maintenance
 * scheduling are left for a follow-up (see the issue for the full wishlist).
 * </p>
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class DatabaseMaintenanceWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/database-maintenance.jsp";

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
    if ("vacuumAnalyze".equals(command)) {
      String tableName = context.getParameter("table");
      Set<String> knownTables = DatabaseMaintenanceRepository.findTableNames();
      if (tableName == null || !knownTables.contains(tableName)) {
        context.setErrorMessage("Unknown table");
      } else if (DatabaseMaintenanceRepository.vacuumAnalyzeTable(tableName)) {
        context.setSuccessMessage("VACUUM (ANALYZE) completed for " + tableName);
      } else {
        context.setErrorMessage("VACUUM (ANALYZE) failed for " + tableName + " -- see the server log");
      }
    }

    context.setRedirect("/admin/database-maintenance");
    return context;
  }

  private void loadDashboardData(WidgetContext context) {
    context.getRequest().setAttribute("overview", DatabaseMaintenanceRepository.findOverview());
    context.getRequest().setAttribute("tableStatsList", DatabaseMaintenanceRepository.findTableStats());
    context.getRequest().setAttribute("indexStatsList", DatabaseMaintenanceRepository.findIndexStats());
    context.getRequest().setAttribute("activeQueryList", DatabaseMaintenanceRepository.findActiveQueries());

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
  }
}
