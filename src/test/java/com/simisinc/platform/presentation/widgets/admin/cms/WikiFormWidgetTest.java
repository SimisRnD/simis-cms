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
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class WikiFormWidgetTest extends WidgetBase {

  @Test
  void execute() {
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"wikiForm\" />");

    Wiki wiki = new Wiki();
    wiki.setId(1L);

    addQueryParameter(widgetContext, "wikiId", String.valueOf(wiki.getId()));

    try (MockedStatic<WikiRepository> wikiRepositoryMockedStatic = mockStatic(WikiRepository.class)) {
      wikiRepositoryMockedStatic.when(() -> WikiRepository.findById(wiki.getId())).thenReturn(wiki);

      WikiFormWidget widget = new WikiFormWidget();
      widget.execute(widgetContext);
    }

    // Verify
    Wiki wikiRequest = (Wiki) request.getAttribute("wiki");
    Assertions.assertEquals(wiki.getId(), wikiRequest.getId());
    Assertions.assertEquals(WikiFormWidget.JSP, widgetContext.getJsp());
  }

  @Test
  void post() {
    Wiki wiki = new Wiki();
    wiki.setId(1L);

    addQueryParameter(widgetContext, "widget", widgetContext.getUniqueId());
    addQueryParameter(widgetContext, "token", "12345");
    addQueryParameter(widgetContext, "id", String.valueOf(wiki.getId()));
    addQueryParameter(widgetContext, "name", "Wiki Name");
    addQueryParameter(widgetContext, "description", "Wiki Description");
    addQueryParameter(widgetContext, "enabled", "true");

    try {
      try (MockedStatic<WikiRepository> wikiRepositoryMockedStatic = mockStatic(WikiRepository.class)) {
        wikiRepositoryMockedStatic.when(() -> WikiRepository.findById(wiki.getId())).thenReturn(wiki);
        wikiRepositoryMockedStatic.when(() -> WikiRepository.save(any())).thenReturn(wiki);

        WikiFormWidget widget = new WikiFormWidget();
        widget.post(widgetContext);

        Assertions.assertNull(widgetContext.getSharedRequestValueMap());
      }
    } catch (InvocationTargetException | IllegalAccessException e) {
      fail(e.getMessage());
    }
  }
}