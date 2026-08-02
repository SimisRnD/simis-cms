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

package com.simisinc.platform.application.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;

/**
 * Verifies {@link SaveTagCommand}'s validation and duplicate-name rules (issue #632), mirroring
 * {@code SaveCategoryCommand}'s own checks.
 *
 * @author SimIS Inc.
 */
class SaveTagCommandTest {

  private static Tag tagBean(long collectionId, String name, long createdBy) {
    Tag tag = new Tag();
    tag.setCollectionId(collectionId);
    tag.setName(name);
    tag.setCreatedBy(createdBy);
    return tag;
  }

  @Test
  void aMissingCollectionIdIsRejected() {
    Tag bean = tagBean(-1, "Fiction", 1);
    assertThrows(DataException.class, () -> SaveTagCommand.saveTag(bean));
  }

  @Test
  void aBlankNameIsRejected() {
    Tag bean = tagBean(5, "   ", 1);
    assertThrows(DataException.class, () -> SaveTagCommand.saveTag(bean));
  }

  @Test
  void aMissingCreatedByIsRejected() {
    Tag bean = tagBean(5, "Fiction", -1);
    assertThrows(DataException.class, () -> SaveTagCommand.saveTag(bean));
  }

  @Test
  void aDuplicateNameWithinTheSameCollectionIsRejectedOnInsert() {
    Tag bean = tagBean(5, "Fiction", 1);
    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      repository.when(() -> TagRepository.findByNameWithinCollection("Fiction", 5))
          .thenReturn(tagBean(5, "Fiction", 1));

      assertThrows(DataException.class, () -> SaveTagCommand.saveTag(bean));
      repository.verify(() -> TagRepository.save(any()), never());
    }
  }

  @Test
  void aNewTagWithAUniqueNameIsSaved() throws DataException {
    Tag bean = tagBean(5, "Fiction", 1);
    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      repository.when(() -> TagRepository.findByNameWithinCollection("Fiction", 5)).thenReturn(null);
      repository.when(() -> TagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      Tag saved = SaveTagCommand.saveTag(bean);

      assertEquals("Fiction", saved.getName());
      assertEquals(5, saved.getCollectionId());
      assertEquals(1, saved.getCreatedBy());
    }
  }

  @Test
  void editingAMissingRecordThrows() {
    Tag bean = tagBean(5, "Fiction", 1);
    bean.setId(99L);
    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      repository.when(() -> TagRepository.findById(99L)).thenReturn(null);

      assertThrows(DataException.class, () -> SaveTagCommand.saveTag(bean));
      repository.verify(() -> TagRepository.save(any()), never());
    }
  }

  @Test
  void editingAnExistingRecordDoesNotCheckForDuplicatesAgainstItself() throws DataException {
    Tag existing = tagBean(5, "Fiction", 1);
    existing.setId(1L);

    Tag bean = tagBean(5, "Fiction", 1);
    bean.setId(1L);

    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      repository.when(() -> TagRepository.findById(1L)).thenReturn(existing);
      repository.when(() -> TagRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      SaveTagCommand.saveTag(bean);

      repository.verify(() -> TagRepository.findByNameWithinCollection(any(), eq(5L)), never());
    }
  }
}
