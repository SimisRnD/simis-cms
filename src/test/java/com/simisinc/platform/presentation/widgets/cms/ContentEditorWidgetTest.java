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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.cms.Content;

/**
 * Covers wiring ContentAccessibilityCommand (#258) into the classic /content-editor page's save
 * path as a non-blocking author-facing notice.
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
    addQueryParameter(widgetContext, "returnPage", "/about-us");

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

      // #826: setting the warning was never the bug -- it silently never reached the user because
      // the redirect went to returnPage, a page that doesn't include the content-editor widget, so
      // the flash-message mechanism (keyed on this widget's uniqueId re-rendering on the very next
      // page) never got a chance to pick it back up. When there's a warning to show, the redirect
      // must go back to the editor itself instead, with the content's uniqueId and the original
      // returnPage preserved (a full round-trip proof that the message is actually retrievable
      // there lives in WebContainerCommandTest#a11yWarningSurvivesARedirectBackToTheContentEditor).
      Assertions.assertEquals("/content-editor?uniqueId=hello-content&returnPage=%2Fabout-us",
          widgetContext.getRedirect());
    }
  }

  @Test
  void postDoesNotSetAWarningWhenContentIsClean() {
    // The flip side: a clean save must not set warningMessage at all. page_messages.jspf only
    // renders the warning callout when the field is non-empty, so an unset field is the contract.
    addQueryParameter(widgetContext, "uniqueId", "hello-content");
    addQueryParameter(widgetContext, "content", "<p>Edited content</p>");
    addQueryParameter(widgetContext, "save", "Save as Draft");
    addQueryParameter(widgetContext, "returnPage", "/about-us");

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

      // #826: a clean save must be completely unaffected by the fix above -- still redirect to the
      // live page (returnPage), exactly as before.
      Assertions.assertEquals("/about-us", widgetContext.getRedirect());
    }
  }
}
