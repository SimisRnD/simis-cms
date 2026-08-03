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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.DeleteFileCommand;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Proves FolderFilesListWidget -- the admin widget that uploads new file versions, edits file metadata,
 * and deletes folder files -- now writes an AuditEventCommand.record(...) call for each of those
 * mutations, closing the CMMC AU-2 gap where none of these paths had any audit trail (issue #502).
 *
 * <p>Note: {@code context.getRequest().getParameter(...)} (used by post() for currentFolderId /
 * currentSubFolderId) reads from WidgetBase's mocked request "attributes" map, populated via
 * request.setAttribute(...) -- NOT from WidgetContext's own parameterMap, which is what
 * addQueryParameter() populates and what context.getParameter()/getParameterAsLong() read. Both are set
 * below where needed, matching what each call site actually reads.
 */
class FolderFilesListWidgetTest extends WidgetBase {

  private void setRawRequestParameter(String name, String value) {
    request.setAttribute(name, value);
  }

  @Test
  void postWithANewFileVersionRecordsAFolderFileVersionSuccessEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    setRawRequestParameter("currentFolderId", "1");
    setRawRequestParameter("currentSubFolderId", "-1");
    addQueryParameter(widgetContext, "id", "20");
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "categoryId", "-1");
    addQueryParameter(widgetContext, "version", "2.0");
    addQueryParameter(widgetContext, "title", "Report");

    FileItem newVersionPart = new FileItem();
    newVersionPart.setFilename("report.pdf");

    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(20L);
    savedFileItem.setFilename("report.pdf");
    savedFileItem.setVersion("2.0");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        // checkFile() reaches into FileSystemCommand.getFileServerRootPath(), which falls back to a
        // site-property DB lookup when unset -- not available in this unit test, so it's stubbed out
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(newVersionPart);
      saveFileCommand.when(() -> SaveFileCommand.saveNewVersionOfFile(newVersionPart)).thenReturn(savedFileItem);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.version"), eq(AuditEventCommand.SUCCESS), eq("folder_file"), eq("20"),
          eq("report.pdf"), any()), times(1));
    }
  }

  @Test
  void postWithAMetadataOnlyUpdateRecordsAFolderFileUpdateSuccessEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    setRawRequestParameter("currentFolderId", "1");
    setRawRequestParameter("currentSubFolderId", "-1");
    addQueryParameter(widgetContext, "id", "21");
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "title", "Renamed Report");
    addQueryParameter(widgetContext, "filename", "report.pdf");

    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(21L);
    savedFileItem.setFilename("report.pdf");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      // No "file" part in the request -- this is a plain metadata edit of an existing version
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(savedFileItem);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.update"), eq(AuditEventCommand.SUCCESS), eq("folder_file"), eq("21"),
          eq("report.pdf"), any()), times(1));
    }
  }

  @Test
  void postWhereSavingTheNewVersionFailsRecordsAFolderFileVersionFailureEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    setRawRequestParameter("currentFolderId", "1");
    setRawRequestParameter("currentSubFolderId", "-1");
    addQueryParameter(widgetContext, "id", "22");
    addQueryParameter(widgetContext, "folderId", "1");

    FileItem newVersionPart = new FileItem();
    newVersionPart.setFilename("bad.pdf");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(newVersionPart);
      // A null return is treated as a system-error failure by the widget
      saveFileCommand.when(() -> SaveFileCommand.saveNewVersionOfFile(newVersionPart)).thenReturn(null);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.version"), eq(AuditEventCommand.FAILURE), eq("folder_file"), any(), eq("bad.pdf"),
          any()), times(1));
    }
  }

  @Test
  void deleteRecordsAFolderFileDeleteSuccessEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "30");

    FileItem record = new FileItem();
    record.setId(30L);
    record.setFilename("old-report.pdf");
    record.setFolderId(1L);

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadItemById(30L)).thenReturn(record);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(record)).thenReturn(true);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      WidgetContext result = widget.delete(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.delete"), eq(AuditEventCommand.SUCCESS), eq("folder_file"), eq("30"),
          eq("old-report.pdf"), any()), times(1));
      Assertions.assertEquals("File deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteWhenTheRepositoryRemoveFailsRecordsAFolderFileDeleteFailureEvent() throws Exception {
    // DeleteFileCommand.deleteFile() returning false (as opposed to throwing) is a real, if obscure,
    // failure path -- the audit record must reflect it even though the pre-existing (unrelated,
    // out-of-scope-here) "File deleted" success message does not check this return value.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "31");

    FileItem record = new FileItem();
    record.setId(31L);
    record.setFilename("stubborn.pdf");
    record.setFolderId(1L);

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<DeleteFileCommand> deleteFile = mockStatic(DeleteFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadItemById(31L)).thenReturn(record);
      deleteFile.when(() -> DeleteFileCommand.deleteFile(record)).thenReturn(false);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      widget.delete(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.delete"), eq(AuditEventCommand.FAILURE), eq("folder_file"), eq("31"),
          eq("stubborn.pdf"), any()), times(1));
    }
  }

  @Test
  void deleteWhenTheFileIsNotFoundRecordsAFailureEventAndReturnsNull() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "fileId", "99");

    try (MockedStatic<LoadFileCommand> loadFile = mockStatic(LoadFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadFile.when(() -> LoadFileCommand.loadItemById(99L)).thenReturn(null);

      FolderFilesListWidget widget = new FolderFilesListWidget();
      WidgetContext result = widget.delete(widgetContext);

      Assertions.assertNull(result);
      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.delete"), eq(AuditEventCommand.FAILURE), eq("folder_file"), eq("99"), eq(null),
          any()), times(1));
    }
  }
}
