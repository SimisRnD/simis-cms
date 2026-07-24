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
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
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
    try (MockedStatic<ContentRepository> repo = mockStatic(ContentRepository.class)) {
      repo.when(() -> ContentRepository.findByUniqueId("uid")).thenReturn(null);
      repo.when(() -> ContentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));

      Content saved = SaveContentCommand.saveSafeDeltaContent("uid", VALID_DELTA, 7L, true);

      assertEquals(VALID_DELTA, saved.getContent());
      assertEquals(DeltaContentCommand.DELTA_FORMAT_VERSION, saved.getContentFormat());
      assertNull(saved.getDraftContent());
      assertEquals(DeltaContentCommand.LEGACY_HTML_FORMAT, saved.getDraftContentFormat());
    }
  }
}
