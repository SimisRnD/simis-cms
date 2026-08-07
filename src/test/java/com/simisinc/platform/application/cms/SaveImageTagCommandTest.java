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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;

/**
 * Verifies {@link SaveImageTagCommand}'s find-or-create behavior, used by the "new tag" field in
 * the tag-assignment modal on /admin/images.
 *
 * @author SimIS Inc.
 */
class SaveImageTagCommandTest {

  @Test
  void aBlankNameIsRejected() {
    assertThrows(DataException.class, () -> SaveImageTagCommand.saveImageTag("   ", 1));
  }

  @Test
  void anExistingTagWithThatNameIsReturnedWithoutSavingANewOne() throws DataException {
    ImageTag existing = new ImageTag();
    existing.setId(5L);
    existing.setName("Homepage");

    try (MockedStatic<ImageTagRepository> repository = mockStatic(ImageTagRepository.class)) {
      repository.when(() -> ImageTagRepository.findByName("Homepage")).thenReturn(existing);

      ImageTag result = SaveImageTagCommand.saveImageTag("Homepage", 1);

      assertEquals(5L, result.getId());
      repository.verify(() -> ImageTagRepository.save(any()), never());
    }
  }

  @Test
  void aNewNameCreatesAndSavesATag() throws DataException {
    try (MockedStatic<ImageTagRepository> repository = mockStatic(ImageTagRepository.class)) {
      repository.when(() -> ImageTagRepository.findByName("Homepage")).thenReturn(null);
      repository.when(() -> ImageTagRepository.save(any())).thenAnswer(i -> {
        ImageTag arg = i.getArgument(0);
        arg.setId(7L);
        return arg;
      });

      ImageTag result = SaveImageTagCommand.saveImageTag("Homepage", 1);

      assertEquals("Homepage", result.getName());
      assertEquals(7L, result.getId());
    }
  }

  @Test
  void aSaveFailureThrowsWhenNoConcurrentTagWasCreatedEither() {
    try (MockedStatic<ImageTagRepository> repository = mockStatic(ImageTagRepository.class)) {
      // Both the initial check and the post-failure re-check see nothing -- a genuine save
      // failure, not a race with a concurrent creator.
      repository.when(() -> ImageTagRepository.findByName("Homepage")).thenReturn(null);
      repository.when(() -> ImageTagRepository.save(any())).thenReturn(null);

      assertThrows(DataException.class, () -> SaveImageTagCommand.saveImageTag("Homepage", 1));
    }
  }

  @Test
  void aConcurrentCreatorWinningTheRaceIsUsedInsteadOfThrowing() throws DataException {
    // Regression test: two admins create the same new tag name at once. The first findByName()
    // check sees nothing (both requests pass it); this request's own save() then fails because
    // the other request's insert already committed and won the unique-index race. Rather than
    // hard-failing, a second findByName() must find and return what the winner created.
    ImageTag createdByTheOtherRequest = new ImageTag();
    createdByTheOtherRequest.setId(9L);
    createdByTheOtherRequest.setName("Homepage");

    try (MockedStatic<ImageTagRepository> repository = mockStatic(ImageTagRepository.class)) {
      repository.when(() -> ImageTagRepository.findByName("Homepage"))
          .thenReturn(null, createdByTheOtherRequest);
      repository.when(() -> ImageTagRepository.save(any())).thenReturn(null);

      ImageTag result = SaveImageTagCommand.saveImageTag("Homepage", 1);

      assertEquals(9L, result.getId());
    }
  }
}
