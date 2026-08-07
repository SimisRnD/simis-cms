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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;

import org.apache.commons.beanutils.ConvertUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
import com.simisinc.platform.application.cms.DeleteFileCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
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
import com.simisinc.platform.presentation.controller.SqlTimestampConverter;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies the search-by-filename/title and sort-by-name/date/size/downloads behavior added to the
 * per-folder file list (issue #502), plus post()'s expiration-date parsing (also issue #502).
 * Before search/sort, the widget always loaded every file in the folder with the repository's
 * default "created DESC" order and no way to filter it.
 *
 * post()'s "form update of an old version" branch populates the bean with
 * BeanUtils.populate(fileItemBean, context.getParameterMap()), then re-parses "expirationDate"
 * explicitly (mirrors WebPageFormWidget.post()'s publishAt/expiresAt handling) -- BeanUtils cannot
 * reliably convert a raw datetime-local string ("2026-09-01T14:30") to a java.sql.Timestamp.
 *
 * Two pieces of global/process-wide state make the expiration-date tests hard to unit test without
 * extra setup; both are pre-existing behavior of this widget, not something these tests work around
 * by accident:
 *
 * 1. PageServlet.init() registers a global, null-swallowing SqlTimestampConverter for
 *    java.sql.Timestamp at real application startup (pattern "MM-dd-yyyy HH:mm", constructed with a
 *    null default so a failed parse returns null instead of throwing). That registration mutates
 *    commons-beanutils' static ConvertUtils registry -- outside a running PageServlet it is not
 *    guaranteed to be registered, and without it BeanUtils.populate() throws ConversionException
 *    (verified directly: commons-beanutils' own default converter for java.sql.Timestamp has no
 *    default value, so it throws rather than swallowing). Each expiration-date test below registers
 *    the same converter PageServlet.init() does, so behavior doesn't depend on whichever other test
 *    happened to run first in the same JVM.
 * 2. SaveFilePartCommand.saveFile() calls FileSystemCommand.getFileServerRootPath() -- which can
 *    reach LoadSitePropertyCommand.loadByName() and a real DB connection via CacheManager's loading
 *    cache -- unconditionally, before it ever checks whether a "file" part was submitted. A plain
 *    metadata edit (no new file version) never needs that lookup, so SaveFilePartCommand is mocked
 *    out below to isolate these tests from it, rather than depending on FileSystemCommand's static
 *    path cache already being warm from an earlier test.
 *
 * @author Liz Houser
 * @created 8/2/2026
 */
class FolderFilesListWidgetTest extends WidgetBase {

  @BeforeEach
  void registerTimestampConverter() {
    SqlTimestampConverter converter = new SqlTimestampConverter(null);
    converter.setPattern("MM-dd-yyyy HH:mm");
    ConvertUtils.register(converter, Timestamp.class);
  }

  private static Folder folderWithId(long id) {
    Folder folder = new Folder();
    folder.setId(id);
    return folder;
  }

  private void setUpMetadataEditRequest() {
    setRoles(widgetContext, ADMIN);
    when(request.getParameter("currentFolderId")).thenReturn("10");
    when(request.getParameter("currentSubFolderId")).thenReturn("-1");
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "folderId", "10");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "categoryId", "-1");
    addQueryParameter(widgetContext, "title", "Employee Handbook");
    addQueryParameter(widgetContext, "filename", "handbook.pdf");
    addQueryParameter(widgetContext, "version", "1.0");
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
  void postParsesAndPersistsAValidExpirationDate() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "2026-09-01T14:30");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      // No new file uploaded -> post() takes the "form update of an old version" (BeanUtils.populate) branch
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FileItem saved = new FileItem();
      saved.setId(42L);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(saved);

      new FolderFilesListWidget().post(widgetContext);

      ArgumentCaptor<FileItem> captor = ArgumentCaptor.forClass(FileItem.class);
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(captor.capture()), times(1));
      Assertions.assertEquals(Timestamp.valueOf("2026-09-01 14:30:00"), captor.getValue().getExpirationDate());
    }
  }

  @Test
  void postLeavesExpirationDateNullWhenTheFieldIsBlank() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FileItem saved = new FileItem();
      saved.setId(42L);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(saved);

      new FolderFilesListWidget().post(widgetContext);

      ArgumentCaptor<FileItem> captor = ArgumentCaptor.forClass(FileItem.class);
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(captor.capture()), times(1));
      Assertions.assertNull(captor.getValue().getExpirationDate());
    }
  }

  @Test
  void postRejectsAMalformedExpirationDateWithoutCrashingAndDoesNotSave() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "not-a-date");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);
      // The parse failure is caught by post()'s existing AppException|DataException handler, which
      // calls SaveFilePartCommand.cleanupFile(fileItemBean) -- a mocked static's void methods are
      // no-ops by default, so this doesn't need an explicit stub.

      WidgetContext result = new FolderFilesListWidget().post(widgetContext);

      Assertions.assertEquals("Expiration date format is not valid", result.getErrorMessage());
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(any(FileItem.class)), never());
    }
  }

  private static FileItem fileWithFolder(long fileId, long folderId) {
    FileItem file = new FileItem();
    file.setId(fileId);
    file.setFolderId(folderId);
    file.setFilename("handbook.pdf");
    return file;
  }

  /**
   * Verifies delete()'s permission-gap fix: it previously had a "// @todo make sure the folder's
   * user group can delete" comment and never called
   * CheckFolderPermissionCommand.userHasDeletePermission -- so any user who could merely view this
   * page (any role admin-layout.xml allows: admin/content-manager/community-manager) could delete an
   * individual file regardless of that folder's own delete-permission ACL, by requesting the delete
   * action directly. The delete icon was already correctly hidden in the UI for a user without
   * delete permission; these tests cover the backend enforcement that was missing.
   */
  @Test
  void deleteWithoutFolderDeletePermissionDoesNotDeleteTheFile() {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "fileId", "42");

    FileItem record = fileWithFolder(42L, 7L);

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFileCommand> deleteCmd = mockStatic(DeleteFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(42L, 1L)).thenReturn(record);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(7L, 1L)).thenReturn(false);

      WidgetContext result = new FolderFilesListWidget().delete(widgetContext);

      // The file must NOT be deleted when the user lacks delete permission on its folder
      deleteCmd.verify(() -> DeleteFileCommand.deleteFile(any()), never());
      Assertions.assertEquals("Error. You do not have permission to delete this file.", result.getErrorMessage());
    }
  }

  @Test
  void deleteWithFolderDeletePermissionDeletesTheFile() {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "fileId", "42");

    FileItem record = fileWithFolder(42L, 7L);

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFileCommand> deleteCmd = mockStatic(DeleteFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadFileByIdForAuthorizedUser(42L, 1L)).thenReturn(record);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(7L, 1L)).thenReturn(true);
      deleteCmd.when(() -> DeleteFileCommand.deleteFile(record)).thenReturn(true);

      WidgetContext result = new FolderFilesListWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteFileCommand.deleteFile(record), times(1));
      Assertions.assertEquals("File deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteAsAdminBypassesTheFolderDeletePermissionCheck() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "42");

    FileItem record = fileWithFolder(42L, 7L);

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<DeleteFileCommand> deleteCmd = mockStatic(DeleteFileCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadItemById(42L)).thenReturn(record);
      // Deliberately false -- an admin must bypass this check entirely, not merely happen to pass it
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);
      deleteCmd.when(() -> DeleteFileCommand.deleteFile(record)).thenReturn(true);

      WidgetContext result = new FolderFilesListWidget().delete(widgetContext);

      deleteCmd.verify(() -> DeleteFileCommand.deleteFile(record), times(1));
      Assertions.assertEquals("File deleted", result.getSuccessMessage());
    }
  }

  /**
   * Verifies the new "canAdd" request attribute (issue: the "Add File Link" button was only shown to
   * hasRole('admin') || hasRole('content-manager'), but FolderFileFormWidget's actual permission
   * check for that action is admin OR CheckFolderPermissionCommand.userHasAddPermission(folderId,
   * userId) -- so a role with real per-folder add permission via the folder's own group ACL had no
   * button to find the feature). canAdd mirrors FolderFileFormWidget's own gate exactly.
   */
  @Test
  void executeGrantsCanAddWhenNonAdminHasFolderAddPermission() {
    addQueryParameter(widgetContext, "folderId", "5");
    setRoles(widgetContext, COMMUNITY_MANAGER);

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByIdForAuthorizedUser(5L, 1L)).thenReturn(folderWithId(5L));
      loadFolder.when(() -> LoadFolderCommand.findAllAuthorizedForUser(1L)).thenReturn(new ArrayList<>());
      perm.when(() -> CheckFolderPermissionCommand.userHasEditPermission(anyLong(), anyLong())).thenReturn(false);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);
      perm.when(() -> CheckFolderPermissionCommand.userHasAddPermission(5L, 1L)).thenReturn(true);
      subFolderRepo.when(() -> SubFolderRepository.findAll(any(), any())).thenReturn(new ArrayList<SubFolder>());
      categoryRepo.when(() -> FolderCategoryRepository.findAllByFolderId(5L)).thenReturn(new ArrayList<FolderCategory>());
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new FolderFilesListWidget().execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("canAdd"));
  }

  @Test
  void executeDeniesCanAddWhenNonAdminLacksFolderAddPermission() {
    addQueryParameter(widgetContext, "folderId", "5");
    setRoles(widgetContext, COMMUNITY_MANAGER);

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByIdForAuthorizedUser(5L, 1L)).thenReturn(folderWithId(5L));
      loadFolder.when(() -> LoadFolderCommand.findAllAuthorizedForUser(1L)).thenReturn(new ArrayList<>());
      perm.when(() -> CheckFolderPermissionCommand.userHasEditPermission(anyLong(), anyLong())).thenReturn(false);
      perm.when(() -> CheckFolderPermissionCommand.userHasDeletePermission(anyLong(), anyLong())).thenReturn(false);
      perm.when(() -> CheckFolderPermissionCommand.userHasAddPermission(5L, 1L)).thenReturn(false);
      subFolderRepo.when(() -> SubFolderRepository.findAll(any(), any())).thenReturn(new ArrayList<SubFolder>());
      categoryRepo.when(() -> FolderCategoryRepository.findAllByFolderId(5L)).thenReturn(new ArrayList<FolderCategory>());
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new FolderFilesListWidget().execute(widgetContext);
    }

    Assertions.assertEquals("false", request.getAttribute("canAdd"));
  }

  @Test
  void executeGrantsCanAddToAdminEvenWithoutFolderAddPermission() {
    addQueryParameter(widgetContext, "folderId", "5");

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      // Deliberately false -- an admin must bypass this check entirely, not merely happen to pass it
      perm.when(() -> CheckFolderPermissionCommand.userHasAddPermission(anyLong(), anyLong())).thenReturn(false);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);
    }

    Assertions.assertEquals("true", request.getAttribute("canAdd"));
  }

  /**
   * Verifies the pagination fix: execute() previously built its DataConstraints with the no-arg
   * constructor (page size -1, i.e. unpaginated), so the entire file list rendered regardless of
   * folder size. Mirrors AdminBlogPostListWidget/FileVersionsListWidget's page/items request params.
   */
  @Test
  void executeDefaultsToPageOneWithThePreferencesPageSize() {
    addQueryParameter(widgetContext, "folderId", "5");
    widgetContext.getPreferences().put("limit", "40");

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

    Assertions.assertEquals(1, constraintsCaptor.getValue().getPageNumber());
    Assertions.assertEquals(40, constraintsCaptor.getValue().getPageSize());
  }

  @Test
  void executeHonorsThePageAndItemsRequestParameters() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "page", "3");
    addQueryParameter(widgetContext, "items", "10");

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

    Assertions.assertEquals(3, constraintsCaptor.getValue().getPageNumber());
    Assertions.assertEquals(10, constraintsCaptor.getValue().getPageSize());
  }

  @Test
  void executeDefaultsThePageSizeTo25WhenNoLimitPreferenceIsConfigured() {
    addQueryParameter(widgetContext, "folderId", "5");

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

    Assertions.assertEquals(25, constraintsCaptor.getValue().getPageSize());
  }

  /**
   * Verifies the recordPagingParams attribute (built by the private appendPagingParam() helper)
   * that paging_control.jspf appends to every pagination link, for the plain-folder case.
   */
  @Test
  void executeSetsRecordPagingParamsForAPlainFolder() {
    addQueryParameter(widgetContext, "folderId", "5");

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);
    }

    String pagingParams = (String) request.getAttribute("recordPagingParams");
    Assertions.assertNotNull(pagingParams);
    Assertions.assertTrue(pagingParams.contains("folderId=5"), "expected folderId=5 in: " + pagingParams);
    Assertions.assertFalse(pagingParams.contains("subFolderId"), "a plain folder must not carry subFolderId: " + pagingParams);
  }

  /**
   * Same as above, but for a sub-folder with an active search term and a non-default sort -- the
   * combined case where a dropped/mis-joined param would be easiest to miss.
   */
  @Test
  void executeSetsRecordPagingParamsForASubFolderWithSearchAndSort() {
    addQueryParameter(widgetContext, "folderId", "5");
    addQueryParameter(widgetContext, "subFolderId", "9");
    addQueryParameter(widgetContext, "query", "invoice");
    addQueryParameter(widgetContext, "sortBy", "name");

    SubFolder subFolder = new SubFolder();
    subFolder.setId(9L);
    subFolder.setFolderId(5L);

    try (MockedStatic<FolderRepository> folderRepo = mockStatic(FolderRepository.class);
        MockedStatic<CheckFolderPermissionCommand> perm = mockStatic(CheckFolderPermissionCommand.class);
        MockedStatic<SubFolderRepository> subFolderRepo = mockStatic(SubFolderRepository.class);
        MockedStatic<FolderCategoryRepository> categoryRepo = mockStatic(FolderCategoryRepository.class);
        MockedStatic<FileItemRepository> fileItemRepo = mockStatic(FileItemRepository.class)) {
      stubCommonCollaborators(folderRepo, perm, subFolderRepo, categoryRepo, 5L);
      subFolderRepo.when(() -> SubFolderRepository.findById(9L)).thenReturn(subFolder);
      fileItemRepo.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      setRoles(widgetContext, ADMIN);
      new FolderFilesListWidget().execute(widgetContext);
    }

    String pagingParams = (String) request.getAttribute("recordPagingParams");
    Assertions.assertNotNull(pagingParams);
    Assertions.assertTrue(pagingParams.contains("folderId=5"), "expected folderId=5 in: " + pagingParams);
    Assertions.assertTrue(pagingParams.contains("subFolderId=9"), "expected subFolderId=9 in: " + pagingParams);
    Assertions.assertTrue(pagingParams.contains("query=invoice"), "expected query=invoice in: " + pagingParams);
    Assertions.assertTrue(pagingParams.contains("sortBy=name"), "expected sortBy=name in: " + pagingParams);
  }
}
