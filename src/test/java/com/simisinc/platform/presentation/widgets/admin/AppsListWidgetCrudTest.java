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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Named separately from {@code AppsListWidgetTest} -- that pre-existing file actually exercises
 * {@code ApisListWidget} (a longstanding naming collision documented on that class, left as-is,
 * outside this change's scope) and has no coverage of the real {@link AppsListWidget}.
 *
 * <p>
 * Covers the real "Devices" count (previously a hardcoded 0, not bound to any query -- see the
 * apps-list.jsp Devices column) and the new Delete action, including its audit trail: before this
 * change there was no in-app way to disable or delete an App at all, so a leaked Client ID had no
 * remediation path.
 *
 * @author elizabeth houser
 */
class AppsListWidgetCrudTest extends WidgetBase {

  @Test
  void executeBindsARealPerAppDeviceCountInsteadOfAHardcodedZero() {
    App app1 = new App();
    app1.setId(1L);
    app1.setName("Mobile App");
    App app2 = new App();
    app2.setId(2L);
    app2.setName("Kiosk App");

    try (MockedStatic<AppRepository> appRepository = mockStatic(AppRepository.class);
        MockedStatic<SessionRepository> sessionRepository = mockStatic(SessionRepository.class)) {
      appRepository.when(AppRepository::findAll).thenReturn(List.of(app1, app2));
      sessionRepository.when(() -> SessionRepository.countByAppId(1L)).thenReturn(37L);
      sessionRepository.when(() -> SessionRepository.countByAppId(2L)).thenReturn(0L);

      new AppsListWidget().execute(widgetContext);

      @SuppressWarnings("unchecked")
      Map<Long, Long> deviceCountByAppId = (Map<Long, Long>) request.getAttribute("deviceCountByAppId");
      assertNotNull(deviceCountByAppId);
      assertEquals(37L, deviceCountByAppId.get(1L));
      assertEquals(0L, deviceCountByAppId.get(2L));
    }
  }

  @Test
  void deleteRequiresTheAdminRole() {
    addQueryParameter(widgetContext, "id", "1");
    // No admin role granted

    try (MockedStatic<AppRepository> appRepository = mockStatic(AppRepository.class)) {
      WidgetContext result = new AppsListWidget().delete(widgetContext);

      assertEquals("Must be an admin", result.getWarningMessage());
      appRepository.verify(() -> AppRepository.remove(any()), never());
    }
  }

  @Test
  void deleteRemovesTheAppAndRecordsAnAuditEvent() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "1");

    App app = new App();
    app.setId(1L);
    app.setName("Leaked Client");

    try (MockedStatic<AppRepository> appRepository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      appRepository.when(() -> AppRepository.findById(1L)).thenReturn(app);
      appRepository.when(() -> AppRepository.remove(app)).thenReturn(true);

      WidgetContext result = new AppsListWidget().delete(widgetContext);

      appRepository.verify(() -> AppRepository.remove(app), times(1));
      assertEquals("App was deleted", result.getSuccessMessage());
      assertNull(result.getErrorMessage());
      // No in-app remediation path for a leaked Client ID existed before this change
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("app.delete"), eq(AuditEventCommand.SUCCESS),
          eq("app"), eq("1"), eq("Leaked Client"), any()));
    }
  }

  @Test
  void deleteReportsFailureAndAuditsItWhenTheRepositoryRefuses() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "1");

    App app = new App();
    app.setId(1L);
    app.setName("Still In Use");

    try (MockedStatic<AppRepository> appRepository = mockStatic(AppRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      appRepository.when(() -> AppRepository.findById(1L)).thenReturn(app);
      // e.g. a FK constraint from sessions.app_id blocks the delete
      appRepository.when(() -> AppRepository.remove(app)).thenReturn(false);

      WidgetContext result = new AppsListWidget().delete(widgetContext);

      assertEquals("App could not be deleted", result.getWarningMessage());
      audit.verify(() -> AuditEventCommand.record(any(), any(), eq("app.delete"), eq(AuditEventCommand.FAILURE),
          eq("app"), eq("1"), eq("Still In Use"), any()));
    }
  }

  @Test
  void deleteOfAMissingAppReportsAnErrorWithoutCallingRemove() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "id", "99");

    try (MockedStatic<AppRepository> appRepository = mockStatic(AppRepository.class)) {
      appRepository.when(() -> AppRepository.findById(99L)).thenReturn(null);

      WidgetContext result = new AppsListWidget().delete(widgetContext);

      assertEquals("App was not found", result.getErrorMessage());
      appRepository.verify(() -> AppRepository.remove(any()), never());
    }
  }
}
