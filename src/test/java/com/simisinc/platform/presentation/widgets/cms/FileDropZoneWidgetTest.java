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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadFolderCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Proves that FileDropZoneWidget -- the drag-and-drop widget used for a folder's public-facing
 * "drop-box" upload form -- sets a real JSON error response when a file is rejected server-side
 * (disallowed extension, oversized file, any DataException from ValidateFileCommand/SaveFileCommand).
 *
 * <p>Without a JSON response, this dropzone form's targeted-widget POST falls through to a redirect
 * that the browser's XMLHttpRequest follows transparently, reporting the reloaded page's HTTP 200
 * back to the caller. Dropzone.js decides success/error purely from that status code, so a rejected
 * upload was reported to the user as a success ("N files uploaded. Refreshing...") even though it was
 * actually rejected.
 */
class FileDropZoneWidgetTest extends WidgetBase {

  private Folder buildFolder() {
    Folder folder = new Folder();
    folder.setId(1L);
    return folder;
  }

  @Test
  void postUploadingANewFileReturnsALocationJsonResponse() throws Exception {
    setRoles(widgetContext, ADMIN);
    preferences.put("folderUniqueId", "drop-box");

    Folder folder = buildFolder();

    FileItem uploadedPart = new FileItem();
    uploadedPart.setFilename("diagram.png");
    uploadedPart.setExtension("png");
    uploadedPart.setFileLength(2048);

    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(50L);
    savedFileItem.setFilename("diagram.png");
    savedFileItem.setFileLength(2048);
    savedFileItem.setWebPath("20260802120000");

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(anyString(), anyLong()))
          .thenReturn(folder);
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);

      FileDropZoneWidget widget = new FileDropZoneWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertTrue(result.hasJson());
      Assertions.assertTrue(result.getJson().contains("location"));
    }
  }

  @Test
  void postWithNoFilePartSetsAJsonErrorResponse() throws Exception {
    setRoles(widgetContext, ADMIN);
    preferences.put("folderUniqueId", "drop-box");

    Folder folder = buildFolder();

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(anyString(), anyLong()))
          .thenReturn(folder);
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FileDropZoneWidget widget = new FileDropZoneWidget();
      WidgetContext result = widget.post(widgetContext);

      // A rejected upload must set a JSON error response, not just an error message, or the
      // targeted-widget POST falls through to a redirect that Dropzone.js's XHR sees as a plain 200
      // and reports as a success.
      Assertions.assertTrue(result.hasJson(), "A rejected upload must set a JSON response");
      Assertions.assertTrue(result.getJson().contains("\"error\""), "The JSON response must carry an error field");
      Assertions.assertTrue(result.getJson().contains("A file was not found"));
      Assertions.assertFalse(result.getJson().contains("location"), "A rejected upload must not report a success-shaped location");
      Assertions.assertEquals("A file was not found, please choose a file and try again", result.getErrorMessage());
    }
  }

  @Test
  void postWithADisallowedExtensionSetsAJsonErrorResponse() throws Exception {
    setRoles(widgetContext, ADMIN);
    preferences.put("folderUniqueId", "drop-box");

    Folder folder = buildFolder();

    FileItem uploadedPart = new FileItem();
    uploadedPart.setFilename("virus.exe");
    uploadedPart.setExtension("exe");
    uploadedPart.setFileLength(1024);

    try (MockedStatic<LoadFolderCommand> loadFolder = mockStatic(LoadFolderCommand.class);
        MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class)) {
      loadFolder.when(() -> LoadFolderCommand.loadFolderByUniqueIdForAuthorizedUser(anyString(), anyLong()))
          .thenReturn(folder);
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      validateFile.when(() -> ValidateFileCommand.checkFileExtension("exe"))
          .thenThrow(new DataException("File type '.exe' is not allowed"));

      FileDropZoneWidget widget = new FileDropZoneWidget();
      WidgetContext result = widget.post(widgetContext);

      Assertions.assertTrue(result.hasJson(), "A rejected upload must set a JSON response");
      Assertions.assertTrue(result.getJson().contains("\"error\""), "The JSON response must carry an error field");
      Assertions.assertTrue(result.getJson().contains("File type '.exe' is not allowed"));
      Assertions.assertFalse(result.getJson().contains("location"), "A rejected upload must not report a success-shaped location");
    }
  }
}
