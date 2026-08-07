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

package com.simisinc.platform.application.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveBotUserAgentCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.BotUserAgent;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.BotUserAgentRepository;

/**
 * Proves ProcessBotListCSVFileCommand.processCSV() (a) only counts a row as imported when
 * SaveBotUserAgentCommand.save() actually returns a saved record, rather than discarding the return
 * value and unconditionally incrementing the counter, and (b) isolates each row's processing in its
 * own try/catch, so one bad row (e.g. a blank "Partial User Agent" cell) is skipped and accounted for
 * instead of aborting every row after it with no partial-success reporting.
 */
class ProcessBotListCSVFileCommandTest extends WidgetBase {

  private Path csvFile;

  @AfterEach
  void cleanup() throws Exception {
    if (csvFile != null) {
      Files.deleteIfExists(csvFile);
    }
  }

  @Test
  void aRowWhereSaveReturnsNullIsNotCountedAsImported() throws Exception {
    csvFile = Files.createTempFile("bot-list-import", ".csv");
    Files.write(csvFile, ("Partial User Agent,Label\n" +
        "GoodBot1,Label1\n" +
        "GoodBot2,Label2\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/bot-list-import.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<SaveBotUserAgentCommand> saveCommand = mockStatic(SaveBotUserAgentCommand.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/tmp/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenReturn(csvFile.toFile());
      // No existing duplicates -- BotUserAgentRepository is fully mocked, so unstubbed calls
      // (e.g. findByUserAgent()) default to returning null

      // Simulate save() returning null for one row, as it does on a caught DB-level failure inside
      // BotUserAgentRepository -- the other row persists normally
      saveCommand.when(() -> SaveBotUserAgentCommand.save(any(BotUserAgent.class))).thenAnswer(invocation -> {
        BotUserAgent candidate = invocation.getArgument(0);
        if ("GoodBot2".equals(candidate.getUserAgent())) {
          return null;
        }
        BotUserAgent saved = new BotUserAgent();
        saved.setId(1L);
        saved.setUserAgent(candidate.getUserAgent());
        return saved;
      });

      ProcessBotListCSVFileCommand.ImportResult result = ProcessBotListCSVFileCommand.processCSV(widgetContext);

      Assertions.assertEquals(1, result.getRecordCount(), "Only the row that actually persisted should be counted");
      Assertions.assertEquals(1, result.getSkippedCount());
      Assertions.assertEquals(2, result.getTotalRowCount());
    }
  }

  @Test
  void aBadRowIsSkippedWithoutAbortingTheRemainingRows() throws Exception {
    csvFile = Files.createTempFile("bot-list-import", ".csv");
    Files.write(csvFile, ("Partial User Agent,Label\n" +
        "GoodBot1,Label1\n" +
        ",BlankRowLabel\n" +
        "GoodBot3,Label3\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/bot-list-import.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<BotUserAgentRepository> repository = mockStatic(BotUserAgentRepository.class);
        MockedStatic<SaveBotUserAgentCommand> saveCommand = mockStatic(SaveBotUserAgentCommand.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/tmp/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
          .thenReturn(csvFile.toFile());

      // Mirror the real save()'s behavior for a blank value (throws DataException) without wiring
      // up SaveBotUserAgentCommand's own repository/cache dependencies
      saveCommand.when(() -> SaveBotUserAgentCommand.save(any(BotUserAgent.class))).thenAnswer(invocation -> {
        BotUserAgent candidate = invocation.getArgument(0);
        if (StringUtils.isBlank(candidate.getUserAgent())) {
          throw new DataException("A partial user agent value is required");
        }
        BotUserAgent saved = new BotUserAgent();
        saved.setId(2L);
        saved.setUserAgent(candidate.getUserAgent());
        return saved;
      });

      ProcessBotListCSVFileCommand.ImportResult result = ProcessBotListCSVFileCommand.processCSV(widgetContext);

      Assertions.assertEquals(2, result.getRecordCount(), "The two good rows must still be imported");
      Assertions.assertEquals(1, result.getSkippedCount(),
          "The blank row must be counted as skipped, not silently dropped");
      Assertions.assertEquals(3, result.getTotalRowCount());
    }
  }
}
