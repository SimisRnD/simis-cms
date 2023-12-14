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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.database.DB;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class ContentWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");
    // <p>${uniqueId:sample-content}</p>

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ContentWidget.JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
  }

  @Test
  void executeInLineContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>${uniqueId:another-content}");

    Content content2 = new Content();
    content2.setUniqueId("another-content");
    content2.setContent("<p>This is additional content</p>");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("another-content")))
          .thenReturn(content2);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ContentWidget.JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertTrue(contentHtml.contains("Hello"));
    Assertions.assertTrue(contentHtml.contains("This is additional content"));
  }

  @Test
  void action() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Widgets can have parameters
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p><hr><p>Card 2</p>");
    content.setDraftContent("<p>This is Card 1</p><hr><p>This is Card 2</p>");

    // Execute the widget action
    // Mock DB calls
    Connection jdbcConnection = mock(Connection.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(jdbcConnection);
      try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
        staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
            .thenReturn(content);
        ContentWidget contentWidget = new ContentWidget();
        widgetContext = contentWidget.action(widgetContext);
      }
    }
  }
}