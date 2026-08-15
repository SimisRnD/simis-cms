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

import java.io.File;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.AddImageToLibraryCommand;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.cms.ValidateFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Proves FolderFileDropZoneWidget -- the drag-and-drop widget that uploads a brand-new folder file's
 * actual bytes -- now writes an AuditEventCommand.record(...) call for both the success and failure
 * paths, closing part of the CMMC AU-2 gap for folder-file mutations (issue #502).
 *
 * <p>Also proves that a rejected upload sets a real JSON error response (not just
 * context.setErrorMessage(...)). Without it, the dropzone form's targeted-widget POST falls through
 * to a redirect that the browser's XMLHttpRequest follows transparently, reporting the reloaded
 * page's HTTP 200 back to Dropzone.js -- which decides success/error purely from that status code,
 * so a rejected upload was reported to the admin as a success ("N files uploaded. Refreshing...").
 */
class FolderFileDropZoneWidgetTest extends WidgetBase {

  /**
   * The drop zone must advertise the same limit the server enforces, read from the one
   * system.upload.maxBytes property (issue #1198). Dropzone.js's maxFilesize is in whole
   * megabytes, so 52428800 bytes has to reach the JSP as 50 -- not the old hardcoded 55 that
   * matched nothing.
   */
  @Test
  void executeAdvertisesTheConfiguredUploadCeilingInWholeMegabytes() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      folderRepository.when(() -> FolderRepository.findById(1L)).thenReturn(new Folder());
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("52428800");

      WidgetContext result = new FolderFileDropZoneWidget().execute(widgetContext);

      Assertions.assertEquals("50", widgetContext.getRequest().getAttribute("maxUploadSize"));
      Assertions.assertEquals("/admin/folder-file-drop-zone.jsp", result.getJsp());
    }
  }

  @Test
  void executeFallsBackToTheDefaultCeilingWhenThePropertyIsUnset() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    try (MockedStatic<FolderRepository> folderRepository = mockStatic(FolderRepository.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      folderRepository.when(() -> FolderRepository.findById(1L)).thenReturn(new Folder());
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      new FolderFileDropZoneWidget().execute(widgetContext);

      // SaveFilePartCommand.resolveMaxUploadBytes()'s 10 MB code default, rendered as whole MB
      Assertions.assertEquals("10", widgetContext.getRequest().getAttribute("maxUploadSize"));
    }
  }

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
      WidgetContext result = widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.FAILURE), eq("folder_file"), eq("-1"), eq(null),
          eq("A file was not found, please choose a file and try again")), times(1));

      // A rejected upload must set a JSON error response, not just an error message, or the
      // targeted-widget POST falls through to a redirect that Dropzone.js's XHR sees as a plain 200
      // and reports as a success (issue: rejected uploads showing "N files uploaded").
      Assertions.assertTrue(result.hasJson(), "A rejected upload must set a JSON response");
      Assertions.assertTrue(result.getJson().contains("\"error\""), "The JSON response must carry an error field");
      Assertions.assertTrue(result.getJson().contains("A file was not found"));
      Assertions.assertFalse(result.getJson().contains("location"), "A rejected upload must not report a success-shaped location");
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
      WidgetContext result = widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.FAILURE), eq("folder_file"), any(),
          eq("diagram.png"), eq("File type '.png' is not allowed in this folder")), times(1));

      // Same JSON-error-response guarantee as the no-file-part case above, this time for the
      // folder's allowed-extension rejection (issue #370's DataException path).
      Assertions.assertTrue(result.hasJson(), "A rejected upload must set a JSON response");
      Assertions.assertTrue(result.getJson().contains("\"error\""), "The JSON response must carry an error field");
      Assertions.assertTrue(result.getJson().contains("File type '.png' is not allowed in this folder"));
      Assertions.assertFalse(result.getJson().contains("location"), "A rejected upload must not report a success-shaped location");
    }
  }

  /**
   * Issue #1197: a file uploaded here lands in the folder file system, which the image picker on
   * Site Settings / Theme Settings cannot see -- so a logo uploaded to a folder could never be
   * selected as one. Ticking "Also add images to the Image Library" registers the image in the
   * library too, via the same write path the site properties editor's own uploader uses.
   */
  private FileItem uploadedImagePart() {
    FileItem uploadedPart = new FileItem();
    uploadedPart.setFilename("logo.png");
    uploadedPart.setExtension("png");
    uploadedPart.setFileLength(4096);
    return uploadedPart;
  }

  private FileItem savedImageFileItem(String fileType) {
    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(60L);
    savedFileItem.setFilename("logo.png");
    savedFileItem.setFileLength(4096);
    savedFileItem.setWebPath("20260814120000");
    savedFileItem.setFileServerPath("uploads/2026/08/14/unique-name.png");
    // ValidateFileCommand.checkFile() derives this from the file's detected mime type -- the same
    // value the folder listing uses to decide whether a row gets an image thumbnail
    savedFileItem.setFileType(fileType);
    return savedFileItem;
  }

  @Test
  void postWithAddToImageLibraryAddsTheUploadedImageToTheLibrary() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "addToImageLibrary", "true");

    FileItem uploadedPart = uploadedImagePart();
    FileItem savedFileItem = savedImageFileItem("Image");

    Image savedImage = new Image();
    savedImage.setId(77L);
    savedImage.setFilename("logo.png");
    savedImage.setWebPath("20260814120000");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<AddImageToLibraryCommand> addImage = mockStatic(AddImageToLibraryCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/files/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenReturn(new File("/files/uploads/2026/08/14/unique-name.png"));
      addImage.when(() -> AddImageToLibraryCommand.addFromFile(any(File.class), anyString(), anyLong()))
          .thenReturn(savedImage);

      WidgetContext result = new FolderFileDropZoneWidget().post(widgetContext);

      // The library copy goes through the shared command, keeping the record identical to one
      // uploaded through the site properties editor (validation, unique filename, srcset variants)
      addImage.verify(() -> AddImageToLibraryCommand.addFromFile(any(File.class), eq("logo.png"), anyLong()), times(1));
      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("image.create"), eq(AuditEventCommand.SUCCESS), eq("image"), eq("77"), eq("logo.png"), any()), times(1));
      Assertions.assertTrue(result.hasJson());
      Assertions.assertTrue(result.getJson().contains("location"));
      Assertions.assertFalse(result.getJson().contains("libraryError"),
          "A library add that worked must not report an error");
    }
  }

  @Test
  void postWithoutAddToImageLibraryLeavesTheImageLibraryAlone() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");

    FileItem uploadedPart = uploadedImagePart();
    FileItem savedFileItem = savedImageFileItem("Image");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<AddImageToLibraryCommand> addImage = mockStatic(AddImageToLibraryCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);

      WidgetContext result = new FolderFileDropZoneWidget().post(widgetContext);

      // Unticked (and every upload predating this option) must behave exactly as before -- no
      // duplicate bytes and no library records nobody asked for
      addImage.verify(() -> AddImageToLibraryCommand.addFromFile(any(File.class), anyString(), anyLong()), never());
      Assertions.assertTrue(result.hasJson());
      Assertions.assertFalse(result.getJson().contains("libraryError"));
    }
  }

  @Test
  void postWithAddToImageLibrarySkipsFilesThatAreNotImages() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "addToImageLibrary", "true");

    FileItem uploadedPart = uploadedImagePart();
    FileItem savedFileItem = savedImageFileItem("PDF");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<AddImageToLibraryCommand> addImage = mockStatic(AddImageToLibraryCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);

      WidgetContext result = new FolderFileDropZoneWidget().post(widgetContext);

      // The option names images; a ticked box must not turn a good PDF upload into an error
      addImage.verify(() -> AddImageToLibraryCommand.addFromFile(any(File.class), anyString(), anyLong()), never());
      Assertions.assertTrue(result.hasJson());
      Assertions.assertTrue(result.getJson().contains("location"));
      Assertions.assertFalse(result.getJson().contains("libraryError"));
    }
  }

  @Test
  void postReportsAFailedImageLibraryAddWithoutFailingTheFolderUpload() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "addToImageLibrary", "true");

    FileItem uploadedPart = uploadedImagePart();
    FileItem savedFileItem = savedImageFileItem("Image");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<ValidateFileCommand> validateFile = mockStatic(ValidateFileCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<AddImageToLibraryCommand> addImage = mockStatic(AddImageToLibraryCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(uploadedPart);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(uploadedPart)).thenReturn(savedFileItem);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/files/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenReturn(new File("/files/uploads/2026/08/14/unique-name.png"));
      addImage.when(() -> AddImageToLibraryCommand.addFromFile(any(File.class), anyString(), anyLong()))
          .thenThrow(new DataException("Image could not be read"));

      WidgetContext result = new FolderFileDropZoneWidget().post(widgetContext);

      // The folder file saved, so this is a partial success: still report the location so Dropzone
      // counts it as uploaded, but say what didn't happen rather than skipping the step in silence
      Assertions.assertTrue(result.hasJson());
      Assertions.assertTrue(result.getJson().contains("location"),
          "The folder file saved, so its location must still be reported");
      Assertions.assertTrue(result.getJson().contains("libraryError"),
          "A skipped library add must be reported, not swallowed");
      Assertions.assertTrue(result.getJson().contains("Image could not be read"));
      Assertions.assertFalse(result.getJson().contains("\"error\""),
          "A partial success must not be reported to Dropzone as a failed upload");
      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("image.create"), eq(AuditEventCommand.FAILURE), eq("image"), eq("-1"), eq("logo.png"),
          eq("Image could not be read")), times(1));
    }
  }
}
