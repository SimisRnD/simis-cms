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
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;

/**
 * Verifies restoring a folder file to an archived version (issue #502): the archived version's
 * physical file must still exist on disk (it is never deleted or overwritten while the file item
 * exists -- see FileSystemCommand#generateUniqueFilename and DeleteFileCommand), and restoring
 * re-uses SaveFileCommand#saveNewVersionOfFile with fields sourced from the archived FileVersion.
 */
class RestoreFileVersionCommandTest {

  private Path tempFile;

  @AfterEach
  void cleanup() throws IOException {
    if (tempFile != null) {
      Files.deleteIfExists(tempFile);
    }
  }

  private static FileVersion version(long id, long fileId, String path) {
    FileVersion record = new FileVersion();
    record.setId(id);
    record.setFileId(fileId);
    record.setFolderId(3L);
    record.setSubFolderId(-1L);
    record.setCategoryId(-1L);
    record.setFilename("handbook.pdf");
    record.setTitle("Handbook");
    record.setVersion("1.0");
    record.setExtension("pdf");
    record.setFileServerPath(path);
    record.setFileLength(1024L);
    record.setFileType("PDF");
    record.setMimeType("application/pdf");
    record.setFileHash("SHA-512;abc");
    record.setWidth(-1);
    record.setHeight(-1);
    record.setSummary("The handbook");
    return record;
  }

  @Test
  void restoreRejectsANullVersion() {
    DataException e = assertThrows(DataException.class, () -> RestoreFileVersionCommand.restore(null, 1L));
    assertEquals("The selected version was not specified", e.getMessage());
  }

  @Test
  void restoreRejectsAVersionWhoseFileIsMissingFromDisk() {
    FileVersion fileVersion = version(20L, 9L, "/uploads/2026/01/01/gone.pdf");

    try (MockedStatic<FileSystemCommand> fsCommand = mockStatic(FileSystemCommand.class);
        MockedStatic<SaveFileCommand> saveCommand = mockStatic(SaveFileCommand.class)) {
      fsCommand.when(FileSystemCommand::getFileServerRootPath).thenReturn("/opt/simis/files/");
      fsCommand.when(() -> FileSystemCommand.resolveWithinRoot("/opt/simis/files/", "/uploads/2026/01/01/gone.pdf"))
          .thenReturn(new File("/opt/simis/files/uploads/2026/01/01/gone.pdf"));

      DataException e = assertThrows(DataException.class, () -> RestoreFileVersionCommand.restore(fileVersion, 1L));

      assertEquals("The archived file could not be found on the server and cannot be restored", e.getMessage());
      saveCommand.verify(() -> SaveFileCommand.saveNewVersionOfFile(any()), never());
    }
  }

  @Test
  void restorePromotesTheArchivedVersionWhenItsFileExists() throws Exception {
    tempFile = Files.createTempFile("restore-file-version-test", ".pdf");
    FileVersion fileVersion = version(20L, 9L, "/uploads/2026/01/01/old.pdf");
    FileItem expected = new FileItem();
    expected.setId(9L);

    try (MockedStatic<FileSystemCommand> fsCommand = mockStatic(FileSystemCommand.class);
        MockedStatic<SaveFileCommand> saveCommand = mockStatic(SaveFileCommand.class)) {
      fsCommand.when(FileSystemCommand::getFileServerRootPath).thenReturn("/opt/simis/files/");
      fsCommand.when(() -> FileSystemCommand.resolveWithinRoot("/opt/simis/files/", "/uploads/2026/01/01/old.pdf"))
          .thenReturn(tempFile.toFile());
      saveCommand.when(() -> SaveFileCommand.saveNewVersionOfFile(any())).thenReturn(expected);

      FileItem result = RestoreFileVersionCommand.restore(fileVersion, 42L);

      assertEquals(expected, result);

      ArgumentCaptor<FileItem> captor = ArgumentCaptor.forClass(FileItem.class);
      saveCommand.verify(() -> SaveFileCommand.saveNewVersionOfFile(captor.capture()));
      FileItem submitted = captor.getValue();
      assertEquals(9L, submitted.getId());
      assertEquals(3L, submitted.getFolderId());
      assertEquals(-1L, submitted.getSubFolderId());
      assertEquals("handbook.pdf", submitted.getFilename());
      assertEquals("Handbook", submitted.getTitle());
      assertEquals("1.0", submitted.getVersion());
      assertEquals("pdf", submitted.getExtension());
      assertEquals("/uploads/2026/01/01/old.pdf", submitted.getFileServerPath());
      assertEquals(1024L, submitted.getFileLength());
      assertEquals("PDF", submitted.getFileType());
      assertEquals("application/pdf", submitted.getMimeType());
      assertEquals("SHA-512;abc", submitted.getFileHash());
      assertEquals("The handbook", submitted.getSummary());
      // The restoring user, not the version's original uploader, is recorded as this new save's actor
      assertEquals(42L, submitted.getCreatedBy());
      assertEquals(42L, submitted.getModifiedBy());
    }
  }
}
