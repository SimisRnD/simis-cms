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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.ContentVersionDiffCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.ContentVersion;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentVersionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the /admin/content-versions widget (#406): listing a content block's prior published
 * versions, comparing two of them with a word-level diff, and restoring a selected one back into the
 * draft slot.
 *
 * @author elizabeth houser
 */
class ContentVersionsListWidgetTest extends WidgetBase {

  private static Content content(long id, String uniqueId) {
    Content record = new Content();
    record.setId(id);
    record.setUniqueId(uniqueId);
    return record;
  }

  private static ContentVersion version(long id, long contentId, String html, long approvedBy) {
    ContentVersion record = new ContentVersion();
    record.setId(id);
    record.setContentId(contentId);
    record.setContent(html);
    record.setApprovedBy(approvedBy);
    return record;
  }

  @Test
  void executeLoadsTheContentVersionsAndApproverMap() {
    addQueryParameter(widgetContext, "uniqueId", "example-block");
    Content record = content(3L, "example-block");
    ContentVersion version = version(10L, 3L, "<p>v1</p>", 7L);
    User approver = new User();
    approver.setId(7L);
    approver.setFirstName("Jamie");

    try (MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class);
        MockedStatic<ContentVersionRepository> versionRepo = mockStatic(ContentVersionRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      contentRepo.when(() -> ContentRepository.findByUniqueId("example-block")).thenReturn(record);
      versionRepo.when(() -> ContentVersionRepository.findByContentId(eq(3L), any(DataConstraints.class)))
          .thenReturn(List.of(version));
      userRepo.when(() -> UserRepository.findByUserId(7L)).thenReturn(approver);

      WidgetContext result = new ContentVersionsListWidget().execute(widgetContext);

      assertEquals(record, result.getRequest().getAttribute("content"));
      assertEquals(List.of(version), result.getRequest().getAttribute("versionList"));
      Map<Long, User> userMap = (Map<Long, User>) result.getRequest().getAttribute("userMap");
      assertEquals(approver, userMap.get(7L));
    }
  }

  @Test
  void executeSetsAnErrorWhenTheContentIsNotFound() {
    addQueryParameter(widgetContext, "uniqueId", "missing-block");

    try (MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class)) {
      contentRepo.when(() -> ContentRepository.findByUniqueId("missing-block")).thenReturn(null);

      WidgetContext result = new ContentVersionsListWidget().execute(widgetContext);

      assertEquals("Content was not found", result.getErrorMessage());
    }
  }

  @Test
  void executeComputesAWordDiffWhenTwoOwnVersionsAreSelected() {
    addQueryParameter(widgetContext, "uniqueId", "example-block");
    addQueryParameter(widgetContext, "compareFrom", "10");
    addQueryParameter(widgetContext, "compareTo", "11");
    Content record = content(3L, "example-block");
    ContentVersion from = version(10L, 3L, "<p>Hello world</p>", 7L);
    ContentVersion to = version(11L, 3L, "<p>Hello there</p>", 7L);

    try (MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class);
        MockedStatic<ContentVersionRepository> versionRepo = mockStatic(ContentVersionRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      contentRepo.when(() -> ContentRepository.findByUniqueId("example-block")).thenReturn(record);
      versionRepo.when(() -> ContentVersionRepository.findByContentId(eq(3L), any(DataConstraints.class)))
          .thenReturn(List.of(from, to));
      versionRepo.when(() -> ContentVersionRepository.findById(10L)).thenReturn(from);
      versionRepo.when(() -> ContentVersionRepository.findById(11L)).thenReturn(to);
      userRepo.when(() -> UserRepository.findByUserId(7L)).thenReturn(null);

      WidgetContext result = new ContentVersionsListWidget().execute(widgetContext);

      ContentVersionDiffCommand.Result diffResult =
          (ContentVersionDiffCommand.Result) result.getRequest().getAttribute("diffResult");
      assertEquals("Hello <del>world</del> <ins>there</ins>", diffResult.getHtml());
    }
  }

  @Test
  void executeRejectsAComparisonVersionThatBelongsToADifferentContentBlock() {
    // A stray/foreign contentVersionId must never diff content that doesn't belong to this block.
    addQueryParameter(widgetContext, "uniqueId", "example-block");
    addQueryParameter(widgetContext, "compareFrom", "10");
    addQueryParameter(widgetContext, "compareTo", "99");
    Content record = content(3L, "example-block");
    ContentVersion from = version(10L, 3L, "<p>Hello world</p>", 7L);
    ContentVersion foreign = version(99L, 999L, "<p>someone else's content</p>", 7L);

    try (MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class);
        MockedStatic<ContentVersionRepository> versionRepo = mockStatic(ContentVersionRepository.class);
        MockedStatic<UserRepository> userRepo = mockStatic(UserRepository.class)) {
      contentRepo.when(() -> ContentRepository.findByUniqueId("example-block")).thenReturn(record);
      versionRepo.when(() -> ContentVersionRepository.findByContentId(eq(3L), any(DataConstraints.class)))
          .thenReturn(List.of(from));
      versionRepo.when(() -> ContentVersionRepository.findById(10L)).thenReturn(from);
      versionRepo.when(() -> ContentVersionRepository.findById(99L)).thenReturn(foreign);
      userRepo.when(() -> UserRepository.findByUserId(7L)).thenReturn(null);

      WidgetContext result = new ContentVersionsListWidget().execute(widgetContext);

      assertNull(result.getRequest().getAttribute("diffResult"));
      assertEquals("One or both selected versions could not be compared", result.getWarningMessage());
    }
  }

  @Test
  void postRestoresTheDraftFromTheSelectedVersion() throws Exception {
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "uniqueId", "example-block");
    addQueryParameter(widgetContext, "contentVersionId", "10");
    ContentVersion version = version(10L, 3L, "<p>old</p>", 7L);
    Content record = content(3L, "example-block");

    try (MockedStatic<ContentVersionRepository> versionRepo = mockStatic(ContentVersionRepository.class);
        MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class)) {
      versionRepo.when(() -> ContentVersionRepository.findById(10L)).thenReturn(version);
      contentRepo.when(() -> ContentRepository.findByUniqueId("example-block")).thenReturn(record);
      contentRepo.when(() -> ContentRepository.restoreDraftFromVersion(3L, "<p>old</p>")).thenReturn(true);
      versionRepo.when(() -> ContentVersionRepository.findByContentId(eq(3L), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      WidgetContext result = new ContentVersionsListWidget().post(widgetContext);

      contentRepo.verify(() -> ContentRepository.restoreDraftFromVersion(3L, "<p>old</p>"));
      assertEquals("The version was restored to the draft. It must be reviewed and published again to go live.",
          result.getSuccessMessage());
    }
  }

  @Test
  void postSetsAnErrorWhenTheVersionIsNotFound() throws Exception {
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "uniqueId", "example-block");
    addQueryParameter(widgetContext, "contentVersionId", "404");
    Content record = content(3L, "example-block");

    try (MockedStatic<ContentVersionRepository> versionRepo = mockStatic(ContentVersionRepository.class);
        MockedStatic<ContentRepository> contentRepo = mockStatic(ContentRepository.class)) {
      versionRepo.when(() -> ContentVersionRepository.findById(404L)).thenReturn(null);
      contentRepo.when(() -> ContentRepository.findByUniqueId("example-block")).thenReturn(record);
      versionRepo.when(() -> ContentVersionRepository.findByContentId(eq(3L), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());

      WidgetContext result = new ContentVersionsListWidget().post(widgetContext);

      assertEquals("The selected version was not found", result.getErrorMessage(),
          "the fallthrough execute() reload must not clobber the error set here (needs uniqueId to succeed)");
      contentRepo.verify(() -> ContentRepository.restoreDraftFromVersion(anyLong(), any()), never());
    }
  }

  @Test
  void postIgnoresAnyActionOtherThanRestore() throws Exception {
    addQueryParameter(widgetContext, "action", "somethingElse");

    WidgetContext result = new ContentVersionsListWidget().post(widgetContext);

    assertNull(result);
  }
}
