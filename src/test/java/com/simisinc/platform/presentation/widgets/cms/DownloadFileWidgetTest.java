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

import java.util.Collections;
import java.util.List;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadFileCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.FileVersion;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FileVersionRepository;
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
    record.setWebPath("20240101010101");
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
    record.setWebPath("20240101010101");
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
    record.setWebPath("20240101010101");
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
    record.setWebPath("20240101010101");
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

  @Test
  void archivedVersionRequestStreamsTheVersionsOwnBytesNotTheLiveRecords() throws IOException {
    // Proves the fix: a request whose web path belongs to an archived version (not the file's
    // current one) must resolve and stream that version's own file/mime/name -- not silently fall
    // through to whatever the live record currently points to.
    setRoles(widgetContext, ADMIN);
    setRequestUri("20230101010101-9/old-report.pdf");

    Path tempDir = Files.createTempDirectory("download-file-widget-version-test");
    File archivedFile = new File(tempDir.toFile(), "old-report.pdf");
    Files.writeString(archivedFile.toPath(), "archived contents");

    FileItem record = new FileItem();
    record.setId(9L);
    record.setWebPath("20240101010101"); // current version's web path -- differs from the request
    record.setFileType("pdf");
    record.setFilename("report.pdf");
    record.setFileServerPath("/current/report.pdf");
    record.setMimeType("application/pdf");

    FileVersion version = new FileVersion();
    version.setId(50L);
    version.setFileId(9L);
    version.setWebPath("20230101010101");
    version.setFileServerPath("/old-report.pdf");
    version.setMimeType("application/pdf");
    version.setFilename("old-report.pdf");
    version.setCreated(new Timestamp(System.currentTimeMillis()));

    ServletOutputStream outputStream = mock(ServletOutputStream.class);
    when(response.getOutputStream()).thenReturn(outputStream);

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileSystemCommand> fileSystemMockedStatic = mockStatic(FileSystemCommand.class);
        MockedStatic<FileItemRepository> fileItemRepositoryMockedStatic = mockStatic(FileItemRepository.class);
        MockedStatic<FileVersionRepository> fileVersionRepositoryMockedStatic = mockStatic(FileVersionRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(9L)).thenReturn(record);
      fileSystemMockedStatic.when(FileSystemCommand::getFileServerRootPath).thenReturn(tempDir.toString());
      List<FileVersion> versionList = Collections.singletonList(version);
      fileVersionRepositoryMockedStatic.when(() -> FileVersionRepository.findAll(any(), eq(null)))
          .thenReturn(versionList);

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertTrue(result.handledResponse());
      // The audit event's file label proves the VERSION's filename was used, not the live record's
      // ("report.pdf") -- if the version resolution were silently skipped, this would read "report.pdf"
      // and the stream would come from /current/report.pdf instead.
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.SUCCESS),
          eq("folder_file"), eq("9"), eq("old-report.pdf"), eq(null)), times(1));
    } finally {
      archivedFile.delete();
      tempDir.toFile().delete();
    }
  }

  @Test
  void archivedVersionNotFoundRecordsAFailureAuditEventAndReturnsNull() {
    // The access check earlier in the widget already matched this web path to either the live
    // record or a file_versions row, so an empty result here means the version was removed after
    // that check -- not a permissions gap. Confirms that path fails safely rather than falling
    // through to the live record's bytes.
    setRoles(widgetContext, ADMIN);
    setRequestUri("20230101010101-10/old-report.pdf");

    FileItem record = new FileItem();
    record.setId(10L);
    record.setWebPath("20240101010101");
    record.setFileType("pdf");
    record.setFilename("report.pdf");

    try (MockedStatic<LoadFileCommand> loadFileMockedStatic = mockStatic(LoadFileCommand.class);
        MockedStatic<FileVersionRepository> fileVersionRepositoryMockedStatic = mockStatic(FileVersionRepository.class);
        MockedStatic<AuditEventCommand> auditMockedStatic = mockStatic(AuditEventCommand.class)) {
      loadFileMockedStatic.when(() -> LoadFileCommand.loadItemById(10L)).thenReturn(record);
      fileVersionRepositoryMockedStatic.when(() -> FileVersionRepository.findAll(any(), eq(null)))
          .thenReturn(Collections.emptyList());

      DownloadFileWidget widget = new DownloadFileWidget();
      WidgetContext result = widget.execute(widgetContext);

      Assertions.assertNull(result);
      auditMockedStatic.verify(() -> AuditEventCommand.record(any(WidgetContext.class),
          eq(AuditEventCommand.DATA_ACCESS), eq("folder_file.download"), eq(AuditEventCommand.FAILURE),
          eq("folder_file"), eq("10"), eq(null), any()), times(1));
    }
  }
}
