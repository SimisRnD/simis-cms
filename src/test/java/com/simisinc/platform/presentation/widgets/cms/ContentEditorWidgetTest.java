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

package com.simisinc.platform.presentation.widgets.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.sql.Timestamp;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Covers two independent concerns on the classic /content-editor page's save path:
 * <p>
 * - Wiring ContentAccessibilityCommand (#258) in as a non-blocking author-facing notice.
 * <p>
 * - Wiring PublishEventCachePurgeHandler (#420) so the widget's own post() method triggers an AFD
 * cache purge on publish, and correctly skips it for a plain "Save as Draft".
 *
 * @author elizabeth houser
 */
class ContentEditorWidgetTest extends WidgetBase {

  @Test
  void postSurfacesAccessibilityFindingsAsAWarningWhenPresent() {
    // #258: a11y-lint is wired in as a non-blocking, purely additive notice -- it must never
    // prevent or delay the save, which SaveContentCommand.saveSafeContent (mocked here) already
    // completed successfully by the time the check runs.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Text</p><img src=\"/assets/foo.jpg\">");
    addQueryParameter(widgetContext, "save", "Save as Draft");

    Content savedContent = new Content();
    savedContent.setId(42L);
    savedContent.setUniqueId("hello-content");
    savedContent.setDraftContent("<p>Text</p><img src=\"/assets/foo.jpg\">");

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class)) {
      saveContent
          .when(() -> SaveContentCommand.saveSafeContent(eq("hello-content"),
              eq("<p>Text</p><img src=\"/assets/foo.jpg\">"), anyLong(), eq(false)))
          .thenReturn(savedContent);

      ContentEditorWidget widget = new ContentEditorWidget();
      widgetContext = widget.post(widgetContext);

      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNotNull(widgetContext.getWarningMessage());
      Assertions.assertTrue(widgetContext.getWarningMessage().contains("accessibility"),
          widgetContext.getWarningMessage());
      Assertions.assertTrue(widgetContext.getWarningMessage().contains("missing alt text"),
          widgetContext.getWarningMessage());
    }
  }

  @Test
  void postDoesNotSetAWarningWhenContentIsClean() {
    // The flip side: a clean save must not set warningMessage at all. page_messages.jspf only
    // renders the warning callout when the field is non-empty, so an unset field is the contract.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Edited content</p>");
    addQueryParameter(widgetContext, "save", "Save as Draft");

    Content savedContent = new Content();
    savedContent.setId(42L);
    savedContent.setUniqueId("hello-content");
    savedContent.setDraftContent("<p>Edited content</p>");

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class)) {
      saveContent
          .when(() -> SaveContentCommand.saveSafeContent(eq("hello-content"), eq("<p>Edited content</p>"), anyLong(),
              eq(false)))
          .thenReturn(savedContent);

      ContentEditorWidget widget = new ContentEditorWidget();
      widgetContext = widget.post(widgetContext);

      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNull(widgetContext.getWarningMessage());
    }
  }

  @Test
  void publishingContentTriggersOnPageUpdated() throws Exception {
    // #420: the inline content editor's "Save Draft"/publish flow (post()) is a real path that
    // changes a live page's rendered HTML but never triggered any AFD cache purge -- this confirms
    // the widget's own post() method calls the purge hook, not just PublishEventCachePurgeHandler
    // in isolation.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Hello</p>");
    addQueryParameter(widgetContext, "returnPage", "/about");

    Content saved = new Content();
    saved.setId(3L);
    saved.setUniqueId("hello-content");

    WebPage webPage = new WebPage();
    webPage.setId(9L);
    webPage.setLink("/about");
    // Modified moments ago: DateCommand.isHoursOld(modified, 10) is false, so the activity-feed
    // debounce suppresses the WorkflowManager event -- the AFD purge must fire regardless (see
    // #420 design notes; a "just updated" page still needs its stale cached response invalidated).
    webPage.setModified(new Timestamp(System.currentTimeMillis()));

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentReviewCommand> review = mockStatic(ContentReviewCommand.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<WorkflowManager> workflow = mockStatic(WorkflowManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      saveContent.when(() -> SaveContentCommand.saveSafeContent(anyString(), anyString(), anyLong(), eq(true)))
          .thenReturn(saved);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required")).thenReturn(false);
      review.when(() -> ContentReviewCommand.mayPublishDirectly(anyBoolean())).thenReturn(true);
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink("/about")).thenReturn(webPage);

      new ContentEditorWidget().post(widgetContext);

      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(webPage));
      // The debounce that suppresses the activity-feed event for a stale "modified" timestamp must
      // NOT suppress the purge -- the page's rendered content changed regardless.
      workflow.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()), never());
    }
  }

  @Test
  void savingAsDraftNeverTriggersAPurge() throws Exception {
    // "Save as Draft" never publishes -- the live page is unchanged, so there is nothing to purge.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Hello</p>");
    addQueryParameter(widgetContext, "returnPage", "/about");
    addQueryParameter(widgetContext, "save", "Save as Draft");

    Content saved = new Content();
    saved.setId(3L);
    saved.setUniqueId("hello-content");

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      saveContent.when(() -> SaveContentCommand.saveSafeContent(anyString(), anyString(), anyLong(), eq(false)))
          .thenReturn(saved);

      new ContentEditorWidget().post(widgetContext);

      purge.verifyNoInteractions();
    }
  }
}
