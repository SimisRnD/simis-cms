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
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * Proves FolderFileFormWidget -- the /admin/file-form widget that creates a brand-new folder file/link
 * record -- now writes an AuditEventCommand.record(...) call for both the success and failure paths,
 * closing part of the CMMC AU-2 gap for folder-file mutations (issue #502).
 */
class FolderFileFormWidgetTest extends WidgetBase {

  @Test
  void postCreatingANewLinkRecordsAFolderFileCreateSuccessEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "title", "Company Handbook");
    addQueryParameter(widgetContext, "filename", "https://example.com/handbook.pdf");

    FileItem savedFileItem = new FileItem();
    savedFileItem.setId(40L);
    savedFileItem.setFilename("https://example.com/handbook.pdf");

    try (MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(savedFileItem);

      FolderFileFormWidget widget = new FolderFileFormWidget();
      WidgetContext result = widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.SUCCESS), eq("folder_file"), eq("40"),
          eq("https://example.com/handbook.pdf"), any()), times(1));
      org.junit.jupiter.api.Assertions.assertEquals("File was saved", result.getSuccessMessage());
    }
  }

  @Test
  void postWhenSaveFailsRecordsAFolderFileCreateFailureEvent() throws Exception {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "folderId", "1");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "title", "Broken Link");
    addQueryParameter(widgetContext, "filename", "https://example.com/broken.pdf");

    try (MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class)))
          .thenThrow(new DataException("A folder is required"));

      FolderFileFormWidget widget = new FolderFileFormWidget();
      widget.post(widgetContext);

      audit.verify(() -> AuditEventCommand.record(any(WidgetContext.class), eq(AuditEventCommand.CONTENT),
          eq("folder_file.create"), eq(AuditEventCommand.FAILURE), eq("folder_file"), any(),
          eq("https://example.com/broken.pdf"), eq("A folder is required")), times(1));
    }
  }
}
