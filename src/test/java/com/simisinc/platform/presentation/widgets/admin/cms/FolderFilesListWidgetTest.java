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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteFileCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;

/**
 * @author SimIS Inc.
 */
class FolderFilesListWidgetTest extends WidgetBase {

  private FileItem newFileItem(long id, long folderId, long subFolderId) {
    FileItem file = new FileItem();
    file.setId(id);
    file.setFolderId(folderId);
    file.setSubFolderId(subFolderId);
    file.setFilename("file-" + id + ".txt");
    file.setTitle("file-" + id + ".txt");
    return file;
  }

  @Test
  void bulkDeletePostRemovesOnlyTheSelectedFileIds() throws Exception {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1", "3" });
    addQueryParameter(widgetContext, "currentFolderId", "5");
    addQueryParameter(widgetContext, "currentSubFolderId", "-1");

    Folder folder = new Folder();
    folder.setId(5L);

    FileItem file1 = newFileItem(1L, 5L, -1);
    FileItem file3 = newFileItem(3L, 5L, -1);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      loadFile.when(() -> LoadFileCommand.loadItemById(1L)).thenReturn(file1);
      loadFile.when(() -> LoadFileCommand.loadItemById(3L)).thenReturn(file3);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(any(FileItem.class))).thenReturn(true);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verify(() -> DeleteFileCommand.deleteFile(file1));
      deleteFile.verify(() -> DeleteFileCommand.deleteFile(file3));
      // file id 2 was never selected/present -- it must never even be looked up
      loadFile.verify(() -> LoadFileCommand.loadItemById(2L), never());
    }

    assertEquals("2 of 2 selected files deleted.", widgetContext.getSuccessMessage());
    assertEquals("/admin/folder-details?folderId=5", widgetContext.getRedirect());
  }

  @Test
  void bulkDeletePreservesTheSubFolderOnRedirect() throws Exception {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1" });
    addQueryParameter(widgetContext, "currentFolderId", "5");
    addQueryParameter(widgetContext, "currentSubFolderId", "9");

    Folder folder = new Folder();
    folder.setId(5L);

    FileItem file1 = newFileItem(1L, 5L, 9L);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      loadFile.when(() -> LoadFileCommand.loadItemById(1L)).thenReturn(file1);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(any(FileItem.class))).thenReturn(true);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);
    }

    assertEquals("/admin/sub-folder-details?folderId=5&subFolderId=9", widgetContext.getRedirect());
  }

  @Test
  void bulkDeleteRejectsASelectionLargerThanTheMax() throws Exception {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    addQueryParameter(widgetContext, "currentFolderId", "5");
    String[] tooMany = new String[FolderFilesListWidget.MAX_BULK_SELECTION + 1];
    for (int i = 0; i < tooMany.length; i++) {
      tooMany[i] = String.valueOf(i + 1);
    }
    widgetContext.getParameterMap().put("fileId", tooMany);

    Folder folder = new Folder();
    folder.setId(5L);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verifyNoInteractions();
    }
    assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void bulkDeleteWithoutFolderDeletePermissionNeverCallsDeleteFileCommand() throws Exception {
    // Default logged-in test user has no roles at all -- neither admin nor content-manager, and
    // no folder_groups delete_permission grant
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1" });
    addQueryParameter(widgetContext, "currentFolderId", "5");

    Folder folder = new Folder();
    folder.setId(5L);

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> checkPermission = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByIdForAuthorizedUser(5L, widgetContext.getUserId())).thenReturn(folder);
      checkPermission.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(5L, widgetContext.getUserId())).thenReturn(false);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verifyNoInteractions();
    }
    assertNotNull(widgetContext.getErrorMessage());
  }

  @Test
  void bulkDeleteSkipsAFileIdBelongingToADifferentFolder() throws Exception {
    // A file id for a folder other than currentFolderId must not be deletable through this batch,
    // even though the id itself resolves to a real record
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1" });
    addQueryParameter(widgetContext, "currentFolderId", "5");

    Folder folder = new Folder();
    folder.setId(5L);

    FileItem fileInOtherFolder = newFileItem(1L, 99L, -1);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      loadFile.when(() -> LoadFileCommand.loadItemById(1L)).thenReturn(fileInOtherFolder);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verifyNoInteractions();
    }
    assertEquals("0 of 1 selected file deleted. 1 were already gone.", widgetContext.getErrorMessage());
  }

  @Test
  void bulkDeletePartialFailureDoesNotClaimFullSuccessForTheWholeBatch() throws Exception {
    // One file already gone (loadItemById returns null -- e.g. deleted by another admin between
    // page render and this submit), one file deletes successfully
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1", "2" });
    addQueryParameter(widgetContext, "currentFolderId", "5");

    Folder folder = new Folder();
    folder.setId(5L);

    FileItem file1 = newFileItem(1L, 5L, -1);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      loadFile.when(() -> LoadFileCommand.loadItemById(1L)).thenReturn(file1);
      loadFile.when(() -> LoadFileCommand.loadItemById(2L)).thenReturn(null);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(file1)).thenReturn(true);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verify(() -> DeleteFileCommand.deleteFile(file1));
    }

    // Must not claim the whole batch succeeded -- exactly one of two, and the other's fate is stated
    assertEquals("1 of 2 selected files deleted. 1 were already gone.", widgetContext.getSuccessMessage());
  }

  @Test
  void bulkDeleteWhereEverySelectedFileFailsSetsAnErrorNotASuccessMessage() throws Exception {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    widgetContext.getParameterMap().put("fileId", new String[] { "1", "2" });
    addQueryParameter(widgetContext, "currentFolderId", "5");

    Folder folder = new Folder();
    folder.setId(5L);

    FileItem file1 = newFileItem(1L, 5L, -1);
    FileItem file2 = newFileItem(2L, 5L, -1);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);
      loadFile.when(() -> LoadFileCommand.loadItemById(1L)).thenReturn(file1);
      loadFile.when(() -> LoadFileCommand.loadItemById(2L)).thenReturn(file2);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(file1)).thenReturn(false);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(file2)).thenThrow(new DataException("disk error"));

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);
    }

    assertEquals("0 of 2 selected files deleted. 2 could not be deleted.", widgetContext.getErrorMessage());
    assertNull(widgetContext.getSuccessMessage());
  }

  @Test
  void bulkDeleteWithNoFilesSelectedSetsAnErrorMessage() throws Exception {
    setRoles(widgetContext, ADMIN);
    widgetContext.getParameterMap().put("command", new String[] { "bulkDelete" });
    addQueryParameter(widgetContext, "currentFolderId", "5");

    Folder folder = new Folder();
    folder.setId(5L);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folder);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      deleteFile.verifyNoInteractions();
    }
    assertEquals("No files were selected", widgetContext.getErrorMessage());
  }
}
