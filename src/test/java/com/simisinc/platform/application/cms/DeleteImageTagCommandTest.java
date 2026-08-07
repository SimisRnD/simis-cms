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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.persistence.cms.ImageTagRepository;

/**
 * Verifies {@link DeleteImageTagCommand}, mirroring items' DeleteTagCommandTest.
 *
 * @author SimIS Inc.
 */
class DeleteImageTagCommandTest {

  @Test
  void aNullTagIsRejected() {
    assertThrows(DataException.class, () -> DeleteImageTagCommand.deleteImageTag(null));
  }

  @Test
  void aTagWithNoIdIsRejected() {
    ImageTag tag = new ImageTag();
    assertThrows(DataException.class, () -> DeleteImageTagCommand.deleteImageTag(tag));
  }

  @Test
  void anExistingTagIsRemovedViaTheRepository() throws DataException {
    ImageTag tag = new ImageTag();
    tag.setId(5L);

    try (MockedStatic<ImageTagRepository> repository = mockStatic(ImageTagRepository.class)) {
      repository.when(() -> ImageTagRepository.remove(tag)).thenReturn(true);

      boolean result = DeleteImageTagCommand.deleteImageTag(tag);

      assertTrue(result);
      repository.verify(() -> ImageTagRepository.remove(tag));
    }
  }
}
