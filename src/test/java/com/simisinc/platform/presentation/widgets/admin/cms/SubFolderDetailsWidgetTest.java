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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteSubFolderCommand;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;

/**
 * Verifies that deleting a CMS sub-folder is gated on the user's delete permission for the parent
 * folder, matching how folder deletes are gated (see FolderDetailsWidgetTest). Without the check,
 * any admin/content-manager/community-manager who can merely view the sub-folder-details page could
 * delete any sub-folder by id, regardless of the parent folder's own delete-permission ACL.
 *
 * @author Liz Houser
 * @created 8/6/2026
 */
class SubFolderDetailsWidgetTest extends WidgetBase {

  private static SubFolder subFolderWithId(long id, long folderId) {
    SubFolder subFolder = new SubFolder();
    subFolder.setId(id);
    subFolder.setFolderId(folderId);
    return subFolder;
  }

  @Test
  void deleteWithoutPermissionDoesNotRemoveTheSubFolder() {
    addQueryParameter(widgetContext, "subFolderId", "5");
    try (MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      subFolderRepo.when(() -> SubFolderRepository.findById(5L)).thenReturn(subFolderWithId(5L, 9L));
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);

      new SubFolderDetailsWidget().delete(widgetContext);

      // The sub-folder must NOT be deleted when the user lacks delete permission on the parent folder
      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()), never());
    }
  }

  @Test
  void deleteWithPermissionRemovesTheSubFolder() {
    addQueryParameter(widgetContext, "subFolderId", "5");
    try (MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      subFolderRepo.when(() -> SubFolderRepository.findById(5L)).thenReturn(subFolderWithId(5L, 9L));
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(true);

      new SubFolderDetailsWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()));
    }
  }

  @Test
  void deleteAsAdminBypassesTheFolderPermissionCheck() {
    addQueryParameter(widgetContext, "subFolderId", "5");
    setRoles(widgetContext, ADMIN);
    try (MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      subFolderRepo.when(() -> SubFolderRepository.findById(5L)).thenReturn(subFolderWithId(5L, 9L));
      // No delete permission on the parent folder -- an admin must still be able to delete
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);

      new SubFolderDetailsWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()));
    }
  }

  @Test
  void deleteOfAMissingSubFolderIsRejected() {
    addQueryParameter(widgetContext, "subFolderId", "5");
    try (MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteSubFolderCommand> deleteCmd = mockStatic(DeleteSubFolderCommand.class)) {
      subFolderRepo.when(() -> SubFolderRepository.findById(5L)).thenReturn(null);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(true);

      new SubFolderDetailsWidget().delete(widgetContext);

      // A null sub-folder must not reach the delete (and the permission check must not NPE on it)
      deleteCmd.verify(() -> DeleteSubFolderCommand.deleteSubFolder(any()), never());
    }
  }
}
