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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WebRequestFilter;
import com.simisinc.platform.presentation.controller.WidgetContext;

class WebRedirectListWidgetTest extends WidgetBase {

  private static WebRedirect redirect(long id, boolean enabled) {
    WebRedirect record = new WebRedirect();
    record.setId(id);
    record.setFromPath("/old-page");
    record.setToUrl("/new-page");
    record.setStatusCode(301);
    record.setEnabled(enabled);
    return record;
  }

  @Test
  void deleteActuallyDeletesTheRecordAndRecordsAnAuditEvent() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webRedirectId", "4");

    WebRedirect target = redirect(4L, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebRedirectRepository.findById(4L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.remove(target)).thenReturn(true);

      WidgetContext result = new WebRedirectListWidget().delete(widgetContext);

      repository.verify(() -> WebRedirectRepository.remove(target));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION),
          eq("web_redirect.remove"), eq(AuditEventCommand.SUCCESS), eq("web_redirect"), eq("4"),
          eq("/old-page"), any()));
      assertEquals("Web redirect deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deletePurgesTheFromPathFromTheLegacyCsvFallback() {
    // Issue #408 review: without this, a deleted redirect whose from_path also happens to be
    // defined in the legacy redirects.csv file would keep being served by that fallback for the
    // rest of this server's uptime -- see WebRequestFilter.purgeCsvFallback().
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webRedirectId", "4");

    WebRedirect target = redirect(4L, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<WebRequestFilter> requestFilter = mockStatic(WebRequestFilter.class)) {
      repository.when(() -> WebRedirectRepository.findById(4L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.remove(target)).thenReturn(true);

      new WebRedirectListWidget().delete(widgetContext);

      requestFilter.verify(() -> WebRequestFilter.purgeCsvFallback("/old-page"));
    }
  }

  @Test
  void aFailedDeleteDoesNotPurgeTheCsvFallback() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webRedirectId", "4");

    WebRedirect target = redirect(4L, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<WebRequestFilter> requestFilter = mockStatic(WebRequestFilter.class)) {
      repository.when(() -> WebRedirectRepository.findById(4L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.remove(target)).thenReturn(false);

      new WebRedirectListWidget().delete(widgetContext);

      requestFilter.verify(() -> WebRequestFilter.purgeCsvFallback(any()), never());
    }
  }

  @Test
  void deleteAllowsAContentManagerNotJustAnAdmin() {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "webRedirectId", "4");

    WebRedirect target = redirect(4L, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebRedirectRepository.findById(4L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.remove(target)).thenReturn(true);

      WidgetContext result = new WebRedirectListWidget().delete(widgetContext);

      repository.verify(() -> WebRedirectRepository.remove(target));
      assertEquals("Web redirect deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteWithoutAnAuthorizedRoleDoesNotRemoveAnything() {
    // WidgetBase's default login() has no roles granted -- mirrors an unauthorized request.
    addQueryParameter(widgetContext, "webRedirectId", "4");

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      new WebRedirectListWidget().delete(widgetContext);

      repository.verify(() -> WebRedirectRepository.findById(4L), never());
      repository.verify(() -> WebRedirectRepository.remove(any()), never());
    }
  }

  @Test
  void toggleEnabledFlipsAnEnabledRedirectToDisabled() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "toggleEnabled");
    addQueryParameter(widgetContext, "webRedirectId", "6");

    WebRedirect target = redirect(6L, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebRedirectRepository.findById(6L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.update(target)).thenAnswer(i -> i.getArgument(0));

      WidgetContext result = new WebRedirectListWidget().post(widgetContext);

      assertFalse(target.getEnabled(), "toggling an enabled redirect must disable it");
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION),
          eq("web_redirect.disable"), eq(AuditEventCommand.SUCCESS), any(), any(), any(), any()));
      assertEquals("Web redirect disabled", result.getSuccessMessage());
    }
  }

  @Test
  void toggleEnabledFlipsADisabledRedirectToEnabled() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "toggleEnabled");
    addQueryParameter(widgetContext, "webRedirectId", "6");

    WebRedirect target = redirect(6L, false);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebRedirectRepository.findById(6L)).thenReturn(target);
      repository.when(() -> WebRedirectRepository.update(target)).thenAnswer(i -> i.getArgument(0));

      WidgetContext result = new WebRedirectListWidget().post(widgetContext);

      assertTrue(target.getEnabled(), "toggling a disabled redirect must enable it");
      assertEquals("Web redirect enabled", result.getSuccessMessage());
    }
  }

  @Test
  void toggleEnabledWithoutAnAuthorizedRoleIsANoOp() {
    addQueryParameter(widgetContext, "action", "toggleEnabled");
    addQueryParameter(widgetContext, "webRedirectId", "6");

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      new WebRedirectListWidget().post(widgetContext);

      repository.verify(() -> WebRedirectRepository.findById(anyLong()), never());
      repository.verify(() -> WebRedirectRepository.update(any()), never());
    }
  }

  @Test
  void postWithoutAnyKnownActionIsANoOp() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webRedirectId", "6");

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      new WebRedirectListWidget().post(widgetContext);

      repository.verify(() -> WebRedirectRepository.findById(anyLong()), never());
      repository.verify(() -> WebRedirectRepository.update(any()), never());
    }
  }
}
