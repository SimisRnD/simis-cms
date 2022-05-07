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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.ContentTab;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class ContentTabsWidgetTest extends WidgetBase {

  @Test
  void execute() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"contentTabs\">\n" +
            "  <smudge>false</smudge>\n" +
            "  <tabs>\n" +
            "    <tab name=\"Work From Home Skills Training\" linkId=\"content-tab-1\" contentUniqueId=\"workforce-programs-tab-home-skills-content\" />\n" +
            "    <tab name=\"Digital Literacy\" linkId=\"content-tab-2\" contentUniqueId=\"workforce-programs-tab-digital-literacy-content\" />\n" +
            "    <tab name=\"Skills to Succeed\" linkId=\"content-tab-3\" contentUniqueId=\"workforce-programs-tab-skills-succeed-content\" enabled=\"false\" />\n" +
            "    <tab name=\"Training\" linkId=\"content-tab-4\" contentUniqueId=\"workforce-programs-tab-training-content\" />\n" +
            "  </tabs>\n" +
            "</widget>");

    PreferenceEntriesList entriesList = widgetContext.getPreferenceAsDataList("tabs");
    Assertions.assertFalse(entriesList.isEmpty());

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("workforce-programs-tab-training-content");
    content.setContent("<p>Hello</p>");

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId("workforce-programs-tab-home-skills-content")).thenReturn(null);
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId("workforce-programs-tab-digital-literacy-content")).thenReturn(null);
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId("workforce-programs-tab-training-content")).thenReturn(content);
      ContentTabsWidget widget = new ContentTabsWidget();
      widget.execute(widgetContext);
    }

    ArrayList<ContentTab> contentTabList = (ArrayList<ContentTab>) request.getAttribute("contentTabList");
    Assertions.assertNotNull(contentTabList);
    Assertions.assertEquals(1, contentTabList.size());

    ContentTab contentTab = contentTabList.get(0);
    Assertions.assertEquals("Training", contentTab.getName());
    Assertions.assertEquals("content-tab-4", contentTab.getLinkId());
    Assertions.assertEquals("workforce-programs-tab-training-content", contentTab.getContentUniqueId());
    Assertions.assertNotNull(contentTab.getHtml());
    Assertions.assertFalse(contentTab.getIsActive());

    Assertions.assertEquals("false", request.getAttribute("smudge"));
    Assertions.assertEquals(ContentTabsWidget.JSP, widgetContext.getJsp());
  }
}