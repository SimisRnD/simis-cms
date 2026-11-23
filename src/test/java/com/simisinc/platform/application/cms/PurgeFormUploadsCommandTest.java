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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;

/**
 * Verifies the form upload cleanup removes only what nothing needs, and never reaches outside
 * forms/ (issue #2130).
 */
class PurgeFormUploadsCommandTest {

  private static final Instant NOW = Instant.now();
  private static final Instant CUTOFF = NOW.minus(Duration.ofDays(1));

  @TempDir
  Path root;

  @TempDir
  Path elsewhere;

  private MockedStatic<FileSystemCommand> fileSystem;
  private MockedStatic<FormUploadRepository> repository;

  @BeforeEach
  void setUp() {
    fileSystem = mockStatic(FileSystemCommand.class, Mockito.CALLS_REAL_METHODS);
    fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
    repository = mockStatic(FormUploadRepository.class);
    repository.when(() -> FormUploadRepository.remove(any())).thenReturn(true);
  }

  @AfterEach
  void tearDown() {
    repository.close();
    fileSystem.close();
  }

  /** A file under the root, last modified {@code daysAgo} days ago */
  private Path file(String relative, int daysAgo) throws IOException {
    Path path = root.resolve(relative);
    Files.createDirectories(path.getParent());
    Files.write(path, new byte[] { 1 });
    Files.setLastModifiedTime(path, FileTime.from(NOW.minus(Duration.ofDays(daysAgo))));
    return path;
  }

  private static FormUpload upload(long id, String filePath, String thumbnailPath) {
    FormUpload upload = new FormUpload();
    upload.setId(id);
    upload.setFilePath(filePath);
    upload.setThumbnailPath(thumbnailPath);
    return upload;
  }

  @Test
  void anUnlinkedUploadLosesItsRecordAndThenItsFiles() throws IOException {
    Path image = file("forms/contact-us/a.png", 30);
    Path thumbnail = file("forms/contact-us/a-thumbnail.png", 30);
    FormUpload upload = upload(1, "forms/contact-us/a.png", "forms/contact-us/a-thumbnail.png");
    repository.when(() -> FormUploadRepository.findUnlinkedCreatedBefore(any())).thenReturn(List.of(upload));

    assertEquals(1, PurgeFormUploadsCommand.removeUnlinkedUploads(CUTOFF));

    repository.verify(() -> FormUploadRepository.remove(upload));
    assertFalse(Files.exists(image));
    assertFalse(Files.exists(thumbnail));
  }

  @Test
  void whenTheRecordCannotBeRemovedItsFilesAreKept() throws IOException {
    Path image = file("forms/contact-us/a.png", 30);
    FormUpload upload = upload(1, "forms/contact-us/a.png", null);
    repository.when(() -> FormUploadRepository.findUnlinkedCreatedBefore(any())).thenReturn(List.of(upload));
    repository.when(() -> FormUploadRepository.remove(upload)).thenReturn(false);

    assertEquals(0, PurgeFormUploadsCommand.removeUnlinkedUploads(CUTOFF));

    assertTrue(Files.exists(image), "a record must never be left pointing at files that are gone");
  }

  @Test
  void aRecordWhosePathLeavesTheRootDeletesNothingOutsideIt() throws IOException {
    Path outside = elsewhere.resolve("keep.png");
    Files.write(outside, new byte[] { 1 });
    String escape = root.relativize(outside).toString();
    repository.when(() -> FormUploadRepository.findUnlinkedCreatedBefore(any())).thenReturn(List.of(upload(1, escape, null)));

    PurgeFormUploadsCommand.removeUnlinkedUploads(CUTOFF);

    assertTrue(Files.exists(outside));
  }

  @Test
  void theSweepDeletesOldFilesNoRecordNamesAndKeepsTheRest() throws IOException {
    Path referenced = file("forms/contact-us/kept.png", 30);
    Path referencedThumbnail = file("forms/contact-us/kept-thumbnail.png", 30);
    Path orphan = file("forms/contact-us/orphan.png", 30);
    Path quarantine = file("forms/quarantine/left-behind.upload", 3);
    Path recent = file("forms/contact-us/just-written.png", 0);
    Path otherModule = file("images/2026/11/23/old.png", 30);
    repository.when(FormUploadRepository::findAllStoredPaths)
        .thenReturn(Set.of("forms/contact-us/kept.png", "forms/contact-us/kept-thumbnail.png"));

    assertEquals(2, PurgeFormUploadsCommand.deleteUnreferencedFiles(CUTOFF));

    assertTrue(Files.exists(referenced));
    assertTrue(Files.exists(referencedThumbnail));
    assertFalse(Files.exists(orphan));
    assertFalse(Files.exists(quarantine), "a quarantine copy a crash left behind");
    assertTrue(Files.exists(recent), "may belong to a submission still being saved");
    assertTrue(Files.exists(otherModule), "only forms/ is swept");
  }

  @Test
  void whenTheStoredPathsCannotBeReadNothingIsDeleted() throws IOException {
    Path file = file("forms/contact-us/a.png", 30);
    repository.when(FormUploadRepository::findAllStoredPaths).thenReturn(null);

    assertEquals(0, PurgeFormUploadsCommand.deleteUnreferencedFiles(CUTOFF));

    assertTrue(Files.exists(file), "unknown is not the same as unreferenced");
  }

  @Test
  void theSweepDoesNotFollowSymbolicLinks() throws IOException {
    Path target = elsewhere.resolve("old.png");
    Files.write(target, new byte[] { 1 });
    Files.setLastModifiedTime(target, FileTime.from(NOW.minus(Duration.ofDays(30))));
    Files.createDirectories(root.resolve("forms"));
    Files.createSymbolicLink(root.resolve("forms/linked-dir"), elsewhere);
    Files.createSymbolicLink(root.resolve("forms/linked-file.png"), target);
    repository.when(FormUploadRepository::findAllStoredPaths).thenReturn(Set.of());

    PurgeFormUploadsCommand.deleteUnreferencedFiles(CUTOFF);

    assertTrue(Files.exists(target), "nothing outside forms/ is reached through a link");
  }

  @Test
  void aFormsDirectoryThatIsItselfALinkIsNotSwept() throws IOException {
    Path target = elsewhere.resolve("old.png");
    Files.write(target, new byte[] { 1 });
    Files.setLastModifiedTime(target, FileTime.from(NOW.minus(Duration.ofDays(30))));
    Files.createSymbolicLink(root.resolve("forms"), elsewhere);
    repository.when(FormUploadRepository::findAllStoredPaths).thenReturn(Set.of());

    assertEquals(0, PurgeFormUploadsCommand.deleteUnreferencedFiles(CUTOFF));
    assertTrue(Files.exists(target));
  }

  @Test
  void withNoFormsDirectoryThereIsNothingToSweep() {
    repository.when(FormUploadRepository::findAllStoredPaths).thenReturn(Set.of());

    assertEquals(0, PurgeFormUploadsCommand.deleteUnreferencedFiles(CUTOFF));
    repository.verify(FormUploadRepository::findAllStoredPaths, Mockito.never());
  }
}
