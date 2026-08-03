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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.CheckFolderPermissionCommand;
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
}
