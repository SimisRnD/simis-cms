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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.persistence.cms.FormUploadRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Verifies {@link FormUploadFileWidget} serves a form upload only once its scan passed, always as a
 * download, and records every request (issue #2130).
 */
class FormUploadFileWidgetTest extends WidgetBase {

  @TempDir
  Path root;

  private final CapturingOutputStream body = new CapturingOutputStream();
  private MockedStatic<FileSystemCommand> fileSystem;
  private MockedStatic<FormUploadRepository> repository;
  private MockedStatic<AuditEventCommand> audit;

  @BeforeEach
  void setUp() throws IOException {
    fileSystem = mockStatic(FileSystemCommand.class, Mockito.CALLS_REAL_METHODS);
    fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn(root.toString() + "/");
    repository = mockStatic(FormUploadRepository.class);
    audit = mockStatic(AuditEventCommand.class);
    when(response.getOutputStream()).thenReturn(body);
    when(request.getMethod()).thenReturn("GET");
    Files.createDirectories(root.resolve("forms/contact-us"));
    Files.write(root.resolve("forms/contact-us/abc.png"), new byte[] { 1, 2, 3 });
    Files.write(root.resolve("forms/contact-us/abc-thumbnail.jpg"), new byte[] { 4, 5 });
  }

  @AfterEach
  void tearDown() {
    audit.close();
    repository.close();
    fileSystem.close();
  }

  private void uploadWithStatus(String scanStatus) {
    FormUpload upload = new FormUpload();
    upload.setId(7L);
    upload.setOriginalFilename("my shot.jpg");
    upload.setFilePath("forms/contact-us/abc.png");
    upload.setContentType("image/png");
    upload.setThumbnailPath("forms/contact-us/abc-thumbnail.jpg");
    upload.setThumbnailContentType("image/jpeg");
    upload.setScanStatus(scanStatus);
    repository.when(() -> FormUploadRepository.findById(7L)).thenReturn(upload);
  }

  private void verifyAudited(String outcome) {
    audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.DATA_ACCESS),
        eq("form_upload.download"), eq(outcome), eq("form_upload"), eq("7"), any(), any()), Mockito.atLeastOnce());
  }

  @Test
  void aCleanUploadIsSentAsADownloadNamedForWhatItIs() {
    uploadWithStatus(FormUpload.SCAN_CLEAN);
    addQueryParameter(widgetContext, "id", "7");

    WidgetContext result = new FormUploadFileWidget().execute(widgetContext);

    assertTrue(result.handledResponse());
    assertArrayEquals(new byte[] { 1, 2, 3 }, body.toByteArray());
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    // The re-encoded file is a PNG, so the visitor's ".jpg" name is corrected
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"my shot.png\"");
    verify(response).setContentType("application/octet-stream");
    verify(response).setHeader("Cache-Control", "private, no-store");
    verifyAudited(AuditEventCommand.SUCCESS);
  }

  @Test
  void anImageStoredWithoutAScanIsSentTheSameWay() {
    uploadWithStatus(FormUpload.SCAN_NOT_REQUIRED);
    addQueryParameter(widgetContext, "id", "7");

    WidgetContext result = new FormUploadFileWidget().execute(widgetContext);

    assertTrue(result.handledResponse());
    assertArrayEquals(new byte[] { 1, 2, 3 }, body.toByteArray());
    verify(response).setHeader("X-Content-Type-Options", "nosniff");
    verify(response).setHeader("Content-Disposition", "attachment; filename=\"my shot.png\"");
    verifyAudited(AuditEventCommand.SUCCESS);
  }

  @Test
  void theThumbnailIsSentInlineWithTheSandboxingHeaders() {
    uploadWithStatus(FormUpload.SCAN_CLEAN);
    addQueryParameter(widgetContext, "id", "7");
    addQueryParameter(widgetContext, "thumbnail", "true");

    new FormUploadFileWidget().execute(widgetContext);

    assertArrayEquals(new byte[] { 4, 5 }, body.toByteArray());
    verify(response).setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
    verify(response).setContentType("image/jpeg");
  }

  @Test
  void anUploadWhoseScanHasNotPassedIsRefusedWith403() {
    for (String status : new String[] { FormUpload.SCAN_PENDING, FormUpload.SCAN_REJECTED, "anything else" }) {
      Mockito.clearInvocations(response);
      uploadWithStatus(status);
      addQueryParameter(widgetContext, "id", "7");

      WidgetContext result = new FormUploadFileWidget().execute(widgetContext);

      assertTrue(result.handledResponse());
      verify(response).setStatus(HttpServletResponse.SC_FORBIDDEN);
      assertEquals(0, body.size(), "no bytes for status " + status);
    }
    verifyAudited(AuditEventCommand.FAILURE);
  }

  @Test
  void anUnknownIdIsNotFound() throws IOException {
    addQueryParameter(widgetContext, "id", "404");

    assertNull(new FormUploadFileWidget().execute(widgetContext));
    verify(response, never()).getOutputStream();
  }

  @Test
  void aCleanRecordWhoseFileIsGoneIsNotFound() throws IOException {
    uploadWithStatus(FormUpload.SCAN_CLEAN);
    Files.delete(root.resolve("forms/contact-us/abc.png"));
    addQueryParameter(widgetContext, "id", "7");

    assertNull(new FormUploadFileWidget().execute(widgetContext));
    verifyAudited(AuditEventCommand.FAILURE);
  }

  @Test
  void aStoredPathThatLeavesTheFileRootIsNotFollowed() throws IOException {
    FormUpload upload = new FormUpload();
    upload.setId(7L);
    upload.setFilePath("../outside.png");
    upload.setScanStatus(FormUpload.SCAN_CLEAN);
    repository.when(() -> FormUploadRepository.findById(7L)).thenReturn(upload);
    addQueryParameter(widgetContext, "id", "7");

    assertNull(new FormUploadFileWidget().execute(widgetContext));
    verify(response, never()).getOutputStream();
  }

  @Test
  void theDownloadNameKeepsTheVisitorsNameWithTheStoredExtension() {
    FormUpload upload = new FormUpload();
    upload.setFilePath("forms/x/abc.webp");
    upload.setOriginalFilename("IMG_0001.HEIC.jpg");
    assertEquals("IMG_0001.HEIC.webp", FormUploadFileWidget.downloadName(upload));
    upload.setOriginalFilename(null);
    assertEquals("attachment.webp", FormUploadFileWidget.downloadName(upload));
  }

  private static final class CapturingOutputStream extends ServletOutputStream {
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    @Override
    public void write(int b) {
      bytes.write(b);
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setWriteListener(WriteListener listener) {
    }

    byte[] toByteArray() {
      return bytes.toByteArray();
    }

    int size() {
      return bytes.size();
    }
  }
}
