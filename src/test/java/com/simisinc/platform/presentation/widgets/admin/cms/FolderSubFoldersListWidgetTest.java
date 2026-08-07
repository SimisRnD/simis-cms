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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteSubFolderCommand;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.LoadSubFolderCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies two fixes to the per-folder sub-folder list:
 *
 * <p>
 * 1. execute() already computed canEdit/canDelete for the JSP's action column, but the JSP never
 * rendered anything with them -- the tests below cover the widget-level behavior those variables
 * drive (a JSP-rendering test isn't practical in this codebase's test style).
 * </p>
 *
 * <p>
 * 2. delete() never checked the sub-folder's parent-folder delete permission before removing it
 * (same gap FolderDetailsWidget and FolderFilesListWidget's delete() actions were also missing) --
 * any user who could reach the action could delete any sub-folder by id. A sub-folder's own
 * permissions are governed by its parent folder's group grants, so the check uses
 * {@code record.getFolderId()}, not the sub-folder's own id.
 * </p>
 *
 * @author Liz Houser
 * @created 8/6/2026
 */
class FolderSubFoldersListWidgetTest extends WidgetBase {

  private static Folder folderWithId(long id) {
    Folder folder = new Folder();
    folder.setId(id);
    return folder;
  }

  private static SubFolder subFolderWithId(long id, long folderId) {
    SubFolder subFolder = new SubFolder();
    subFolder.setId(id);
    subFolder.setFolderId(folderId);
    return subFolder;
  }

  @Test
  void executeSetsCanEditAndCanDeleteFromThePermissionCommandForANonAdmin() {
    addQueryParameter(widgetContext, "folderId", "5");

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByIdForAuthorizedUser(5L, 1L)).thenReturn(folderWithId(5L));
      perm.when(() -> CheckFolderPermissionCommand.userHasEditPermission(5L, 1L)).thenReturn(true);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(5L, 1L)).thenReturn(false);
      subFolderRepo.when(() -> SubFolderRepository.findAll(any(), any())).thenReturn(new ArrayList<SubFolder>());

      new FolderSubFoldersListWidget().execute(widgetContext);
    }

    assertEquals("true", request.getAttribute("canEdit"));
    assertEquals("false", request.getAttribute("canDelete"));
  }

  @Test
  void executeGrantsAdminBothPermissionsRegardlessOfThePermissionCommand() {
    addQueryParameter(widgetContext, "folderId", "5");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folderWithId(5L));
      // Both stubbed false -- proves the admin role overrides the permission command's answer
      perm.when(() -> CheckFolderPermissionCommand.userHasEditPermission(anyLong(), anyLong())).thenReturn(false);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);
      subFolderRepo.when(() -> SubFolderRepository.findAll(any(), any())).thenReturn(new ArrayList<SubFolder>());

      new FolderSubFoldersListWidget().execute(widgetContext);
    }

    assertEquals("true", request.getAttribute("canEdit"));
    assertEquals("true", request.getAttribute("canDelete"));
  }

  @Test
  void deleteWithoutPermissionDoesNotRemoveTheSubFolder() {
    addQueryParameter(widgetContext, "subFolderId", "42");

    try (MockedStatic<LoadSubFolderCommand> loadSubFolder = mockStatic(LoadSubFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      loadSubFolder.when(() -> LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(42L, 1L))
          .thenReturn(subFolderWithId(42L, 5L));
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(5L, 1L)).thenReturn(false);

      WidgetContext result = new FolderSubFoldersListWidget().delete(widgetContext);

      // The sub-folder must NOT be deleted when the user lacks delete permission on its parent folder
      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()), never());
      assertEquals("Error. You do not have permission to delete this sub-folder.", result.getErrorMessage());
    }
  }

  @Test
  void deleteWithPermissionRemovesTheSubFolder() throws Exception {
    addQueryParameter(widgetContext, "subFolderId", "42");

    try (MockedStatic<LoadSubFolderCommand> loadSubFolder = mockStatic(LoadSubFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      SubFolder subFolder = subFolderWithId(42L, 5L);
      loadSubFolder.when(() -> LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(42L, 1L)).thenReturn(subFolder);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(5L, 1L)).thenReturn(true);
      deleteCmd.when(() -> DeleteSubFolderCommand.deleteSubFolder(subFolder)).thenReturn(true);

      new FolderSubFoldersListWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(subFolder), times(1));
    }
  }

  @Test
  void deleteAsAdminBypassesThePermissionCheck() throws Exception {
    addQueryParameter(widgetContext, "subFolderId", "42");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<LoadSubFolderCommand> loadSubFolder = mockStatic(LoadSubFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      SubFolder subFolder = subFolderWithId(42L, 5L);
      loadSubFolder.when(() -> LoadSubFolderCommand.loadSubFolderById(42L)).thenReturn(subFolder);
      // Stubbed false -- proves the admin role bypasses the permission command entirely
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(eq(5L), anyLong())).thenReturn(false);
      deleteCmd.when(() -> DeleteSubFolderCommand.deleteSubFolder(subFolder)).thenReturn(true);

      new FolderSubFoldersListWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(subFolder), times(1));
    }
  }

  @Test
  void deleteOfAMissingSubFolderIsRejectedWithoutCallingThePermissionCommand() {
    addQueryParameter(widgetContext, "subFolderId", "42");

    try (MockedStatic<LoadSubFolderCommand> loadSubFolder = mockStatic(LoadSubFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      loadSubFolder.when(() -> LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(42L, 1L)).thenReturn(null);

      new FolderSubFoldersListWidget().delete(widgetContext);

      // A missing/inaccessible sub-folder must not reach the permission check or the delete
      perm.verify(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong()), never());
      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()), never());
    }
  }
}
