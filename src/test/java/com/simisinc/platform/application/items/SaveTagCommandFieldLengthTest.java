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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;

/**
 * Issue #1740 for tags. This command reports the first problem and stops, rather than accumulating
 * into a StringBuilder the way the folder and wiki commands do, so the length check throws in kind
 * -- and the test is here to make sure it throws at all, since a check written in the accumulating
 * style would have appended to a builder nothing ever reads.
 *
 * @author elizabeth houser
 */
class SaveTagCommandFieldLengthTest {

  private static Tag tagNamed(String name) {
    Tag bean = new Tag();
    bean.setCollectionId(1L);
    bean.setName(name);
    bean.setCreatedBy(42L);
    return bean;
  }

  @Test
  void overTheLimitIsRefusedByNameBeforeAnythingIsWritten() {
    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      DataException exception = assertThrows(DataException.class,
          () -> SaveTagCommand.saveTag(tagNamed("a".repeat(256))));
      assertTrue(exception.getMessage().contains("A name can be up to 255 characters"),
          "the admin has to be told the field and the number: " + exception.getMessage());
      repository.verify(() -> TagRepository.save(any()), never());
    }
  }

  @Test
  void exactlyAtTheLimitIsAccepted() throws DataException {
    try (MockedStatic<TagRepository> repository = mockStatic(TagRepository.class)) {
      repository.when(() -> TagRepository.findByNameWithinCollection(anyString(), anyLong()))
          .thenReturn(null);
      repository.when(() -> TagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      SaveTagCommand.saveTag(tagNamed("a".repeat(255)));
      repository.verify(() -> TagRepository.save(any()));
    }
  }
}
