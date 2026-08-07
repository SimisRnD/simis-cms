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

package com.simisinc.platform.application.admin;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadAllowedIPListCommand;
import com.simisinc.platform.application.cms.LoadBlockedIPListCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Proves ProcessBlockListCSVFileCommand.processCSV() isolates each row: a single malformed row
 * (an invalid IP address) is skipped and counted, rather than throwing and aborting every row
 * after it, and the returned ImportResult carries accurate saved/skipped counts and a readable
 * summary message (fix 2).
 */
class ProcessBlockListCSVFileCommandTest extends WidgetBase {

  private Path csvFile;

  @AfterEach
  void cleanup() throws Exception {
    if (csvFile != null) {
      Files.deleteIfExists(csvFile);
    }
  }

  @Test
  void aMalformedRowIsSkippedWithoutAbortingTheRowsAfterIt() throws Exception {
    csvFile = Files.createTempFile("block-import", ".csv");
    Files.write(csvFile, ("IP Address,Reason\n" +
        "203.0.113.5,First valid entry\n" +
        "not-an-ip,This row is malformed\n" +
        "203.0.113.6,Second valid entry after the bad row\n").getBytes(StandardCharsets.UTF_8),
        StandardOpenOption.TRUNCATE_EXISTING);

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/block-import.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/tmp/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString())).thenReturn(csvFile.toFile());

      blockedRepo.when(() -> BlockedIPRepository.findByIpAddress(anyString())).thenReturn(null);
      blockedRepo.when(() -> BlockedIPRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      ProcessBlockListCSVFileCommand.ImportResult result = ProcessBlockListCSVFileCommand.processCSV(widgetContext);

      Assertions.assertEquals(2, result.getSavedCount(), "both well-formed rows should have been saved");
      Assertions.assertEquals(1, result.getSkippedCount(), "the malformed row should be counted as skipped, not thrown away silently");
      Assertions.assertEquals(0, result.getRemovedCount());
      Assertions.assertEquals(1, result.getSkipDetails().size());
      Assertions.assertTrue(result.getSkipDetails().get(0).contains("Row 2"),
          "the skip detail should identify which row failed");
      Assertions.assertTrue(result.getSkipDetails().get(0).contains("not-an-ip"));

      String summary = result.getSummaryMessage();
      Assertions.assertTrue(summary.contains("2 of 3"), "summary should read '2 of 3': " + summary);
      Assertions.assertTrue(summary.contains("1"), "summary should mention the 1 skipped row: " + summary);
    }
  }

  @Test
  void allValidRowsProduceNoSkipsAndAConciseSummary() throws Exception {
    csvFile = Files.createTempFile("block-import-clean", ".csv");
    Files.write(csvFile, ("IP Address,Reason\n" +
        "203.0.113.5,Valid entry one\n" +
        "203.0.113.6,Valid entry two\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);

    FileItem fileItemBean = new FileItem();
    fileItemBean.setFileServerPath("uploads/block-import-clean.csv");

    try (MockedStatic<SaveFilePartCommand> saveFilePart = mockStatic(SaveFilePartCommand.class);
        MockedStatic<FileSystemCommand> fileSystem = mockStatic(FileSystemCommand.class);
        MockedStatic<BlockedIPRepository> blockedRepo = mockStatic(BlockedIPRepository.class);
        MockedStatic<LoadAllowedIPListCommand> loadAllowed = mockStatic(LoadAllowedIPListCommand.class);
        MockedStatic<LoadBlockedIPListCommand> loadBlocked = mockStatic(LoadBlockedIPListCommand.class)) {

      saveFilePart.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(fileItemBean);
      fileSystem.when(FileSystemCommand::getFileServerRootPath).thenReturn("/tmp/");
      fileSystem.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString())).thenReturn(csvFile.toFile());

      blockedRepo.when(() -> BlockedIPRepository.findByIpAddress(anyString())).thenReturn(null);
      blockedRepo.when(() -> BlockedIPRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
      loadBlocked.when(LoadBlockedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());
      loadAllowed.when(LoadAllowedIPListCommand::retrieveCachedIpAddressList).thenReturn(new ArrayList<>());

      ProcessBlockListCSVFileCommand.ImportResult result = ProcessBlockListCSVFileCommand.processCSV(widgetContext);

      Assertions.assertEquals(2, result.getSavedCount());
      Assertions.assertEquals(0, result.getSkippedCount());
      Assertions.assertTrue(result.getSkipDetails().isEmpty());
      Assertions.assertFalse(result.getSummaryMessage().contains("skipped"));
    }
  }
}
