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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.RestoreFileVersionCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the /admin/file-versions widget (#502): listing a file's prior uploaded versions and
 * restoring a selected one back to being the live file.
 */
class FileVersionsListWidgetTest extends WidgetBase {

  private static FileItem fileItem(long id, long folderId, String path) {
    FileItem record = new FileItem();
    record.setId(id);
    record.setFolderId(folderId);
    record.setSubFolderId(-1);
    record.setFileServerPath(path);
    record.setTitle("Handbook");
    return record;
  }

  private static FileVersion version(long id, long fileId, long folderId, String path) {
    FileVersion record = new FileVersion();
    record.setId(id);
    record.setFileId(fileId);
    record.setFolderId(folderId);
    record.setFileServerPath(path);
    return record;
  }

  private static Folder folder(long id) {
    Folder record = new Folder();
    record.setId(id);
    return record;
  }

  @Test
  void executeLoadsTheFileAndItsVersionsForAnAdmin() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "9");
    FileItem file = fileItem(9L, 3L, "/uploads/2026/08/02/current.pdf");
    FileVersion version = version(20L, 9L, 3L, "/uploads/2026/07/01/old.pdf");

    try (MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      fileRepo.when(() -> FileItemRepository.findById(9L)).thenReturn(file);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(List.of(version));
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().execute(widgetContext);

      assertEquals(file, result.getRequest().getAttribute("file"));
      assertEquals(List.of(version), result.getRequest().getAttribute("versionList"));
      assertEquals("true", result.getRequest().getAttribute("canRestore"));
    }
  }

  @Test
  void executeSetsAnErrorWhenTheFileIsNotFound() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "404");

    try (MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class)) {
      fileRepo.when(() -> FileItemRepository.findById(404L)).thenReturn(null);

      WidgetContext result = new FileVersionsListWidget().execute(widgetContext);

      assertEquals("Error. File was not found.", result.getErrorMessage());
    }
  }

  @Test
  void postRestoresTheSelectedVersion() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");
    FileVersion oldVersion = version(20L, 9L, 3L, "/uploads/2026/07/01/old.pdf");
    FileItem restored = fileItem(9L, 3L, "/uploads/2026/07/01/old.pdf");
    FileItem currentFile = fileItem(9L, 3L, "/uploads/2026/07/01/old.pdf");

    try (MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      versionRepo.when(() -> FileVersionRepository.findById(20L)).thenReturn(oldVersion);
      restoreCmd.when(() -> RestoreFileVersionCommand.restore(oldVersion, 1L)).thenReturn(restored);
      fileRepo.when(() -> FileItemRepository.findById(9L)).thenReturn(currentFile);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(oldVersion, 1L));
      assertEquals("The selected version is now the current file.", result.getSuccessMessage());
    }
  }

  @Test
  void postSetsAnErrorWhenTheVersionIsNotFound() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "404");
    FileItem currentFile = fileItem(9L, 3L, "/uploads/2026/07/01/old.pdf");

    try (MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      versionRepo.when(() -> FileVersionRepository.findById(404L)).thenReturn(null);
      fileRepo.when(() -> FileItemRepository.findById(9L)).thenReturn(currentFile);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertEquals("The selected version was not found", result.getErrorMessage());
      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(any(), anyLong()), never());
    }
  }

  @Test
  void postRejectsAVersionThatBelongsToADifferentFile() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");
    // This version actually belongs to file 55, not the file 9 the request claims
    FileVersion mismatchedVersion = version(20L, 55L, 3L, "/uploads/2026/07/01/old.pdf");
    FileItem currentFile = fileItem(9L, 3L, "/uploads/2026/07/01/old.pdf");

    try (MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      versionRepo.when(() -> FileVersionRepository.findById(20L)).thenReturn(mismatchedVersion);
      fileRepo.when(() -> FileItemRepository.findById(9L)).thenReturn(currentFile);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertEquals("The selected version was not found", result.getErrorMessage());
      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(any(), anyLong()), never());
    }
  }

  @Test
  void postRequiresFolderAddPermissionForNonAdmins() throws Exception {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");
    FileVersion oldVersion = version(20L, 9L, 3L, "/uploads/2026/07/01/old.pdf");
    FileItem currentFile = fileItem(9L, 3L, "/uploads/2026/07/01/old.pdf");

    try (MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<LoadFileCommand> loadFileCmd = mockStatic(LoadFileCommand.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      versionRepo.when(() -> FileVersionRepository.findById(20L)).thenReturn(oldVersion);
      perm.when(() -> CheckFolderPermissionCommand.userHasAddPermission(3L, 1L)).thenReturn(false);
      // Not an admin, so the execute() fallthrough authorizes the file lookup via the folder's user groups
      loadFileCmd.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(9L, 1L)).thenReturn(currentFile);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertEquals("You do not have permission to restore this file", result.getErrorMessage());
      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(any(), anyLong()), never());
    }
  }

  @Test
  void postIgnoresAnyActionOtherThanRestore() throws Exception {
    addQueryParameter(widgetContext, "action", "somethingElse");

    WidgetContext result = new FileVersionsListWidget().post(widgetContext);

    assertNull(result);
  }

  @Test
  void postIsRejectedWithoutAdminOrContentManagerRole() throws Exception {
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");

    try (MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class)) {
      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertNull(result);
      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(any(), anyLong()), never());
    }
  }

  @Test
  void restoreSurfacesADataExceptionAsAnErrorMessage() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");
    FileVersion oldVersion = version(20L, 9L, 3L, "/uploads/2026/07/01/missing.pdf");
    FileItem currentFile = fileItem(9L, 3L, "/uploads/2026/08/02/current.pdf");

    try (MockedStatic<FileVersionRepository> versionRepo = mockStatic(FileVersionRepository.class);
        MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class);
        MockedStatic<FileItemRepository> fileRepo = mockStatic(FileItemRepository.class);
        MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class)) {
      versionRepo.when(() -> FileVersionRepository.findById(20L)).thenReturn(oldVersion);
      restoreCmd.when(() -> RestoreFileVersionCommand.restore(oldVersion, 1L))
          .thenThrow(new DataException("The archived file could not be found on the server and cannot be restored"));
      fileRepo.when(() -> FileItemRepository.findById(9L)).thenReturn(currentFile);
      versionRepo.when(() -> FileVersionRepository.findAll(any(), any(DataConstraints.class)))
          .thenReturn(Collections.emptyList());
      folderRepo.when(() -> FolderRepository.findById(3L)).thenReturn(folder(3L));

      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertEquals("The archived file could not be found on the server and cannot be restored", result.getErrorMessage());
    }
  }

  @Test
  void postRestoreAcceptsCommunityManagerRoleWithFolderPermission() throws Exception {
    // community-manager alone must NOT be enough -- restore requires admin or content-manager,
    // matching FolderFilesListWidget#post's gate on "add a file version"
    setRoles(widgetContext, COMMUNITY_MANAGER);
    addQueryParameter(widgetContext, "action", "restore");
    addQueryParameter(widgetContext, "fileId", "9");
    addQueryParameter(widgetContext, "fileVersionId", "20");

    try (MockedStatic<RestoreFileVersionCommand> restoreCmd = mockStatic(RestoreFileVersionCommand.class)) {
      WidgetContext result = new FileVersionsListWidget().post(widgetContext);

      assertNull(result);
      restoreCmd.verify(() -> RestoreFileVersionCommand.restore(any(), anyLong()), never());
    }
  }
}
