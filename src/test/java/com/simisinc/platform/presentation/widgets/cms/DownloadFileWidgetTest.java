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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;

import jakarta.servlet.ServletOutputStream;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Proves DownloadFileWidget -- the widget that actually serves folder-file bytes to a browser -- now
 * writes an AuditEventCommand.record(...) call for every outcome (found-and-served, not-found/no-access,
 * and the server-file-missing case), closing the CMMC AU-2 gap where a file view/download previously only
 * incremented a bare numeric counter with no audit trail of who accessed it (issue #502).
 */
class DownloadFileWidgetTest extends WidgetBase {

  private void setRequestUri(String suffix) {
    org.mockito.Mockito.when(request.getRequestURI()).thenReturn("/example/path/" + suffix);
  }

  @Test
  void recordNotFoundOrNoAccessRecordsAFailureAuditEventAndReturnsNull() {
    setRoles(widgetContext, ADMIN);
    setRequestUri("20240101010101-99/missing.pdf");

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(99L)).thenReturn(null);

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertNull(result);
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.FAILURE),
          eq("folder_file"), eq("99"), eq(null), any()), times(1));
    }
  }

  @Test
  void urlTypeFileRedirectsAndRecordsADownloadSuccessEventWhenNotViewing() {
    setRoles(widgetContext, ADMIN);
    setRequestUri("20240101010101-5/link");

    FileItem record = new FileItem();
    record.setId(5L);
    record.setFileType("URL");
    record.setFilename("https://example.com/doc.pdf");

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileItemRepository> fileItemRepositoryMockedStatic = mockStatic(FileItemRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(5L)).thenReturn(record);

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertEquals("https://example.com/doc.pdf", result.getRedirect());
      fileItemRepositoryMockedStatic.verify(() -> FileItemRepository.incrementDownloadCount(record));
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.SUCCESS),
          eq("folder_file"), eq("5"), eq("https://example.com/doc.pdf"), any()), times(1));
    }
  }

  @Test
  void urlTypeFileRecordsAViewEventWhenTheViewPreferenceIsSet() {
    setRoles(widgetContext, ADMIN);
    setRequestUri("20240101010101-6/link");
    widgetContext.getPreferences().put("view", "true");

    FileItem record = new FileItem();
    record.setId(6L);
    record.setFileType("URL");
    record.setFilename("https://example.com/other.pdf");

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileItemRepository> fileItemRepositoryMockedStatic = mockStatic(FileItemRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(6L)).thenReturn(record);

      DownloadFileWidget widget = new DownloadFileWidget();
      widget.execute(widgetContext);

      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.view"), eq(AuditEventCommand.SUCCESS),
          eq("folder_file"), eq("6"), eq("https://example.com/other.pdf"), any()), times(1));
    }
  }

  @Test
  void serverFileMissingRecordsAFailureAuditEventAndReturnsNull() {
    setRoles(widgetContext, ADMIN);
    setRequestUri("20240101010101-7/report.pdf");

    FileItem record = new FileItem();
    record.setId(7L);
    record.setFileType("pdf");
    record.setFilename("report.pdf");
    record.setFileServerPath("/does-not-exist/report.pdf");

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystemMockedStatic = mockStatic(FileSystemCommand.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(7L)).thenReturn(record);
      fileSystemMockedStatic.when(FileSystemCommand::getFileServerRootPath).thenReturn("/tmp/no-such-root");

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertNull(result);
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.FAILURE),
          eq("folder_file"), eq("7"), eq("report.pdf"), any()), times(1));
    }
  }

  @Test
  void fullDownloadStreamsTheFileAndRecordsADownloadSuccessEvent() throws IOException {
    setRoles(widgetContext, ADMIN);
    setRequestUri("20240101010101-8/report.pdf");

    Path tempDir = Files.createTempDirectory("download-file-widget-test");
    File tempFile = new File(tempDir.toFile(), "report.pdf");
    Files.writeString(tempFile.toPath(), "hello world");

    FileItem record = new FileItem();
    record.setId(8L);
    record.setFileType("pdf");
    record.setFilename("report.pdf");
    record.setFileServerPath("/report.pdf");
    record.setMimeType("application/pdf");
    record.setModified(new Timestamp(System.currentTimeMillis()));

    ServletOutputStream outputStream = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(outputStream);

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystemMockedStatic = mockStatic(FileSystemCommand.class);
        MockedStatic<FileItemRepository> fileItemRepositoryMockedStatic = mockStatic(FileItemRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(8L)).thenReturn(record);
      fileSystemMockedStatic.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString());

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertTrue(result.handledResponse());
      fileItemRepositoryMockedStatic.verify(() -> FileItemRepository.incrementDownloadCount(record));
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.SUCCESS),
          eq("folder_file"), eq("8"), eq("report.pdf"), eq(null)), times(1));
    } finally {
      tempFile.delete();
      tempDir.toFile().delete();
    }
  }
}
