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
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class ContentRevealWidgetTest extends WidgetBase {

  @Test
  void executeRevealContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");
    preferences.put("view", "reveal");
    preferences.put("addReveal", "true");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent(
        "<p><img src=\"/assets/img/1604951037677-235/Example.png\" alt=\"Example1\" width=\"600\" height=\"695\" /></p>\n"
            +
            "<h5><a href=\"#reveal-example-bio\">Example Name</a></h5>\n" +
            "<p>Director<br />of Development</p>\n" +
            "<p><a href=\"mailto:example@example.com\"><span class=\"fas fa-envelope-square tinymce-noedit\">&nbsp;</span></a> <a href=\"#my-example\" target=\"_blank\" rel=\"noopener\"><span class=\"fab fa-linkedin tinymce-noedit\">&nbsp;</span></a></p>\n"
            +
            "<hr />\n" +
            "<p><img src=\"/assets/img/1564502222206-109/Example2.jpg\" alt=\"Brian Donahue\" width=\"600\" height=\"695\" /></p>\n"
            +
            "<h5><a href=\"#reveal-example2-bio\">Example Name2</a></h5>\n" +
            "<p>Asst. Director<br />of Development</p>\n" +
            "<p><a href=\"mailto:example2@example.com\"><span class=\"fas fa-envelope-square tinymce-noedit\">&nbsp;</span></a> <a href=\"#my-example-2\" target=\"_blank\" rel=\"noopener\"><span class=\"fab fa-linkedin tinymce-noedit\">&nbsp;</span></a></p>\n"
            +
            "<hr />");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      ContentRevealWidget contentWidget = new ContentRevealWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);

    List<String> cardList = (List) request.getAttribute("cardList");
    Assertions.assertNotNull(cardList);
    Assertions.assertEquals(2, cardList.size());

    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ContentRevealWidget.REVEAL_JSP, widgetContext.getJsp());
  }
}