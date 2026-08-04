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

package com.simisinc.platform.infrastructure.scheduler.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.cms.GenerateImageVariantsCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageVariant;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Verifies {@link ImageVariantJob.ImageVariantJobRequestHandler}'s control flow (issue #411):
 * {@link GenerateImageVariantsCommand} and {@link ImageRepository} are statically mocked, so
 * nothing here shells out to ImageMagick or touches a database.
 *
 * @author SimIS Inc.
 */
class ImageVariantJobTest {

  @Test
  void runGeneratesVariantsAndStampsTheImageAsProcessed() {
    Image image = new Image();
    image.setId(42L);

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<GenerateImageVariantsCommand> generateCommand = mockStatic(GenerateImageVariantsCommand.class)) {
      imageRepository.when(() -> ImageRepository.findById(42L)).thenReturn(image);
      generateCommand.when(() -> GenerateImageVariantsCommand.generateVariants(image))
          .thenReturn(List.of(new ImageVariant()));

      new ImageVariantJob.ImageVariantJobRequestHandler().run(new ImageVariantJob(42L));

      generateCommand.verify(() -> GenerateImageVariantsCommand.generateVariants(image));
      imageRepository.verify(() -> ImageRepository.save(argThat(saved -> {
        assertNotNull(saved.getProcessed(), "the image must be stamped as processed");
        return true;
      })));
    }
  }

  @Test
  void runDoesNothingWhenTheImageIsNotFound() {
    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<GenerateImageVariantsCommand> generateCommand = mockStatic(GenerateImageVariantsCommand.class)) {
      imageRepository.when(() -> ImageRepository.findById(99L)).thenReturn(null);

      new ImageVariantJob.ImageVariantJobRequestHandler().run(new ImageVariantJob(99L));

      generateCommand.verify(() -> GenerateImageVariantsCommand.generateVariants(any()), never());
      imageRepository.verify(() -> ImageRepository.save(any()), never());
    }
  }

  @Test
  void runStampsTheImageAsProcessedEvenWhenNoVariantMadeSense() {
    // A small original can legitimately produce zero variants (every target size is >= the
    // original) -- that must still count as "done being processed", not stay stuck reprocessing.
    Image image = new Image();
    image.setId(7L);

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<GenerateImageVariantsCommand> generateCommand = mockStatic(GenerateImageVariantsCommand.class)) {
      imageRepository.when(() -> ImageRepository.findById(7L)).thenReturn(image);
      generateCommand.when(() -> GenerateImageVariantsCommand.generateVariants(image)).thenReturn(List.of());

      new ImageVariantJob.ImageVariantJobRequestHandler().run(new ImageVariantJob(7L));

      imageRepository.verify(() -> ImageRepository.save(argThat(saved -> saved.getProcessed() != null)));
    }
  }

  @Test
  void constructorCapturesTheImageId() {
    ImageVariantJob job = new ImageVariantJob(123L);
    assertNotNull(job.getJobRequestHandler());
    assertEquals(123L, job.getImageId());
  }
}
