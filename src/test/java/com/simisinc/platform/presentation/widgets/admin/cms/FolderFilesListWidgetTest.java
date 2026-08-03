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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;

import org.apache.commons.beanutils.ConvertUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.SaveFileCommand;
import com.simisinc.platform.application.cms.SaveFilePartCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.presentation.controller.SqlTimestampConverter;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * post()'s "form update of an old version" branch populates the bean with
 * BeanUtils.populate(fileItemBean, context.getParameterMap()), then re-parses "expirationDate"
 * explicitly (mirrors WebPageFormWidget.post()'s publishAt/expiresAt handling) -- BeanUtils cannot
 * reliably convert a raw datetime-local string ("2026-09-01T14:30") to a java.sql.Timestamp.
 *
 * Two pieces of global/process-wide state make this widget hard to unit test without extra setup;
 * both are pre-existing behavior of this widget, not something this test works around by accident:
 *
 * 1. PageServlet.init() registers a global, null-swallowing SqlTimestampConverter for
 *    java.sql.Timestamp at real application startup (pattern "MM-dd-yyyy HH:mm", constructed with a
 *    null default so a failed parse returns null instead of throwing). That registration mutates
 *    commons-beanutils' static ConvertUtils registry -- outside a running PageServlet it is not
 *    guaranteed to be registered, and without it BeanUtils.populate() throws ConversionException
 *    (verified directly: commons-beanutils' own default converter for java.sql.Timestamp has no
 *    default value, so it throws rather than swallowing). Each test below registers the same
 *    converter PageServlet.init() does, so behavior doesn't depend on whichever other test happened
 *    to run first in the same JVM.
 * 2. SaveFilePartCommand.saveFile() calls FileSystemCommand.getFileServerRootPath() -- which can
 *    reach LoadSitePropertyCommand.loadByName() and a real DB connection via CacheManager's loading
 *    cache -- unconditionally, before it ever checks whether a "file" part was submitted. A plain
 *    metadata edit (no new file version) never needs that lookup, so SaveFilePartCommand is mocked
 *    out below to isolate these tests from it, rather than depending on FileSystemCommand's static
 *    path cache already being warm from an earlier test.
 */
class FolderFilesListWidgetTest extends WidgetBase {

  @BeforeEach
  void registerTimestampConverter() {
    SqlTimestampConverter converter = new SqlTimestampConverter(null);
    converter.setPattern("MM-dd-yyyy HH:mm");
    ConvertUtils.register(converter, Timestamp.class);
  }

  private void setUpMetadataEditRequest() {
    setRoles(widgetContext, ADMIN);
    when(request.getParameter("currentFolderId")).thenReturn("10");
    when(request.getParameter("currentSubFolderId")).thenReturn("-1");
    addQueryParameter(widgetContext, "id", "42");
    addQueryParameter(widgetContext, "folderId", "10");
    addQueryParameter(widgetContext, "subFolderId", "-1");
    addQueryParameter(widgetContext, "categoryId", "-1");
    addQueryParameter(widgetContext, "title", "Employee Handbook");
    addQueryParameter(widgetContext, "filename", "handbook.pdf");
    addQueryParameter(widgetContext, "version", "1.0");
  }

  @Test
  void postParsesAndPersistsAValidExpirationDate() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "2026-09-01T14:30");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      // No new file uploaded -> post() takes the "form update of an old version" (BeanUtils.populate) branch
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FileItem saved = new FileItem();
      saved.setId(42L);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(saved);

      new FolderFilesListWidget().post(widgetContext);

      ArgumentCaptor<FileItem> captor = ArgumentCaptor.forClass(FileItem.class);
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(captor.capture()), times(1));
      Assertions.assertEquals(Timestamp.valueOf("2026-09-01 14:30:00"), captor.getValue().getExpirationDate());
    }
  }

  @Test
  void postLeavesExpirationDateNullWhenTheFieldIsBlank() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);

      FileItem saved = new FileItem();
      saved.setId(42L);
      saveFileCommand.when(() -> SaveFileCommand.saveFile(any(FileItem.class))).thenReturn(saved);

      new FolderFilesListWidget().post(widgetContext);

      ArgumentCaptor<FileItem> captor = ArgumentCaptor.forClass(FileItem.class);
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(captor.capture()), times(1));
      Assertions.assertNull(captor.getValue().getExpirationDate());
    }
  }

  @Test
  void postRejectsAMalformedExpirationDateWithoutCrashingAndDoesNotSave() throws Exception {
    setUpMetadataEditRequest();
    addQueryParameter(widgetContext, "expirationDate", "not-a-date");

    try (MockedStatic<SaveFilePartCommand> saveFilePartCommand = mockStatic(SaveFilePartCommand.class);
        MockedStatic<SaveFileCommand> saveFileCommand = mockStatic(SaveFileCommand.class)) {
      saveFilePartCommand.when(() -> SaveFilePartCommand.saveFile(widgetContext)).thenReturn(null);
      // The parse failure is caught by post()'s existing AppException|DataException handler, which
      // calls SaveFilePartCommand.cleanupFile(fileItemBean) -- a mocked static's void methods are
      // no-ops by default, so this doesn't need an explicit stub.

      WidgetContext result = new FolderFilesListWidget().post(widgetContext);

      Assertions.assertEquals("Expiration date format is not valid", result.getErrorMessage());
      saveFileCommand.verify(() -> SaveFileCommand.saveFile(any(FileItem.class)), never());
    }
  }
}
