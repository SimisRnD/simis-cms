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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;

import java.util.Collections;
import java.util.List;

import org.jobrunr.scheduling.BackgroundJobRequest;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageHashJob;

/**
 * Verifies {@link ScanForDuplicateImagesCommand#startScan}'s enqueue-one-job-per-image control
 * flow (the "Scan for Duplicates" backfill) -- {@link ImageRepository} and
 * {@link BackgroundJobRequest} are statically mocked, so this never touches a database or JobRunr.
 *
 * @author SimIS Inc.
 */
class ScanForDuplicateImagesCommandTest {

  @Test
  void startScanEnqueuesOneJobPerUnhashedImageAndReturnsTheCount() {
    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepository.when(ImageRepository::findAllUnhashed).thenReturn(List.of(1L, 2L, 3L));

      int enqueued = ScanForDuplicateImagesCommand.startScan();

      assertEquals(3, enqueued);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((ImageHashJob job) -> job.getImageId() == 1L)));
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((ImageHashJob job) -> job.getImageId() == 2L)));
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((ImageHashJob job) -> job.getImageId() == 3L)));
    }
  }

  @Test
  void startScanEnqueuesNothingWhenEveryImageIsAlreadyHashed() {
    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      imageRepository.when(ImageRepository::findAllUnhashed).thenReturn(Collections.emptyList());

      int enqueued = ScanForDuplicateImagesCommand.startScan();

      assertEquals(0, enqueued);
      jobRequest.verifyNoInteractions();
    }
  }
}
