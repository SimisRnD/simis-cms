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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;

/**
 * Tests the visual editor's Delta write-path: valid, current-shape Delta is stamped and stored;
 * anything else is rejected before it reaches the repository.
 *
 * @author elizabeth houser
 */
class SaveContentCommandTest {

  private static final String VALID_DELTA = "{\"ops\":[{\"insert\":\"hello\\n\"}]}";

  @Test
  void nullContentIsRejected() {
    assertThrows(DataException.class,
        () -> SaveContentCommand.saveSafeDeltaContent("uid", null, 1L, false));
  }

  @Test
  void malformedDocumentIsRejected() {
    assertThrows(DataException.class,
        () -> SaveContentCommand.saveSafeDeltaContent("uid", "not-a-delta", 1L, false));
    assertThrows(DataException.class,
        () -> SaveContentCommand.saveSafeDeltaContent("uid", "{\"blocks\":[]}", 1L, false));
  }

  @Test
  void legacyDeltaShapeIsRejected() {
    // The removed Quill 1.x embed shape (integer insert) must be migrated, not stored.
    assertThrows(DataException.class,
        () -> SaveContentCommand.saveSafeDeltaContent("uid", "{\"ops\":[{\"insert\":1}]}", 1L, false));
  }

  @Test
  void draftSaveStampsDeltaFormatOnTheDraftOnly() throws DataException {
    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(null);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));

      Content saved = SaveContentCommand.saveSafeDeltaContent("uid", VALID_DELTA, 7L, false);

      assertEquals(VALID_DELTA, saved.getDraftContent());
      assertEquals(DeltaContentCommand.DELTA_FORMAT_VERSION, saved.getDraftContentFormat());
      // The published side is untouched on a draft save -- the mixed HTML/Delta state the two
      // columns exist to represent.
      assertNull(saved.getContent());
      assertEquals(DeltaContentCommand.LEGACY_HTML_FORMAT, saved.getContentFormat());
    }
  }

  @Test
  void publishStampsDeltaFormatAndClearsTheDraft() throws DataException {
    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(null);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
      // #406: a publish now also resolves content.versionHistoryLimit before snapshotting.
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("content.versionHistoryLimit")).thenReturn(null);

      Content saved = SaveContentCommand.saveSafeDeltaContent("uid", VALID_DELTA, 7L, true);

      assertEquals(VALID_DELTA, saved.getContent());
      assertEquals(DeltaContentCommand.DELTA_FORMAT_VERSION, saved.getContentFormat());
      assertNull(saved.getDraftContent());
      assertEquals(DeltaContentCommand.LEGACY_HTML_FORMAT, saved.getDraftContentFormat());
    }
  }

  // --- Version history on direct publish (issue #406 follow-up) ---
  //
  // ContentRepository#publish() (the governed/widget publish path) already snapshots the outgoing
  // content, but saveSafeContent/saveSafeDeltaContent's own "publish immediately" branch writes the
  // new value straight onto the record instead of promoting it from draft_content in SQL -- so it
  // needs its own call to ContentRepository#snapshotBeforeDirectPublish, made BEFORE the record is
  // mutated to the new value. These tests prove that wiring, not snapshotBeforeDirectPublish's own
  // snapshot/prune/no-op logic -- that's covered directly against a real database in
  // ContentRepositoryTest.

  @Test
  void publishOfHtmlContentSnapshotsTheOutgoingContentBeforeOverwritingIt() throws DataException {
    Content existing = new Content();
    existing.setId(5L);
    existing.setUniqueId("uid");
    existing.setContent("<p>old content</p>");

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(existing);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
      repo.when(() -> ContentRepository.resolveVersionHistoryLimit(any())).thenReturn(20);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("content.versionHistoryLimit")).thenReturn("20");

      // The Content object is mutated in place, so capture its content at CALL time -- by the time
      // this test asserts, the same reference would already read the new, post-overwrite value.
      String[] snapshottedContent = new String[1];
      repo.when(() -> ContentRepository.snapshotBeforeDirectPublish(any(Content.class), eq(20)))
          .thenAnswer(invocation -> {
            snapshottedContent[0] = ((Content) invocation.getArgument(0)).getContent();
            return null;
          });

      Content saved = SaveContentCommand.saveSafeContent("uid", "<p>new content</p>", 7L, true);

      assertEquals("<p>old content</p>", snapshottedContent[0],
          "the snapshot must capture the OUTGOING content, taken before it's overwritten");
      assertEquals("<p>new content</p>", saved.getContent());
      repo.verify(() -> ContentRepository.snapshotBeforeDirectPublish(any(Content.class), eq(20)));
    }
  }

  @Test
  void draftSaveOfHtmlContentNeverSnapshots() throws DataException {
    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(null);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));

      SaveContentCommand.saveSafeContent("uid", "<p>draft</p>", 7L, false);

      repo.verify(() -> ContentRepository.snapshotBeforeDirectPublish(any(), anyInt()), never());
    }
  }

  @Test
  void publishOfDeltaContentSnapshotsTheOutgoingContentBeforeOverwritingIt() throws DataException {
    Content existing = new Content();
    existing.setId(9L);
    existing.setUniqueId("uid");
    existing.setContent("<p>old html content</p>");
    existing.setContentFormat(DeltaContentCommand.LEGACY_HTML_FORMAT);

    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(existing);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
      repo.when(() -> ContentRepository.resolveVersionHistoryLimit(any())).thenReturn(20);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("content.versionHistoryLimit")).thenReturn("20");

      String[] snapshottedContent = new String[1];
      repo.when(() -> ContentRepository.snapshotBeforeDirectPublish(any(Content.class), eq(20)))
          .thenAnswer(invocation -> {
            snapshottedContent[0] = ((Content) invocation.getArgument(0)).getContent();
            return null;
          });

      Content saved = SaveContentCommand.saveSafeDeltaContent("uid", VALID_DELTA, 7L, true);

      assertEquals("<p>old html content</p>", snapshottedContent[0],
          "a block that was legacy HTML on a prior cycle must still be snapshotted before this Delta publish overwrites it");
      assertEquals(VALID_DELTA, saved.getContent());
      repo.verify(() -> ContentRepository.snapshotBeforeDirectPublish(any(Content.class), eq(20)));
    }
  }

  @Test
  void draftSaveOfDeltaContentNeverSnapshots() throws DataException {
    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(null);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));

      SaveContentCommand.saveSafeDeltaContent("uid", VALID_DELTA, 7L, false);

      repo.verify(() -> ContentRepository.snapshotBeforeDirectPublish(any(), anyInt()), never());
    }
  }
}
