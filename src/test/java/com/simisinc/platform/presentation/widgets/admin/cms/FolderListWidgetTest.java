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
import com.simisinc.platform.domain.model.cms.Folder;
import com.simisinc.platform.infrastructure.persistence.cms.FolderRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class FolderListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"folderList\">\n" +
        "  <title>Folders</title>\n" +
        "</widget>");

    List<Folder> folderList = new ArrayList<>();
    Folder folder = new Folder();
    folder.setId(1L);
    folderList.add(folder);

    try (MockedStatic<FolderRepository> folderRepositoryMockedStatic = mockStatic(FolderRepository.class)) {
      folderRepositoryMockedStatic.when(FolderRepository::findAll).thenReturn(folderList);

      // Use admin
      setRoles(widgetContext, ADMIN);

      // Execute the widget
      FolderListWidget widget = new FolderListWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(FolderListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("Folders", request.getAttribute("title"));
    List<Folder> folderListRequest = (List) request.getAttribute("folderList");
    Assertions.assertEquals(folder.getId(), folderListRequest.get(0).getId());
  }
}