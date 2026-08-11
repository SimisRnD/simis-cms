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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.HealthCommand;
import com.simisinc.platform.domain.model.cms.ServiceError;
import com.simisinc.platform.domain.model.cms.SystemHealthCheck;
import com.simisinc.platform.infrastructure.persistence.cms.ServiceErrorRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SystemHealthCheckRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author SimIS
 * @created 7/30/2026
 */
class HealthDashboardWidgetTest extends WidgetBase {

  private static SystemHealthCheck check(String serviceName, boolean up) {
    SystemHealthCheck record = new SystemHealthCheck();
    record.setServiceName(serviceName);
    record.setStatus(up ? SystemHealthCheck.STATUS_UP : SystemHealthCheck.STATUS_DOWN);
    record.setResponseTimeMs(10);
    return record;
  }

  @Test
  void executeLoadsLatestChecksAndUptimeForAnAdmin() {
    setRoles(widgetContext, ADMIN);
    List<SystemHealthCheck> latest = List.of(check("database", true), check("filesystem", false));

    try (MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class);
        MockedStatic<ServiceErrorRepository> errors = mockStatic(ServiceErrorRepository.class)) {
      repository.when(SystemHealthCheckRepository::findLatestPerService).thenReturn(latest);
      repository.when(() -> SystemHealthCheckRepository.findUptimePercent("database", 24)).thenReturn(100.0);
      repository.when(() -> SystemHealthCheckRepository.findUptimePercent("filesystem", 24)).thenReturn(50.0);
      errors.when(() -> ServiceErrorRepository.findRecent(anyInt())).thenReturn(List.of());

      WidgetContext result = new HealthDashboardWidget().execute(widgetContext);

      assertEquals("/admin/health-dashboard.jsp", result.getJsp());
      assertEquals(latest, result.getRequest().getAttribute("latestChecks"));
      @SuppressWarnings("unchecked")
      Map<String, Double> uptimeByService = (Map<String, Double>) result.getRequest().getAttribute("uptimeByService");
      assertEquals(100.0, uptimeByService.get("database"));
      assertEquals(50.0, uptimeByService.get("filesystem"));
    }
  }

  @Test
  void executeHandlesNoChecksHavingRunYet() {
    // The real state right after a fresh install/deploy, before SystemHealthJob's first
    // Cron.minutely() run has persisted anything -- health-dashboard.jsp has a dedicated
    // "No health checks have run yet" branch for exactly this.
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class);
        MockedStatic<ServiceErrorRepository> errors = mockStatic(ServiceErrorRepository.class)) {
      repository.when(SystemHealthCheckRepository::findLatestPerService).thenReturn(List.of());
      errors.when(() -> ServiceErrorRepository.findRecent(anyInt())).thenReturn(List.of());

      WidgetContext result = new HealthDashboardWidget().execute(widgetContext);

      assertEquals("/admin/health-dashboard.jsp", result.getJsp());
      assertEquals(List.of(), result.getRequest().getAttribute("latestChecks"));
      @SuppressWarnings("unchecked")
      Map<String, Double> uptimeByService = (Map<String, Double>) result.getRequest().getAttribute("uptimeByService");
      assertTrue(uptimeByService.isEmpty());
      repository.verify(() -> SystemHealthCheckRepository.findUptimePercent(anyString(), anyInt()), never());
    }
  }

  @Test
  void executeLoadsRecentServiceErrorsForAnAdmin() {
    setRoles(widgetContext, ADMIN);
    ServiceError error = new ServiceError();
    error.setExceptionClass("java.lang.NullPointerException");
    error.setMessage("boom");

    try (MockedStatic<SystemHealthCheckRepository> healthRepository = mockStatic(SystemHealthCheckRepository.class);
        MockedStatic<ServiceErrorRepository> errors = mockStatic(ServiceErrorRepository.class)) {
      healthRepository.when(SystemHealthCheckRepository::findLatestPerService).thenReturn(List.of());
      errors.when(() -> ServiceErrorRepository.findRecent(50)).thenReturn(List.of(error));

      WidgetContext result = new HealthDashboardWidget().execute(widgetContext);

      assertEquals(List.of(error), result.getRequest().getAttribute("recentErrors"));
      assertEquals(50, result.getRequest().getAttribute("recentErrorLimit"));
    }
  }

  @Test
  void executeDoesNothingForAUserWithoutAdmin() {
    // WidgetBase's default logged-in test user has no roles at all
    try (MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class)) {
      WidgetContext result = new HealthDashboardWidget().execute(widgetContext);

      assertNull(result.getJsp());
      repository.verifyNoInteractions();
    }
  }

  @Test
  void postRunCheckNowSavesEveryCheckResultAndRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "runCheckNow");

    try (MockedStatic<HealthCommand> healthCommand = mockStatic(HealthCommand.class);
        MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class)) {
      healthCommand.when(HealthCommand::runAllChecks).thenReturn(List.of(
          new HealthCommand.CheckResult(HealthCommand.DATABASE_SERVICE, true, 8, null),
          new HealthCommand.CheckResult(HealthCommand.FILESYSTEM_SERVICE, true, 3, null)));

      WidgetContext result = new HealthDashboardWidget().post(widgetContext);

      repository.verify(() -> SystemHealthCheckRepository.save(any(SystemHealthCheck.class)), times(2));
      assertEquals("/admin/health-dashboard", result.getRedirect());
    }
  }

  @Test
  void postDoesNothingForAUserWithoutAdmin() {
    addQueryParameter(widgetContext, "command", "runCheckNow");

    try (MockedStatic<HealthCommand> healthCommand = mockStatic(HealthCommand.class)) {
      WidgetContext result = new HealthDashboardWidget().post(widgetContext);

      assertNull(result.getRedirect());
      healthCommand.verifyNoInteractions();
    }
  }

  @Test
  void postIgnoresAnUnrecognizedCommandButStillRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "somethingElse");

    try (MockedStatic<HealthCommand> healthCommand = mockStatic(HealthCommand.class);
        MockedStatic<SystemHealthCheckRepository> repository = mockStatic(SystemHealthCheckRepository.class)) {
      WidgetContext result = new HealthDashboardWidget().post(widgetContext);

      healthCommand.verify(HealthCommand::runAllChecks, never());
      repository.verify(() -> SystemHealthCheckRepository.save(any()), never());
      assertEquals("/admin/health-dashboard", result.getRedirect());
    }
  }
}
