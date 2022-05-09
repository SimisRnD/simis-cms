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
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class WikiListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"wikiList\">\n" +
        "  <title>Wikis</title>\n" +
        "</widget>");

    List<Wiki> wikiList = new ArrayList<>();
    Wiki wiki = new Wiki();
    wiki.setId(1L);
    wikiList.add(wiki);

    try (MockedStatic<WikiRepository> wikiRepositoryMockedStatic = mockStatic(WikiRepository.class)) {
      wikiRepositoryMockedStatic.when(WikiRepository::findAll).thenReturn(wikiList);

      try (MockedStatic<WikiPageRepository> wikiPageRepositoryMockedStatic = mockStatic(WikiPageRepository.class)) {
        wikiPageRepositoryMockedStatic.when(() -> WikiPageRepository.findCount(any())).thenReturn(8L);

        // Use admin
        setRoles(widgetContext, ADMIN);

        // Execute the widget
        WikiListWidget widget = new WikiListWidget();
        widget.execute(widgetContext);
      }
    }

    // Verify
    Assertions.assertEquals(WikiListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("Wikis", request.getAttribute("title"));
    List<Wiki> wikiListRequest = (List) request.getAttribute("wikiList");
    Assertions.assertEquals(wiki.getId(), wikiListRequest.get(0).getId());

    Map<Long, Long> wikiPageCount = (Map) request.getAttribute("wikiPageCount");
    Assertions.assertEquals(8L, wikiPageCount.get(wiki.getId()));
  }

  @Test
  void deleteFail() {
    // Execute the widget
    WikiListWidget widget = new WikiListWidget();
    widget.delete(widgetContext);
  }

  @Test
  void delete() {
    Wiki wiki = new Wiki();
    wiki.setId(1L);

    addQueryParameter(widgetContext, "id", String.valueOf(wiki.getId()));

    setRoles(widgetContext, ADMIN);

    try (MockedStatic<WikiRepository> wikiRepositoryMockedStatic = mockStatic(WikiRepository.class)) {
      wikiRepositoryMockedStatic.when(() -> WikiRepository.findById(wiki.getId())).thenReturn(wiki);
      wikiRepositoryMockedStatic.when(() -> WikiRepository.remove(wiki)).thenReturn(true);

      // Execute the widget
      WikiListWidget widget = new WikiListWidget();
      WidgetContext result = widget.delete(widgetContext);

      // Verify
      Assertions.assertNotNull(result);
      Assertions.assertNull(widgetContext.getWarningMessage());
      Assertions.assertNull(widgetContext.getErrorMessage());
      Assertions.assertNotNull(widgetContext.getSuccessMessage());
    }
  }
}