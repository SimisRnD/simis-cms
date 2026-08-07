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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Tests the "Add content" box's post() (issue #499 follow-up). A reference name is a lookup key,
 * not a namespace scoped to this form -- typing an existing one used to redirect straight into
 * editing that existing, possibly load-bearing block with no warning at all. Covers both the
 * new-name (unchanged, fast) path and the existing-name (now warned) path, plus the pre-existing
 * character-set validation this method also does.
 *
 * @author SimIS Inc.
 */
class ContentFormWidgetTest extends WidgetBase {

  @Test
  void blankUniqueIdIsRejectedWithoutCheckingTheRepository() {
    addQueryParameter(widgetContext, "uniqueId", "   ");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      WidgetContext result = new ContentFormWidget().post(widgetContext);

      repository.verifyNoInteractions();
      assertEquals("A value is required", result.getWarningMessage());
      assertNull(result.getRedirect());
    }
  }

  @Test
  void invalidCharactersAreRejectedWithoutCheckingTheRepository() {
    addQueryParameter(widgetContext, "uniqueId", "not!valid$$");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      WidgetContext result = new ContentFormWidget().post(widgetContext);

      repository.verifyNoInteractions();
      assertEquals("Use a-z, 0-9 and dashes", result.getWarningMessage());
      assertNull(result.getRedirect());
    }
  }

  @Test
  void aNewReferenceNameRedirectsToTheContentEditorWithNoWarning() {
    addQueryParameter(widgetContext, "uniqueId", "new-block");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("new-block")).thenReturn(null);

      WidgetContext result = new ContentFormWidget().post(widgetContext);

      assertEquals("/content-editor?uniqueId=new-block&returnPage=/admin/content-list", result.getRedirect());
      assertNull(result.getWarningMessage());
    }
  }

  @Test
  void anExistingReferenceNameWarnsInsteadOfSilentlyRedirecting() {
    // The bug: typing an existing name (e.g. accidentally re-typing "site-footer") used to redirect
    // straight into editing that existing, possibly load-bearing block with no warning at all. The
    // fix stays on this page (see ContentFormWidget#post for why: a redirect to /content-editor is a
    // different page, and the framework's flash-message mechanism does not survive that hop) with a
    // warning naming the reference name, rather than silently taking it over.
    addQueryParameter(widgetContext, "uniqueId", "site-footer");

    Content existing = new Content();
    existing.setId(1L);
    existing.setUniqueId("site-footer");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId("site-footer")).thenReturn(existing);

      WidgetContext result = new ContentFormWidget().post(widgetContext);

      assertNull(result.getRedirect(), "must not silently open the existing block for editing");
      assertNotNull(result.getWarningMessage());
      assertTrue(result.getWarningMessage().contains("site-footer"), result.getWarningMessage());
      assertTrue(result.getWarningMessage().contains("already exists"), result.getWarningMessage());
    }
  }

  @Test
  void theTypedValueIsNormalizedBeforeCheckingTheRepository() {
    // Formatting (trim/lowercase/spaces-to-dashes) happens before the character-set check and must
    // also happen before the existing-name lookup, so "Site Footer" is checked (and would warn) as
    // "site-footer", not looked up verbatim.
    addQueryParameter(widgetContext, "uniqueId", "  Site Footer  ");

    Content existing = new Content();
    existing.setId(1L);
    existing.setUniqueId("site-footer");

    try (MockedStatic<ContentRepository> repository = mockStatic(ContentRepository.class)) {
      repository.when(() -> ContentRepository.findByUniqueId(eq("site-footer"))).thenReturn(existing);

      WidgetContext result = new ContentFormWidget().post(widgetContext);

      repository.verify(() -> ContentRepository.findByUniqueId("site-footer"));
      assertNull(result.getRedirect());
      assertNotNull(result.getWarningMessage());
    }
  }
}
