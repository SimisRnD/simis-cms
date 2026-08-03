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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.persistence.DatabaseMaintenanceRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class DatabaseMaintenanceWidgetTest extends WidgetBase {

  @Test
  void executeLoadsAllFourSectionsForAnAdmin() {
    setRoles(widgetContext, ADMIN);
    DatabaseMaintenanceRepository.DatabaseOverview overview =
        new DatabaseMaintenanceRepository.DatabaseOverview(1024L, "1024 bytes", 5, 8);
    List<DatabaseMaintenanceRepository.TableStats> tableStatsList = List.of();
    List<DatabaseMaintenanceRepository.IndexStats> indexStatsList = List.of();
    List<DatabaseMaintenanceRepository.ActiveQuery> activeQueryList = List.of();

    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      repository.when(DatabaseMaintenanceRepository::findOverview).thenReturn(overview);
      repository.when(DatabaseMaintenanceRepository::findTableStats).thenReturn(tableStatsList);
      repository.when(DatabaseMaintenanceRepository::findIndexStats).thenReturn(indexStatsList);
      repository.when(DatabaseMaintenanceRepository::findActiveQueries).thenReturn(activeQueryList);

      WidgetContext result = new DatabaseMaintenanceWidget().execute(widgetContext);

      assertEquals("/admin/database-maintenance.jsp", result.getJsp());
      assertEquals(overview, result.getRequest().getAttribute("overview"));
      assertEquals(tableStatsList, result.getRequest().getAttribute("tableStatsList"));
      assertEquals(indexStatsList, result.getRequest().getAttribute("indexStatsList"));
      assertEquals(activeQueryList, result.getRequest().getAttribute("activeQueryList"));
    }
  }

  @Test
  void executeDoesNothingForAUserWithoutAdmin() {
    // WidgetBase's default logged-in test user has no roles at all
    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      WidgetContext result = new DatabaseMaintenanceWidget().execute(widgetContext);

      assertNull(result.getJsp());
      repository.verifyNoInteractions();
    }
  }

  @Test
  void postVacuumAnalyzeRunsForAKnownTableAndRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "vacuumAnalyze");
    addQueryParameter(widgetContext, "table", "web_pages");

    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      repository.when(DatabaseMaintenanceRepository::findTableNames).thenReturn(Set.of("web_pages", "users"));
      repository.when(() -> DatabaseMaintenanceRepository.vacuumAnalyzeTable("web_pages")).thenReturn(true);

      WidgetContext result = new DatabaseMaintenanceWidget().post(widgetContext);

      repository.verify(() -> DatabaseMaintenanceRepository.vacuumAnalyzeTable("web_pages"));
      assertEquals("/admin/database-maintenance", result.getRedirect());
      assertEquals("VACUUM (ANALYZE) completed for web_pages", result.getSuccessMessage());
    }
  }

  @Test
  void postVacuumAnalyzeRejectsATableNameNotInTheLiveAllowlist() {
    // Guards the VACUUM target: it's string-interpolated into raw SQL (VACUUM can't take a bind
    // parameter for its target), so a table name must be verified against a live catalog query
    // before it's ever handed to vacuumAnalyzeTable -- this is that guard, exercised directly.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "vacuumAnalyze");
    addQueryParameter(widgetContext, "table", "web_pages; DROP TABLE users;--");

    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      repository.when(DatabaseMaintenanceRepository::findTableNames).thenReturn(Set.of("web_pages", "users"));

      WidgetContext result = new DatabaseMaintenanceWidget().post(widgetContext);

      repository.verify(() -> DatabaseMaintenanceRepository.vacuumAnalyzeTable(anyString()), never());
      assertEquals("Unknown table", result.getErrorMessage());
      assertEquals("/admin/database-maintenance", result.getRedirect());
    }
  }

  @Test
  void postDoesNothingForAUserWithoutAdmin() {
    addQueryParameter(widgetContext, "command", "vacuumAnalyze");
    addQueryParameter(widgetContext, "table", "web_pages");

    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      WidgetContext result = new DatabaseMaintenanceWidget().post(widgetContext);

      assertNull(result.getRedirect());
      repository.verifyNoInteractions();
    }
  }

  @Test
  void postIgnoresAnUnrecognizedCommandButStillRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "somethingElse");

    try (MockedStatic<DatabaseMaintenanceRepository> repository = mockStatic(DatabaseMaintenanceRepository.class)) {
      WidgetContext result = new DatabaseMaintenanceWidget().post(widgetContext);

      repository.verify(() -> DatabaseMaintenanceRepository.vacuumAnalyzeTable(anyString()), never());
      assertEquals("/admin/database-maintenance", result.getRedirect());
    }
  }
}
