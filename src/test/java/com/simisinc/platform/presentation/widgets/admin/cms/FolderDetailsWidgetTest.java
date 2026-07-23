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
import com.simisinc.platform.application.cms.DeleteFolderCommand;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;

/**
 * Verifies that deleting a CMS folder is gated on the user's per-folder delete permission, matching
 * how file and item deletes are gated. Without the check, any user who can reach the action could
 * delete any folder by id -- broken access control.
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class FolderDetailsWidgetTest extends WidgetBase {

  private static Folder folderWithId(long id) {
    Folder folder = new Folder();
    folder.setId(id);
    return folder;
  }

  @Test
  void deleteWithoutPermissionDoesNotRemoveTheFolder() {
    addQueryParameter(widgetContext, "folderId", "5");
    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFolderCommand> deleteCmd = mockStatic(DeleteFolderCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folderWithId(5L));
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);

      new FolderDetailsWidget().delete(widgetContext);

      // The folder must NOT be deleted when the user lacks delete permission on it
      deleteCmd.verify(() -> DeleteFolderCommand.deleteFolder(any()), never());
    }
  }

  @Test
  void deleteWithPermissionRemovesTheFolder() {
    addQueryParameter(widgetContext, "folderId", "5");
    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFolderCommand> deleteCmd = mockStatic(DeleteFolderCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(folderWithId(5L));
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(true);

      new FolderDetailsWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteFolderCommand.deleteFolder(any()));
    }
  }

  @Test
  void deleteOfAMissingFolderIsRejected() {
    addQueryParameter(widgetContext, "folderId", "5");
    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFolderCommand> deleteCmd = mockStatic(DeleteFolderCommand.class)) {
      folderRepo.when(() -> FolderRepository.findById(5L)).thenReturn(null);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(true);

      new FolderDetailsWidget().delete(widgetContext);

      // A null folder must not reach the delete (and the permission check must not NPE on it)
      deleteCmd.verify(() -> DeleteFolderCommand.deleteFolder(any()), never());
    }
  }
}
