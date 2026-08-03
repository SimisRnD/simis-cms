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
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
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
 * Proves FolderFileDropZoneWidget -- the drag-and-drop widget that uploads a brand-new folder file's
 * actual bytes -- now writes an AuditEventCommand.record(...) call for both the success and failure
 * paths, closing part of the CMMC AU-2 gap for folder-file mutations (issue #502).
 */
class FolderFileDropZoneWidgetTest extends WidgetBase {

  @Test
  void postUploadingANewFileRecordsAFolderFileCreateSuccessEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    FileItem uploadedPart = new FileItem();
    uploadedPart.setFilename("diagram.png");
    uploadedPart.setExtension("png");
    uploadedPart.setFileLength(2048);

    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(50L);
    savedFileItem.setFilename("diagram.png");
    savedFileItem.setFileLength(2048);
    savedFileItem.setWebPath("20260802120000");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      folderRepository.when(() -> FolderRepository.findById(1L)).thenReturn(null);
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);

      FolderFileDropZoneWidget widget = new FolderFileDropZoneWidget();
      WidgetContext result = widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.SUCCESS), eq("folder_file"), eq("50"),
          eq("diagram.png"), any()), times(1));
      Assertions.assertTrue(result.hasJson());
    }
  }

  @Test
  void postWithNoFilePartRecordsAFolderFileCreateFailureEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      folderRepository.when(() -> FolderRepository.findById(1L)).thenReturn(null);
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FolderFileDropZoneWidget widget = new FolderFileDropZoneWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.FAILURE), eq("folder_file"), eq("-1"), eq(null),
          eq("A file was not found, please choose a file and try again")), times(1));
    }
  }

  @Test
  void postWithADisallowedExtensionRecordsAFolderFileCreateFailureEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    // Deliberately not ".exe"/.dll/etc: those are rejected earlier by ValidateFileCommand's
    // global dangerous-extension blocklist (see MediaApiControllerTest#uploadRejectsABlockedDangerousExtension),
    // which would short-circuit before ever reaching the folder-specific allowlist this test
    // targets -- SaveFileCommand.saveFile()'s validateAllowedExtension() (issue #370).
    FileItem uploadedPart = new FileItem();
    uploadedPart.setFilename("diagram.png");
    uploadedPart.setExtension("png");
    uploadedPart.setFileLength(1024);

    // ValidateFileCommand is mocked out (same as the success-path test above) so its real
    // checkFile() never runs -- unmocked, it resolves the file server root path via
    // LoadSitePropertyCommand, which falls through to a real DB lookup with no DataSource
    // configured in this unit test.
    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart))
          .thenThrow(new DataException("File type '.png' is not allowed in this folder"));

      FolderFileDropZoneWidget widget = new FolderFileDropZoneWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.FAILURE), eq("folder_file"), any(),
          eq("diagram.png"), eq("File type '.png' is not allowed in this folder")), times(1));
    }
  }
}
