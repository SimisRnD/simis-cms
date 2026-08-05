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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveWebRedirectCommand;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class WebRedirectFormWidgetTest extends WidgetBase {

  private static WebRedirect redirect(long id, String fromPath, String toUrl, int statusCode, boolean enabled) {
    WebRedirect record = new WebRedirect();
    record.setId(id);
    record.setFromPath(fromPath);
    record.setToUrl(toUrl);
    record.setStatusCode(statusCode);
    record.setEnabled(enabled);
    return record;
  }

  @Test
  void savingANewRedirectRedirectsToItsEditPage() throws Exception {
    addQueryParameter(widgetContext, "fromPath", "/old-page");
    addQueryParameter(widgetContext, "toUrl", "/new-page");
    addQueryParameter(widgetContext, "statusCode", "301");
    addQueryParameter(widgetContext, "enabled", "true");

    WebRedirect saved = redirect(10L, "/old-page", "/new-page", 301, true);

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean())).thenReturn(saved);

      WidgetContext result = new WebRedirectFormWidget().post(widgetContext);

      assertEquals("/admin/web-redirect?webRedirectId=10", result.getRedirect());
      assertEquals("Web redirect saved", result.getSuccessMessage());

      // Verify the submitted fields, including the status-code select, were forwarded to the save command.
      saveCommand.verify(() -> SaveWebRedirectCommand.save(argThat(
          bean -> "/old-page".equals(bean.getFromPath()) && "/new-page".equals(bean.getToUrl())
              && bean.getStatusCode() == 301 && bean.getEnabled()), anyBoolean()));
    }
  }

  @Test
  void anUncheckedEnabledCheckboxSavesAsDisabled() throws Exception {
    addQueryParameter(widgetContext, "fromPath", "/old-page");
    addQueryParameter(widgetContext, "toUrl", "/new-page");
    addQueryParameter(widgetContext, "statusCode", "302");
    // No "enabled" parameter at all -- mirrors an unchecked HTML checkbox, which submits nothing.

    WebRedirect saved = redirect(11L, "/old-page", "/new-page", 302, false);

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean())).thenReturn(saved);

      new WebRedirectFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebRedirectCommand.save(argThat(bean -> !bean.getEnabled()), anyBoolean()));
    }
  }

  @Test
  void editingAnExistingRedirectPopulatesTheIdFromTheHiddenField() throws Exception {
    addQueryParameter(widgetContext, "id", "5");
    addQueryParameter(widgetContext, "fromPath", "/old-path");
    addQueryParameter(widgetContext, "toUrl", "/updated-target");
    addQueryParameter(widgetContext, "statusCode", "301");
    addQueryParameter(widgetContext, "enabled", "true");

    WebRedirect saved = redirect(5L, "/old-path", "/updated-target", 301, true);

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean())).thenReturn(saved);

      WidgetContext result = new WebRedirectFormWidget().post(widgetContext);

      assertEquals("/admin/web-redirect?webRedirectId=5", result.getRedirect());
      saveCommand.verify(() -> SaveWebRedirectCommand.save(argThat(bean -> bean.getId() == 5L), anyBoolean()));
    }
  }

  @Test
  void aValidationFailureKeepsTheSubmittedValuesAndShowsTheError() throws Exception {
    addQueryParameter(widgetContext, "fromPath", "");
    addQueryParameter(widgetContext, "toUrl", "/new-page");
    addQueryParameter(widgetContext, "statusCode", "301");

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean()))
          .thenThrow(new DataException("A from path is required."));

      WidgetContext result = new WebRedirectFormWidget().post(widgetContext);

      assertTrue(result.getErrorMessage().contains("from path"));
      assertEquals("/admin/web-redirect", result.getRedirect());
    }
  }

  @Test
  void aValidationFailureOnAnExistingRecordRedirectsBackToItsOwnEditPage() throws Exception {
    addQueryParameter(widgetContext, "id", "7");
    addQueryParameter(widgetContext, "fromPath", "/taken-path");
    addQueryParameter(widgetContext, "toUrl", "/target");
    addQueryParameter(widgetContext, "statusCode", "301");

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean()))
          .thenThrow(new DataException("A redirect for that from path already exists."));

      WidgetContext result = new WebRedirectFormWidget().post(widgetContext);

      assertEquals("/admin/web-redirect?webRedirectId=7", result.getRedirect());
    }
  }

  @Test
  void anAdminsSaveIsMarkedAsSuchToTheCommand() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fromPath", "/old-page");
    addQueryParameter(widgetContext, "toUrl", "https://example.com/new-page");
    addQueryParameter(widgetContext, "statusCode", "301");
    addQueryParameter(widgetContext, "enabled", "true");

    WebRedirect saved = redirect(10L, "/old-page", "https://example.com/new-page", 301, true);

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean())).thenReturn(saved);

      new WebRedirectFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebRedirectCommand.save(any(), eq(true)));
    }
  }

  @Test
  void aContentManagersSaveIsMarkedAsSuchToTheCommand() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "fromPath", "/old-page");
    addQueryParameter(widgetContext, "toUrl", "/new-page");
    addQueryParameter(widgetContext, "statusCode", "301");
    addQueryParameter(widgetContext, "enabled", "true");

    WebRedirect saved = redirect(10L, "/old-page", "/new-page", 301, true);

    try (MockedStatic<SaveWebRedirectCommand> saveCommand = mockStatic(SaveWebRedirectCommand.class)) {
      saveCommand.when(() -> SaveWebRedirectCommand.save(any(), anyBoolean())).thenReturn(saved);

      new WebRedirectFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebRedirectCommand.save(any(), eq(false)));
    }
  }

  @Test
  void executeLoadsAnExistingRedirectById() {
    addQueryParameter(widgetContext, "webRedirectId", "8");
    WebRedirect existing = redirect(8L, "/old-page", "/new-page", 301, true);

    try (MockedStatic<WebRedirectRepository> repository = mockStatic(WebRedirectRepository.class)) {
      repository.when(() -> WebRedirectRepository.findById(8L)).thenReturn(existing);

      new WebRedirectFormWidget().execute(widgetContext);

      assertEquals(existing, widgetContext.getRequest().getAttribute("webRedirect"));
    }
  }

  @Test
  void executeWithNoIdBuildsABlankNewRecord() {
    new WebRedirectFormWidget().execute(widgetContext);

    Object attribute = widgetContext.getRequest().getAttribute("webRedirect");
    assertTrue(attribute instanceof WebRedirect);
    assertEquals(-1L, ((WebRedirect) attribute).getId());
  }
}
