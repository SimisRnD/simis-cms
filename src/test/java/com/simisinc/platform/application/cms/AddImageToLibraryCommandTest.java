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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageVariantJob;

/**
 * Covers {@link AddImageToLibraryCommand#addFromFile}, the entry point issue #1197 added so the
 * folder drop zone can put an uploaded image into the image library -- the store the image picker on
 * Site Settings / Theme Settings actually reads, and which folder uploads had no route into.
 *
 * <p>The library must end up owning its own copy of the bytes. A record pointing at the folder
 * file's copy would mean deleting either one silently breaks the other, so the source file being
 * left intact is asserted, not assumed.
 *
 * <p>{@link FileSystemCommand}, {@link LoadSitePropertyCommand}, {@link ValidateImageCommand},
 * {@link SaveImageCommand}, and {@link BackgroundJobRequest} are statically mocked, so the only real
 * file I/O is the copy under test.
 *
 * @author SimIS Inc.
 */
class AddImageToLibraryCommandTest {

  private static final String SUB_PATH = "images/2026/08/14/";
  private static final String UNIQUE_NAME = "unique-name";

  private void stubFileSystemCommand(MockedStatic<FileSystemCommand> fsc, Path root, File destination) {
    fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
    fsc.when(() -> FileSystemCommand.generateFileServerSubPath(anyString())).thenReturn(SUB_PATH);
    fsc.when(() -> FileSystemCommand.generateUniqueFilename(anyLong())).thenReturn(UNIQUE_NAME);
    fsc.when(() -> FileSystemCommand.cleanExtension(anyString())).thenAnswer(inv -> inv.getArgument(0));
    fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString())).thenReturn(destination);
  }

  private File writeSourceFile(Path dir, String contents) throws Exception {
    File sourceFile = dir.resolve("logo.png").toFile();
    Files.write(sourceFile.toPath(), contents.getBytes(StandardCharsets.UTF_8));
    return sourceFile;
  }

  @Test
  void addFromFileCopiesTheBytesAndLeavesTheSourceFileInPlace(@TempDir Path tempDir) throws Exception {
    File sourceFile = writeSourceFile(tempDir, "the-logo-bytes");
    File destination = tempDir.resolve("library-copy.png").toFile();

    Image savedImage = new Image();
    savedImage.setId(42L);

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir, destination);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenReturn(savedImage);

      Image result = AddImageToLibraryCommand.addFromFile(sourceFile, "logo.png", 1L);

      assertNotNull(result);
      assertTrue(destination.isFile(), "The library must get its own copy of the bytes");
      assertArrayEquals(Files.readAllBytes(sourceFile.toPath()), Files.readAllBytes(destination.toPath()));
      assertTrue(sourceFile.isFile(), "The folder file's own copy must be left untouched");

      // The record stores the root-relative path, so the file is still found when the file server
      // root moves (a re-deploy, a different mount point)
      save.verify(() -> SaveImageCommand.saveImage(argThat((Image image) -> {
        assertEquals(SUB_PATH + UNIQUE_NAME + ".png", image.getFileServerPath());
        assertEquals("logo.png", image.getFilename());
        assertEquals(sourceFile.length(), image.getFileLength());
        assertEquals(1L, image.getCreatedBy());
        return true;
      })));
      // Issue #411: srcset variants are generated for library images, so an image added this way is
      // not a second-class one
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((ImageVariantJob job) -> job.getImageId() == 42L)));
    }
  }

  @Test
  void addFromFileDeletesTheCopyWhenTheRecordCannotBeSaved(@TempDir Path tempDir) throws Exception {
    File sourceFile = writeSourceFile(tempDir, "the-logo-bytes");
    File destination = tempDir.resolve("library-copy.png").toFile();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir, destination);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenThrow(new DataException("Save failed"));

      assertThrows(DataException.class, () -> AddImageToLibraryCommand.addFromFile(sourceFile, "logo.png", 1L));

      // No record points at the copy, so nothing could ever reach it -- leaving it is pure leaked disk
      assertFalse(destination.exists(), "The copy must be removed when no record ends up pointing at it");
      assertTrue(sourceFile.isFile(), "A failed library add must not take the folder file's bytes with it");
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void addFromFileRejectsAnImageThatIsNotAReadableImage(@TempDir Path tempDir) throws Exception {
    File sourceFile = writeSourceFile(tempDir, "not-really-an-image");
    File destination = tempDir.resolve("library-copy.png").toFile();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir, destination);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      validate.when(() -> ValidateImageCommand.checkFile(any(Image.class)))
          .thenThrow(new DataException("Could not determine image type"));

      DataException thrown = assertThrows(DataException.class,
          () -> AddImageToLibraryCommand.addFromFile(sourceFile, "logo.png", 1L));
      assertEquals("Could not determine image type", thrown.getMessage());

      assertFalse(destination.exists());
      save.verify(() -> SaveImageCommand.saveImage(any(Image.class)), never());
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void addFromFileRejectsAFileThatIsMissing(@TempDir Path tempDir) {
    File missing = tempDir.resolve("gone.png").toFile();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      assertThrows(DataException.class, () -> AddImageToLibraryCommand.addFromFile(missing, "gone.png", 1L));

      save.verify(() -> SaveImageCommand.saveImage(any(Image.class)), never());
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void addFromFileRejectsAFileLargerThanTheUploadLimit(@TempDir Path tempDir) throws Exception {
    File sourceFile = writeSourceFile(tempDir, "the-logo-bytes");
    File destination = tempDir.resolve("library-copy.png").toFile();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir, destination);
      // The folder drop zone allows a larger file than the image library's own cap, so the cap is
      // re-checked here rather than trusted from the caller
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("4");

      DataException thrown = assertThrows(DataException.class,
          () -> AddImageToLibraryCommand.addFromFile(sourceFile, "logo.png", 1L));
      assertEquals("The file exceeds the maximum allowed upload size of 1 MB", thrown.getMessage());

      assertFalse(destination.exists(), "An oversized file must be rejected before any bytes are copied");
      save.verify(() -> SaveImageCommand.saveImage(any(Image.class)), never());
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }
}
