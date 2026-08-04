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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers the 4 bulk actions on /admin/web-pages (issue #427, mirroring
 * CalendarEventListWidgetBulkActionsTest's shape for issue #882/PR #911): a batch over
 * MAX_BULK_SELECTION is rejected outright rather than truncated, an empty selection is rejected,
 * one id that no longer resolves never aborts the rest of the batch, bulkPublish/bulkUnpublish
 * reuse {@link SaveWebPageCommand#saveWebPage} (not a raw repository call) so the same domain
 * events the single-item save fires still fire, and bulkDelete requires the stricter admin-only
 * gate {@code WebPageFormWidget#action}'s single-item delete already requires -- not the
 * admin-or-content-manager pairing the other three commands (and the calendar precedent's every
 * command) accept.
 *
 * @author SimIS Inc.
 */
class WebPageListWidgetBulkActionsTest extends WidgetBase {

  private static WebPage webPageWithId(long id) {
    WebPage webPage = new WebPage();
    webPage.setId(id);
    webPage.setLink("/page-" + id);
    webPage.setTitle("Page " + id);
    return webPage;
  }

  private void multiValue(String name, String... values) {
    widgetContext.getParameterMap().put(name, values);
  }

  // --- Permission gates ---

  @Test
  void nonAdminNonContentManagerCannotReachAnyBulkAction() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.findById(anyLong()), never());
    }
  }

  @Test
  void contentManagerCanReachBulkArchive() {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    WebPage webPage = webPageWithId(5L);
    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(webPage);
      repo.when(() -> WebPageRepository.save(webPage)).thenReturn(webPage);

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.save(webPage), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void contentManagerCannotReachBulkDelete() {
    // Stricter than the other 3 commands (and the calendar precedent's uniform gate): delete is
    // admin-only, matching WebPageFormWidget#action's existing single-item delete gate exactly.
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.findById(anyLong()), never());
    }
  }

  @Test
  void adminCanReachBulkDelete() {
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    WebPage webPage = webPageWithId(5L);
    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<PublishEventCachePurgeHandler> cache = mockStatic(PublishEventCachePurgeHandler.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(webPage);

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.remove(webPage), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  // --- Selection bounds (shared shape across all 4 commands; exercised once each) ---

  @Test
  void overCapSelectionIsRejectedWithNoRepositoryCalls() {
    setRoles(widgetContext, ADMIN);
    String[] tooMany = new String[WebPageListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 100);
    }
    multiValue("webPageId", tooMany);
    addQueryParameter(widgetContext, "command", "bulkArchive");

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      WidgetContext result = new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.findById(anyLong()), never());
      assertTrue(result.getErrorMessage().contains("Too many web pages"));
    }
  }

  @Test
  void emptySelectionIsRejectedForBulkDelete() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "bulkDelete");
    // No webPageId parameters at all

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      WidgetContext result = new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.findById(anyLong()), never());
      assertEquals("No web pages were selected", result.getErrorMessage());
    }
  }

  // --- bulkPublish / bulkUnpublish ---

  @Test
  void bulkPublishReusesSaveWebPageCommandNotARawRepositoryUpdate() {
    // The load-bearing point: SaveWebPageCommand.saveWebPage() fires WebPagePublishedEvent/
    // WebPageUpdatedEvent + PublishEventCachePurgeHandler, exactly what WebPageFormWidget's own
    // publish checkbox already triggers for a single page. A raw repository call would silently
    // skip all of that.
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    WebPage first = webPageWithId(5L);
    first.setDraft(true);
    WebPage second = webPageWithId(6L);
    second.setDraft(true);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(first);
      repo.when(() -> WebPageRepository.findById(6L)).thenReturn(second);
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      assertEquals(false, first.getDraft());
      assertEquals(false, second.getDraft());
      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(first), times(1));
      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(second), times(1));
      repo.verify(() -> WebPageRepository.save(any()), never());
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("success"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkUnpublishSetsDraftTrueAndReusesSaveWebPageCommand() {
    setRoles(widgetContext, CONTENT_MANAGER);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkUnpublish");

    WebPage webPage = webPageWithId(5L);
    webPage.setDraft(false);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(webPage);
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      assertEquals(true, webPage.getDraft());
      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(webPage), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.unpublish"),
          eq("success"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(1));
      assertTrue(result.getSuccessMessage().contains("1 of 1"));
    }
  }

  @Test
  void bulkPublishSkipsAnIdThatNoLongerResolvesButContinues() {
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    WebPage found = webPageWithId(5L);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(found);
      repo.when(() -> WebPageRepository.findById(6L)).thenReturn(null); // deleted concurrently / tampered id
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(any()), times(1));
      assertTrue(result.getWarningMessage().contains("1 of 2"));
      assertTrue(result.getWarningMessage().contains("Not found: 1"));
    }
  }

  @Test
  void bulkPublishFailureFromSaveWebPageCommandIsCountedAsAFailureNotAnAbort() throws Exception {
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkPublish");

    WebPage webPage = webPageWithId(5L);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(webPage);
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenThrow(new com.simisinc.platform.application.DataException("A link is required"));

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.publish"),
          eq("failure"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected web page published. Failed: 1."));
      // Issue #427 code-review finding: the failed row's reason must reach the response, not just
      // the audit log (which a non-admin content-manager triggering this action cannot view).
      assertTrue(result.getErrorMessage().contains("Page 5 (#5): A link is required"));
    }
  }

  // --- bulkArchive ---

  @Test
  void bulkArchiveSetsTheArchivedTimestampOnEachResolvedPage() {
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkArchive");

    WebPage first = webPageWithId(5L);
    WebPage second = webPageWithId(6L);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(first);
      repo.when(() -> WebPageRepository.findById(6L)).thenReturn(second);
      repo.when(() -> WebPageRepository.save(first)).thenReturn(first);
      repo.when(() -> WebPageRepository.save(second)).thenReturn(second);

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      assertNotNull(first.getArchived());
      assertNotNull(second.getArchived());
      repo.verify(() -> WebPageRepository.save(first), times(1));
      repo.verify(() -> WebPageRepository.save(second), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.archive"),
          eq("success"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  // --- bulkDelete ---

  @Test
  void bulkDeleteRemovesEachResolvedPageAndPurgesItsCache() {
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5", "6");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    WebPage first = webPageWithId(5L);
    WebPage second = webPageWithId(6L);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<PublishEventCachePurgeHandler> cache = mockStatic(PublishEventCachePurgeHandler.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(first);
      repo.when(() -> WebPageRepository.findById(6L)).thenReturn(second);

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      repo.verify(() -> WebPageRepository.remove(first), times(1));
      repo.verify(() -> WebPageRepository.remove(second), times(1));
      cache.verify(() -> PublishEventCachePurgeHandler.onPageDeleted(first.getLink()), times(1));
      cache.verify(() -> PublishEventCachePurgeHandler.onPageDeleted(second.getLink()), times(1));
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("success"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(2));
      assertTrue(result.getSuccessMessage().contains("2 of 2"));
    }
  }

  @Test
  void bulkDeleteDoesNotPurgeTheCacheForAFailedRemoval() {
    // WebPageRepository.remove() returns void (unlike CalendarEventRepository.remove()), so a
    // failure is signaled by an exception, mirroring WebPageFormWidget#action's own try/catch.
    setRoles(widgetContext, ADMIN);
    multiValue("webPageId", "5");
    addQueryParameter(widgetContext, "command", "bulkDelete");

    WebPage webPage = webPageWithId(5L);

    try (MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<PublishEventCachePurgeHandler> cache = mockStatic(PublishEventCachePurgeHandler.class);
        MockedStatic<SaveAuditEventCommand> audit = mockStatic(SaveAuditEventCommand.class)) {
      repo.when(() -> WebPageRepository.findById(5L)).thenReturn(webPage);
      repo.when(() -> WebPageRepository.remove(webPage)).thenThrow(new RuntimeException("db error"));

      WidgetContext result = new WebPageListWidget().post(widgetContext);

      cache.verifyNoInteractions();
      audit.verify(() -> SaveAuditEventCommand.recordAdminEvent(eq("content"), eq("content.delete"),
          eq("failure"), anyLong(), any(), any(), any(), eq("web_page"), any(), any(), any()), times(1));
      assertTrue(result.getErrorMessage().startsWith("0 of 1 selected web page deleted. Failed: 1."));
      // Issue #427 code-review finding: the failed row's reason must reach the response, not just
      // the audit log (which a non-admin content-manager triggering this action cannot view).
      assertTrue(result.getErrorMessage().contains("Page 5 (#5): db error"));
    }
  }
}
