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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Verifies SaveFilePartCommand.saveFile() -- the server-side upload path behind the folder drop
 * zone -- enforces the configured system.upload.maxBytes ceiling: a file over the limit is
 * rejected before a single byte is written to disk, and a file at the limit is accepted (issue
 * #1198). FileSystemCommand and LoadSitePropertyCommand are statically mocked so no real file I/O
 * or database lookup occurs.
 */
class SaveFilePartCommandTest {

  private final HttpServletRequest request = mock(HttpServletRequest.class);
  private final HttpServletResponse response = mock(HttpServletResponse.class);

  private WidgetContext newContext() {
    WidgetContext context = new WidgetContext(request, response, "widget1", "/admin/folder-file-drop-zone");
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
    fsc.when(() -> FileSystemCommand.generateFileServerSubPath(anyString())).thenReturn("uploads/2026/08/15/");
    fsc.when(() -> FileSystemCommand.generateUniqueFilename(anyLong())).thenReturn("unique-name");
    fsc.when(() -> FileSystemCommand.cleanExtension(anyString())).thenAnswer(inv -> inv.getArgument(0));
    fsc.when(() -> FileSystemCommand.resolveWithinRoot(anyString(), anyString()))
        .thenReturn(tempDir.resolve("upload-target.png").toFile());
  }

  @Test
  void saveFileRejectsAFileOverTheConfiguredCeiling(@TempDir Path tempDir) throws Exception {
    // One byte over the 50 MB ceiling
    Part filePart = mockFilePart("huge.png", 52_428_801L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("52428800");

      Assertions.assertThrows(DataException.class, () -> SaveFilePartCommand.saveFile(context));
      // The file must be rejected before anything is written to disk
      verify(filePart, never()).write(anyString());
    }
  }

  @Test
  void saveFileAcceptsAFileAtTheConfiguredCeiling(@TempDir Path tempDir) throws Exception {
    // Exactly the 50 MB ceiling
    Part filePart = mockFilePart("ok.png", 52_428_800L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("52428800");

      FileItem result = SaveFilePartCommand.saveFile(context);

      Assertions.assertNotNull(result, "A file at the limit must be accepted");
      Assertions.assertEquals(52_428_800L, result.getFileLength());
      verify(filePart).write(anyString());
    }
  }

  @Test
  void saveFileSurfacesTheSpecificOversizeReasonNotTheGenericMessage(@TempDir Path tempDir) throws Exception {
    // One byte over the 50 MB ceiling
    Part filePart = mockFilePart("huge.png", 52_428_801L);
    when(request.getPart("file")).thenReturn(filePart);
    WidgetContext context = newContext();

    try (MockedStatic<FileSystemCommand> fsc = mockStatic(FileSystemCommand.class);
        MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      stubFileSystemCommand(fsc, tempDir);
      props.when(() -> LoadSitePropertyCommand.loadByName("system.upload.maxBytes")).thenReturn("52428800");

      DataException thrown = Assertions.assertThrows(DataException.class,
          () -> SaveFilePartCommand.saveFile(context));
      // The drop-zone widgets show this message to the user (context.setErrorMessage / setJson), so
      // the specific reason must survive saveFile()'s try/catch. Before the fix the deliberate
      // size-limit DataException was caught by the broad catch and masked as the generic
      // "There was an issue with the file", hiding the real reason on bypass uploads (curl / no JS).
      Assertions.assertEquals("The file exceeds the maximum allowed upload size", thrown.getMessage());
    }
  }
}
