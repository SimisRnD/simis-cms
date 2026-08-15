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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import org.jobrunr.scheduling.BackgroundJobRequest;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.SaveImageCommand;
import com.simisinc.platform.application.cms.ValidateImageCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.scheduler.cms.ImageVariantJob;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies {@link ImageUploadWidget#post} enqueues an {@link ImageVariantJob} (issue #411) for the
 * saved image's id on a successful upload, and never enqueues one when the upload fails to save.
 * {@link FileSystemCommand}, {@link LoadSitePropertyCommand}, {@link ValidateImageCommand}, {@link
 * SaveImageCommand}, and {@link BackgroundJobRequest} are statically mocked so no real file I/O or
 * job scheduling occurs.
 *
 * @author SimIS Inc.
 */
class ImageUploadWidgetTest {

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);

  private WidgetContext newContext() {
    WidgetContext context = new WidgetContext(request, response, "widget1", "/admin/image-upload");
    context.setParameterMap(new HashMap<>());
    context.setPreferences(new HashMap<>());
    context.setCoreData(Map.of("userId", "1"));
    return context;
  }

  private Part mockFilePart(String filename, long size) {
    Part part = mock(Part.class);
    when(part.getSubmittedFileName()).thenReturn(filename);
    when(part.getSize()).thenReturn(size);
    return part;
  }

  private void stubFileSystemCommand(MockedStatic<FileSystemCommand> fsc, Path tempDir) {
    fsc.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString() + "/");
    fsc.when(() -> FileSystemCommand.generateFileServerSubPath(anyString())).thenReturn("images/2026/08/03/");
    fsc.when(() -> FileSystemCommand.generateUniqueFilename(anyLong())).thenReturn("unique-name");
    fsc.when(() -> FileSystemCommand.cleanExtension(anyString())).thenAnswer(inv -> inv.getArgument(0));
    fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
        .thenReturn(tempDir.resolve("upload-target.png").toFile());
  }

  @Test
  void postEnqueuesAnImageVariantJobWithTheSavedImageIdOnSuccess(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    Image savedImage = new Image();
    savedImage.setId(42L);
    savedImage.setFilename("photo.png");
    savedImage.setWebPath("20260803120000");

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenReturn(savedImage);

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(argThat((ImageVariantJob job) -> job.getImageId() == 42L)));
    }
  }

  @Test
  void postDoesNotEnqueueAJobWhenSaveImageReturnsNull(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenReturn(null);

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void postDoesNotEnqueueAJobWhenSaveImageThrows(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenThrow(new DataException("Save failed"));

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  /**
   * Issue #1189: before this the widget was POST-only, so /admin/images had no upload control at
   * all and a GET fell through to GenericWidget.execute()'s "MUST OVERRIDE" error branch.
   */
  @Test
  void executeShowsTheDropZoneWithTheConfiguredUploadCeiling() {
    WidgetContext context = newContext();

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("26214400");

      WidgetContext result = new ImageUploadWidget().execute(context);

      assertNotNull(result);
      assertEquals("/cms/image-upload-drop-zone.jsp", result.getJsp());
      // Dropzone.js expects whole megabytes, so 26214400 bytes has to reach the JSP as 25
      verify(request).setAttribute("maxUploadSize", "25");
    }
  }

  @Test
  void executeFallsBackToTheDefaultCeilingWhenThePropertyIsUnset() {
    WidgetContext context = newContext();

    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      new ImageUploadWidget().execute(context);

      verify(request).setAttribute("maxUploadSize", "10");
    }
  }

  /**
   * Issue #1189: a rejected upload has to answer with a real error status and a JSON body.
   *
   * <p>Setting only an error message produces no response body, so the container falls through to
   * a redirect that the caller's XHR follows transparently -- reporting the reloaded page's HTTP
   * 200 back as if the upload had succeeded. Dropzone.js and the editors' image pickers both
   * decide success purely from the status code, so without this a rejected file looked accepted.
   */
  @Test
  void postWithNoFilePartAnswersWithA400AndTheReason() throws Exception {
    when(request.getPart("file")).thenReturn(null);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      assertTrue(result.hasJson());
      assertEquals("{\"error\": \"A file was not found, please choose a file and try again\"}", result.getJson());
      verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  @Test
  void postAnswersWithA400AndTheLimitWhenTheFileIsTooLarge(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("huge.png", 20_971_520L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("10485760");

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      assertTrue(result.hasJson());
      // The real ceiling, not a blanket "wrong file type" -- this is the message an admin needs
      assertEquals("{\"error\": \"The file exceeds the maximum allowed upload size of 10 MB\"}", result.getJson());
      verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
  }

  /**
   * The storage-failure branch used to swallow its exception whole -- no log, no message, no error
   * status -- which is exactly the case that matters when a mounted file server root is not
   * writable by the container. The reason has to reach the (already admin-gated) caller.
   */
  @Test
  void postSurfacesTheUnderlyingReasonWhenTheFileCannotBeWritten(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    doThrow(new IOException("Read-only file system")).when(filePart).write(anyString());
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      assertTrue(result.hasJson());
      assertEquals("{\"error\": \"The file could not be saved: Read-only file system\"}", result.getJson());
      verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void postAnswersWithA400WhenTheImageFailsValidation(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      validate.when(() -> ValidateImageCommand.checkFile(any(Image.class)))
          .thenThrow(new DataException("Could not determine image type"));

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      assertTrue(result.hasJson());
      assertEquals("{\"error\": \"Could not determine image type\"}", result.getJson());
      verify(response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
      jobRequest.verify(() -> BackgroundJobRequest.enqueue(any(ImageVariantJob.class)), never());
    }
  }

  @Test
  void postAnswersWithJsonAndNoErrorStatusOnSuccess(@TempDir Path tempDir) throws Exception {
    Part filePart = mockFilePart("photo.png", 100L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    Image savedImage = new Image();
    savedImage.setId(42L);
    savedImage.setFilename("photo.png");
    savedImage.setWebPath("20260803120000");

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ValidateImageCommand> validate = mockStatic(ValidateImageCommand.class);
        MockedStatic<SaveImageCommand> save = mockStatic(SaveImageCommand.class);
        MockedStatic<BackgroundJobRequest> jobRequest = mockStatic(BackgroundJobRequest.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);
      save.when(() -> SaveImageCommand.saveImage(any(Image.class))).thenReturn(savedImage);

      WidgetContext result = new ImageUploadWidget().post(context);

      assertNotNull(result);
      assertTrue(result.hasJson());
      // A success keeps the {"location": ...} shape the embedded editors already read
      assertTrue(result.getJson().contains("\"location\""));
      verify(response, never()).setStatus(eq(HttpServletResponse.SC_BAD_REQUEST));
    }
  }
}
