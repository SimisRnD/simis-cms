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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadSubFolderCommand;
import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.domain.model.cms.SubFolder;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;

/**
 * The photo-gallery slider loads folder title and photo titles from /json/photoList and writes them
 * into the slider via innerHTML. JSON-encoding is not HTML-safe, so a crafted subfolder name or file
 * title could inject markup (stored DOM XSS). This verifies the endpoint HTML-encodes those fields.
 *
 * @author Elizabeth Houser
 */
class PhotoListAjaxTest extends WidgetBase {

  @Test
  void folderAndPhotoTitlesAreHtmlEncoded() {
    addQueryParameter(widgetContext, "subFolderId", "5");

    SubFolder folder = new SubFolder();
    folder.setId(5L);
    folder.setName("Trip \"><img src=x onerror=alert(1)>");

    FileItem file = new FileItem();
    file.setId(9L);
    file.setFolderId(1L);
    file.setSubFolderId(5L);
    file.setTitle("Photo <script>alert(1)</script>");
    file.setFilename("photo.jpg");
    file.setCreated(new Timestamp(0L));

    try (MockedStatic<LoadSubFolderCommand> subFolders = mockStatic(LoadSubFolderCommand.class);
        MockedStatic<FileItemRepository> files = mockStatic(FileItemRepository.class)) {
      subFolders.when(() -> LoadSubFolderCommand.loadSubFolderByIdForAuthorizedUser(anyLong(), anyLong())).thenReturn(folder);
      files.when(() -> FileItemRepository.findAll(any(), any())).thenReturn(List.of(file));

      new PhotoListAjax().execute(widgetContext);

      String json = widgetContext.getJson();
      Assertions.assertNotNull(json);
      Assertions.assertFalse(json.contains("<img"), "raw markup must not appear: " + json);
      Assertions.assertFalse(json.contains("<script"), "raw markup must not appear: " + json);
      Assertions.assertTrue(json.contains("&lt;img"), "folder name must be HTML-encoded: " + json);
      Assertions.assertTrue(json.contains("&lt;script"), "photo title must be HTML-encoded: " + json);
    }
  }
}
