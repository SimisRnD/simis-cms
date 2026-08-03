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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteFileCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.domain.model.cms.FolderCategory;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.FolderCategoryRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.infrastructure.persistence.cms.SubFolderRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Verifies the search-by-filename/title and sort-by-name/date/size/downloads behavior added to the
 * per-folder file list (issue #502). Before this, the widget always loaded every file in the
 * folder with the repository's default "created DESC" order and no way to filter it.
 *
 * @author Liz Houser
 * @created 8/2/2026
 */
class FolderFilesListWidgetTest extends WidgetBase {

  private static Folder folderWithId(long id) {
    Folder folder = new Folder();
    folder.setId(id);
    return folder;
  }

  /** Stubs every collaborator the widget's execute() touches besides FileItemRepository.findAll. */
  private void stubCommonCollaborators(MockedStatic<FolderRepository> folderRepo,
      MockedStatic<CheckFolderPermissionCommand> perm, MockedStatic<SubFolderRepository> subFolderRepo,
      MockedStatic<FolderCategoryRepository> categoryRepo, long folderId) {
    folderRepo.when(() -> FolderRepository.findById(folderId)).thenReturn(folderWithId(folderId));
    folderRepo.when(FolderRepository::findAll).thenReturn(new ArrayList<>());
    perm.when(() -> CheckFolderPermissionCommand.userHasEditPermission(anyLong(), anyLong())).thenReturn(false);
    perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);
    subFolderRepo.when(() -> SubFolderRepository.findAll(any(), any())).thenReturn(new ArrayList<SubFolder>());
    categoryRepo.when(() -> FolderCategoryRepository.findAllByFolderId(folderId)).thenReturn(new ArrayList<FolderCategory>());
  }

  @Test
  void executeWithNoParamsDefaultsToDateDescendingAndNoSearchTerm() {
    addQueryParameter(widgetContext, "folderId", "5");

    ArgumentCaptor<FileSpecification> specCaptor = ArgumentCaptor.forClass(FileSpecification.class);
    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(specCaptor.capture(), constraintsCaptor.capture()));
    }

    FileSpecification spec = specCaptor.getValue();
    Assertions.assertEquals(5L, spec.getFolderId());
    Assertions.assertNull(spec.getSearchTerm());

    DataConstraints constraints = constraintsCaptor.getValue();
    Assertions.assertArrayEquals(new String[] { "created" }, constraints.getColumnsToSortBy());
    Assertions.assertArrayEquals(new String[] { "desc" }, constraints.getSortOrder());

    Assertions.assertEquals("date", request.getAttribute("sortBy"));
  }

  @Test
  void executeAppliesSearchTermToTheSpecification() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "query", "invoice");

    ArgumentCaptor<FileSpecification> specCaptor = ArgumentCaptor.forClass(FileSpecification.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(specCaptor.capture(), any()));
    }

    Assertions.assertEquals("invoice", specCaptor.getValue().getSearchTerm());
    Assertions.assertEquals("invoice", request.getAttribute("query"));
  }

  @Test
  void executeSortsByNameAscending() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "sortBy", "name");

    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(any(), constraintsCaptor.capture()));
    }

    Assertions.assertArrayEquals(new String[] { "title" }, constraintsCaptor.getValue().getColumnsToSortBy());
    Assertions.assertEquals("name", request.getAttribute("sortBy"));
  }

  @Test
  void executeSortsBySizeDescending() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "sortBy", "size");

    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(any(), constraintsCaptor.capture()));
    }

    Assertions.assertArrayEquals(new String[] { "file_length" }, constraintsCaptor.getValue().getColumnsToSortBy());
    Assertions.assertArrayEquals(new String[] { "desc" }, constraintsCaptor.getValue().getSortOrder());
  }

  @Test
  void executeSortsByDownloadsDescending() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "sortBy", "downloads");

    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(any(), constraintsCaptor.capture()));
    }

    Assertions.assertArrayEquals(new String[] { "download_count" }, constraintsCaptor.getValue().getColumnsToSortBy());
    Assertions.assertArrayEquals(new String[] { "desc" }, constraintsCaptor.getValue().getSortOrder());
  }

  @Test
  void executeFallsBackToDateSortForAnUnrecognizedValue() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "sortBy", "not-a-real-column");

    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(any(), constraintsCaptor.capture()));
    }

    Assertions.assertArrayEquals(new String[] { "created" }, constraintsCaptor.getValue().getColumnsToSortBy());
    // The echoed value must be the normalized "date", not the unrecognized input -- otherwise the
    // sort <select> in the JSP would have no matching <option> to mark selected.
    Assertions.assertEquals("date", request.getAttribute("sortBy"));
  }

  @Test
  void executeSearchAndSortComposeTogether() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "query", "report");
    addQueryParameter(widgetContext, "sortBy", "size");

    ArgumentCaptor<FileSpecification> specCaptor = ArgumentCaptor.forClass(FileSpecification.class);
    ArgumentCaptor<DataConstraints> constraintsCaptor = ArgumentCaptor.forClass(DataConstraints.class);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);

      fileItemRepo.verify(() -> FileItemRepository.findAll(specCaptor.capture(), constraintsCaptor.capture()));
    }

    // A tsvector search (searchName) would force its own "rank DESC" ORDER BY and silently discard
    // the caller's column sort -- this proves the plain substring search (searchTerm) is used
    // instead, since the explicit "size" sort survives alongside it.
    Assertions.assertEquals("report", specCaptor.getValue().getSearchTerm());
    Assertions.assertArrayEquals(new String[] { "file_length" }, constraintsCaptor.getValue().getColumnsToSortBy());
  }

  @Test
  void deleteWhenTheRepositoryRemoveFailsDoesNotClaimSuccess() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "7");

    FileItem record = new FileItem();
    record.setId(7L);
    record.setFilename("policy.pdf");
    record.setFolderId(5L);

    try (MockedStatic<LoadFileCommand> loadFileCommand = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFileCommand = mockStatic(DeleteFileCommand.class);
        MockedStatic<AuditEventCommand> auditEventCommand = mockStatic(AuditEventCommand.class)) {
      loadFileCommand.when(() -> LoadFileCommand.loadItemById(7L)).thenReturn(record);
      // A false return (no exception) models a DB-level failure, e.g. FileItemRepository.remove()
      // failing -- the widget must not tell the admin the file was deleted when it was not.
      deleteFileCommand.when(() -> DeleteFileCommand.deleteFile(record)).thenReturn(false);

      new FolderFilesListWidget().delete(widgetContext);

      auditEventCommand.verify(() -> AuditEventCommand.record(any(), any(), any(),
          eq(AuditEventCommand.FAILURE), any(), any(), any(), any()));
    }

    Assertions.assertNull(widgetContext.getSuccessMessage());
    Assertions.assertNotNull(widgetContext.getErrorMessage());
    Assertions.assertNull(widgetContext.getRedirect());
  }
}
