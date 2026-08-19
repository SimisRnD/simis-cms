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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Verifies {@link ImageHashJob.ImageHashJobRequestHandler}'s control flow (the "Scan for
 * Duplicates" backfill) -- {@link ImageRepository} is statically mocked and {@link
 * FileSystemCommand} is stubbed to a real temp-dir file, so this exercises the real checksum
 * computation without touching a database.
 *
 * @author SimIS Inc.
 */
class ImageHashJobTest {

  @Test
  void runComputesAndSavesTheFileHash(@TempDir Path tempDir) throws Exception {
    File imageFile = tempDir.resolve("photo.jpg").toFile();
    Files.write(imageFile.toPath(), "the-image-bytes".getBytes());

    Image image = new Image();
    image.setId(42L);
    image.setFileServerPath("images/2026/08/photo.jpg");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findById(42L)).thenReturn(image);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString())).thenReturn(imageFile);
      fsc.when(() -> FileSystemCommand.getFileChecksum(imageFile)).thenCallRealMethod();

      new ImageHashJob.ImageHashJobRequestHandler().run(new ImageHashJob(42L));

      imageRepository.verify(() -> ImageRepository.save(argThat(saved -> {
        assertNotNull(saved.getFileHash());
        assertEquals(0, saved.getFileHash().indexOf("SHA-512;"));
        return true;
      })));
    }
  }

  @Test
  void runDoesNothingWhenTheImageIsNotFound() {
    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class)) {
      imageRepository.when(() -> ImageRepository.findById(99L)).thenReturn(null);

      new ImageHashJob.ImageHashJobRequestHandler().run(new ImageHashJob(99L));

      imageRepository.verify(() -> ImageRepository.save(any()), never());
    }
  }

  @Test
  void runDoesNothingWhenTheFileIsMissingFromDisk(@TempDir Path tempDir) {
    Image image = new Image();
    image.setId(7L);
    image.setFileServerPath("images/2026/08/gone.jpg");

    try (MockedStatic<ImageRepository> imageRepository = mockStatic(ImageRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class)) {
      imageRepository.when(() -> ImageRepository.findById(7L)).thenReturn(image);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
      fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenReturn(tempDir.resolve("gone.jpg").toFile());

      new ImageHashJob.ImageHashJobRequestHandler().run(new ImageHashJob(7L));

      imageRepository.verify(() -> ImageRepository.save(any()), never());
    }
  }

  @Test
  void constructorCapturesTheImageId() {
    ImageHashJob job = new ImageHashJob(123L);
    assertNotNull(job.getJobRequestHandler());
    assertEquals(123L, job.getImageId());
  }
}
