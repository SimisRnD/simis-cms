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
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
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
class ContentListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"contentList\">\n" +
        "  <title>Content</title>\n" +
        "</widget>");

    List<Content> contentList = new ArrayList<>();
    Content content = new Content();
    content.setId(1L);
    contentList.add(content);

    try (MockedStatic<ContentRepository> contentRepositoryMockedStatic = mockStatic(ContentRepository.class)) {
      contentRepositoryMockedStatic.when(ContentRepository::findAll).thenReturn(contentList);

      // Execute the widget
      ContentListWidget widget = new ContentListWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Assertions.assertEquals(ContentListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("Content", request.getAttribute("title"));
    List<Content> contentListRequest = (List) request.getAttribute("contentList");
    Assertions.assertEquals(content.getId(), contentListRequest.get(0).getId());
  }
}