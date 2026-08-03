/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;

/**
 * @author SimIS Inc.
 */
class FolderZipDownloadWidgetTest extends WidgetBase {

  private static class CapturingOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
      // not needed for this synchronous test double
    }

    @Override
    public void write(int b) {
      buffer.write(b);
    }

    byte[] toByteArray() {
      return buffer.toByteArray();
    }
  }

  private FileItem newFileItem(long id, long folderId, String filename, String fileServerPath) {
    FileItem file = new FileItem();
    file.setId(id);
    file.setFolderId(folderId);
    file.setSubFolderId(-1);
    file.setFilename(filename);
    file.setTitle(filename);
    file.setFileServerPath(fileServerPath);
    file.setFileType("document");
    return file;
  }

  private Map<String, byte[]> readZipEntries(byte[] zipBytes) throws Exception {
    Map<String, byte[]> entries = new HashMap<>();
    try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int count;
        while ((count = zis.read(buf)) >= 0) {
          out.write(buf, 0, count);
        }
        entries.put(entry.getName(), out.toByteArray());
        zis.closeEntry();
      }
    }
    return entries;
  }

  @Test
  void executeStreamsAZipWithTheFolderFilesAsEntries(@TempDir Path tempDir) throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "5");

    Files.write(tempDir.resolve("one.txt"), "one-content".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDir.resolve("two.txt"), "two-content".getBytes(StandardCharsets.UTF_8));

    Folder folder = new Folder();
    folder.setId(5L);
    folder.setName("My Folder");

    List<FileItem> fileList = new ArrayList<>();
    fileList.add(newFileItem(1L, 5L, "one.txt", "one.txt"));
    fileList.add(newFileItem(2L, 5L, "two.txt", "two.txt"));

    CapturingOutputStream out = new CapturingOutputStream();
    when(response.getOutputStream()).thenReturn(out);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), isNull())).thenReturn(fileList);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      FolderZipDownloadWidget widget = new FolderZipDownloadWidget();
      org.junit.jupiter.api.Assertions.assertNotNull(widget.execute(widgetContext));
    }

    assertTrue(widgetContext.handledResponse());
    verify(response).setHeader(eq("Content-Disposition"), org.mockito.ArgumentMatchers.contains("My Folder.zip"));
    verify(response).setContentType("application/zip");

    Map<String, byte[]> entries = readZipEntries(out.toByteArray());
    assertEquals(2, entries.size());
    assertTrue(entries.containsKey("one.txt"));
    assertTrue(entries.containsKey("two.txt"));
    assertEquals("one-content", new String(entries.get("one.txt"), StandardCharsets.UTF_8));
    assertEquals("two-content", new String(entries.get("two.txt"), StandardCharsets.UTF_8));
  }

  @Test
  void executeSkipsAFileMissingFromDiskWithoutFailingTheWholeZip(@TempDir Path tempDir) throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "5");

    Files.write(tempDir.resolve("present.txt"), "present-content".getBytes(StandardCharsets.UTF_8));
    // "missing.txt" is intentionally never created on disk

    Folder folder = new Folder();
    folder.setId(5L);
    folder.setName("My Folder");

    List<FileItem> fileList = new ArrayList<>();
    fileList.add(newFileItem(1L, 5L, "present.txt", "present.txt"));
    fileList.add(newFileItem(2L, 5L, "missing.txt", "missing.txt"));

    CapturingOutputStream out = new CapturingOutputStream();
    when(response.getOutputStream()).thenReturn(out);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), isNull())).thenReturn(fileList);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      FolderZipDownloadWidget widget = new FolderZipDownloadWidget();
      widget.execute(widgetContext);
    }

    assertTrue(widgetContext.handledResponse());
    Map<String, byte[]> entries = readZipEntries(out.toByteArray());
    // Only the file that actually exists on disk is present -- a partial zip, not an aborted response
    assertEquals(1, entries.size());
    assertTrue(entries.containsKey("present.txt"));
    assertFalse(entries.containsKey("missing.txt"));
  }

  @Test
  void executeReturnsNullWhenTheFolderIsNotFoundOrUnauthorized() {
    // No roles set -- a non-admin user without folder access
    addQueryParameter(widgetContext, "folderId", "5");

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByIdForAuthorizedUser(5L, widgetContext.getUserId())).thenReturn(null);

      FolderZipDownloadWidget widget = new FolderZipDownloadWidget();
      assertNull(widget.execute(widgetContext));

      fileItemRepo.verifyNoInteractions();
    }
  }

  @Test
  void executeDedupesCollidingEntryNames(@TempDir Path tempDir) throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "5");

    Files.createDirectories(tempDir.resolve("a"));
    Files.createDirectories(tempDir.resolve("b"));
    Files.write(tempDir.resolve("a/same.txt"), "first".getBytes(StandardCharsets.UTF_8));
    Files.write(tempDir.resolve("b/same.txt"), "second".getBytes(StandardCharsets.UTF_8));

    Folder folder = new Folder();
    folder.setId(5L);
    folder.setName("My Folder");

    List<FileItem> fileList = new ArrayList<>();
    fileList.add(newFileItem(1L, 5L, "same.txt", "a/same.txt"));
    fileList.add(newFileItem(2L, 5L, "same.txt", "b/same.txt"));

    CapturingOutputStream out = new CapturingOutputStream();
    when(response.getOutputStream()).thenReturn(out);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(FileSpecification.class), isNull())).thenReturn(fileList);
      fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");

      FolderZipDownloadWidget widget = new FolderZipDownloadWidget();
      widget.execute(widgetContext);
    }

    Map<String, byte[]> entries = readZipEntries(out.toByteArray());
    assertEquals(2, entries.size());
    assertTrue(entries.containsKey("same.txt"));
    assertTrue(entries.containsKey("same (2).txt"));
  }
}
