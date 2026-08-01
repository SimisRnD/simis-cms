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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Covers wiring ContentAccessibilityCommand (#258) into the classic /content-editor page's save
 * path as a non-blocking author-facing notice, plus (#833) draft-save auditing parity with the
 * inline Visual Editor overlay's ContentHtmlCommand.saveDraft() -- including that a gated publish
 * attempt (falls back to a draft save as a side effect of the review-required gate) must still
 * produce exactly one audit event, not one for the gate and a second for the fallback save.
 *
 * @author elizabeth houser
 */
class ContentEditorWidgetTest extends WidgetBase {

  @Test
  void postSaveAsDraftRecordsAContentSaveDraftAuditEvent() {
    // #833: before this fix, the draft-save branch here had no AuditEventCommand.record() call at
    // all -- the inline overlay's ContentHtmlCommand.saveDraft() audited every draft save, but this
    // classic /content-editor page silently did not, even though both write to the same governed
    // content/draft_content record. Verify the same call shape (category/action/outcome/target) the
    // overlay's own test (ContentWidgetTest#postSaveDraftForwardsToActionAndPersistsContent) checks.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Edited content</p>");
    addQueryParameter(widgetContext, "save", "Save as Draft");

    Content savedContent = new Content();
    savedContent.setId(42L);
    savedContent.setUniqueId("hello-content");
    savedContent.setDraftContent("<p>Edited content</p>");

    try (MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class)) {
      saveContent
          .when(() -> SaveContentCommand.saveSafeContent(eq("hello-content"), eq("<p>Edited content</p>"), anyLong(),
              eq(false)))
          .thenReturn(savedContent);

      ContentEditorWidget widget = new ContentEditorWidget();
      widgetContext = widget.post(widgetContext);

      auditEvent.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.saveDraft"),
          eq(AuditEventCommand.SUCCESS), eq("content"), eq("42"), eq("hello-content"), any()), times(1));
      // Only the draft event should fire -- no publish event on this path.
      auditEvent.verify(() -> AuditEventCommand.record(any(), any(), eq("content.publish"), any(), any(), any(),
          any(), any()), never());
      Assertions.assertNull(widgetContext.getErrorMessage());
    }
  }

  @Test
  void postGatedPublishRecordsOnlyOneContentPublishFailureEventAndNoSaveDraftEvent() {
    // #833 round 2: a publish attempt that gets gated by "content.review.required" falls through to
    // the same else-branch as a genuine "Save as Draft" click (both end up with publish == false), so
    // round 1's new content.saveDraft audit call fired there too -- double-auditing a single user
    // action alongside the pre-existing content.publish FAILURE event. Before round 1, this scenario
    // correctly produced exactly one audit event; this test locks that back in.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Edited content</p>");
    addQueryParameter(widgetContext, "save", "Publish Immediately");

    Content savedContent = new Content();
    savedContent.setId(42L);
    savedContent.setUniqueId("hello-content");
    savedContent.setDraftContent("<p>Edited content</p>");

    try (MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required"))
          .thenReturn(true);
      // The gate forces the actual save to a draft, exactly as SaveContentCommand itself would enforce.
      saveContent
          .when(() -> SaveContentCommand.saveSafeContent(eq("hello-content"), eq("<p>Edited content</p>"), anyLong(),
              eq(false)))
          .thenReturn(savedContent);

      ContentEditorWidget widget = new ContentEditorWidget();
      widgetContext = widget.post(widgetContext);

      auditEvent.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.publish"),
          eq(AuditEventCommand.FAILURE), eq("content"), eq("42"), eq("hello-content"), any()), times(1));
      auditEvent.verify(() -> AuditEventCommand.record(any(), any(), eq("content.saveDraft"), any(), any(), any(),
          any(), any()), never());
      Assertions.assertNull(widgetContext.getErrorMessage());
    }
  }

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
}
