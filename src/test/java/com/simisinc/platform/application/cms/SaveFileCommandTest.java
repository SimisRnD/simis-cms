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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;

/**
 * Covers the folder extension-allowlist enforcement added for issue #370 -- centralized here so
 * every upload call site (admin drop zone, public drop zone, form widgets, etc.) gets it for free,
 * rather than each widget needing its own duplicated check.
 */
class SaveFileCommandTest {

  private FileItem newFileBean(long folderId, String extension) {
    FileItem bean = new FileItem();
    bean.setFolderId(folderId);
    bean.setFilename("upload." + extension);
    bean.setFileServerPath("/uploads/upload." + extension);
    bean.setExtension(extension);
    bean.setCreatedBy(1L);
    bean.setModifiedBy(1L);
    return bean;
  }

  @Test
  void saveFileRejectsAnExtensionNotOnTheFoldersAllowlist() {
    Folder folder = new Folder();
    folder.setId(5L);
    folder.setAllowedExtensions("jpg, png, gif");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(folder);

      FileItem bean = newFileBean(5, "html");
      DataException thrown = assertThrows(DataException.class, () -> SaveFileCommand.saveFile(bean));
      assertEquals("File type '.html' is not allowed in this folder", thrown.getMessage());
    }
  }

  @Test
  void saveFileAllowsAnExtensionOnTheFoldersAllowlist() throws DataException {
    Folder folder = new Folder();
    folder.setId(5L);
    folder.setAllowedExtensions("jpg, png, gif");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepository = mockStatic(FileItemRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      fileItemRepository.when(() -> FileItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      FileItem bean = newFileBean(5, "png");
      FileItem saved = SaveFileCommand.saveFile(bean);

      assertEquals("png", saved.getExtension());
    }
  }

  @Test
  void saveFileIsUnrestrictedWhenTheFolderHasNoAllowlistConfigured() throws DataException {
    Folder folder = new Folder();
    folder.setId(5L);
    folder.setAllowedExtensions(null);

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepository = mockStatic(FileItemRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      fileItemRepository.when(() -> FileItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      FileItem bean = newFileBean(5, "exe");
      FileItem saved = SaveFileCommand.saveFile(bean);

      assertEquals("exe", saved.getExtension());
    }
  }

  @Test
  void saveFileIsUnrestrictedWhenTheFolderCannotBeFound() throws DataException {
    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepository = mockStatic(FileItemRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(null);
      fileItemRepository.when(() -> FileItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      FileItem bean = newFileBean(5, "exe");
      FileItem saved = SaveFileCommand.saveFile(bean);

      assertEquals("exe", saved.getExtension());
    }
  }

  @Test
  void saveNewVersionOfFileRejectsAnExtensionNotOnTheFoldersAllowlist() {
    Folder folder = new Folder();
    folder.setId(5L);
    folder.setAllowedExtensions("pdf, docx");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepository = mockStatic(FileItemRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(folder);

      FileItem existing = newFileBean(5, "pdf");
      existing.setId(42L);
      fileItemRepository.when(() -> FileItemRepository.findById(42L)).thenReturn(existing);

      FileItem newVersion = newFileBean(5, "svg");
      newVersion.setId(42L);
      DataException thrown = assertThrows(DataException.class, () -> SaveFileCommand.saveNewVersionOfFile(newVersion));
      assertEquals("File type '.svg' is not allowed in this folder", thrown.getMessage());
    }
  }

  @Test
  void saveFileDoesNotRecheckTheAllowlistWhenOnlyRenamingAnExistingRecordWithTheSameExtension() throws DataException {
    // A folder's allowlist can be tightened after files already exist under it -- renaming one of
    // those legacy files (no new bytes, extension unchanged) must not suddenly start failing.
    Folder folder = new Folder();
    folder.setId(5L);
    folder.setAllowedExtensions("jpg"); // tightened since the existing file's "png" was allowed

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<FileItemRepository> fileItemRepository = mockStatic(FileItemRepository.class)) {
      folderRepository.when(() -> FolderRepository.findById(5L)).thenReturn(folder);

      FileItem existing = newFileBean(5, "png");
      existing.setId(42L);
      fileItemRepository.when(() -> FileItemRepository.findById(42L)).thenReturn(existing);
      fileItemRepository.when(() -> FileItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

      FileItem rename = new FileItem();
      rename.setId(42L);
      rename.setFolderId(5);
      rename.setFilename("renamed.png");
      rename.setModifiedBy(1L);

      FileItem saved = SaveFileCommand.saveFile(rename);
      assertEquals("renamed.png", saved.getFilename());
      folderRepository.verify(() -> FolderRepository.findById(eq(5L)), org.mockito.Mockito.never());
    }
  }
}
