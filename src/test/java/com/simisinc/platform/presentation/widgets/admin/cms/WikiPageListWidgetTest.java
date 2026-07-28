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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;

/**
 * Covers {@link WikiPageListWidget}. Before this widget existed,
 * {@link WikiPageRepository#findAll(com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification, com.simisinc.platform.infrastructure.database.DataConstraints)}
 * had never had a caller anywhere in the app.
 *
 * @author SimIS
 * @created 7/28/2026
 */
class WikiPageListWidgetTest extends WidgetBase {

  @Test
  void listsThePagesForTheRequestedWiki() {
    Wiki wiki = new Wiki();
    wiki.setId(5L);
    wiki.setUniqueId("docs");

    WikiPage page = new WikiPage();
    page.setId(1L);
    page.setTitle("Setup Guide");
    page.setUniqueId("setup-guide");

    addQueryParameter(widgetContext, "wikiId", "5");

    try (MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      wikiRepository.when(() -> WikiRepository.findById(5L)).thenReturn(wiki);
      wikiPageRepository.when(() -> WikiPageRepository.findAll(any(), any())).thenReturn(List.of(page));

      new WikiPageListWidget().execute(widgetContext);
    }

    Assertions.assertEquals(WikiPageListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals(wiki, request.getAttribute("pageListWiki"));
    List<WikiPage> wikiPageList = (List<WikiPage>) request.getAttribute("wikiPageList");
    Assertions.assertEquals(1, wikiPageList.size());
    Assertions.assertEquals("Setup Guide", wikiPageList.get(0).getTitle());
  }

  @Test
  void doesNothingWithoutAWikiIdParameter() {
    new WikiPageListWidget().execute(widgetContext);

    Assertions.assertNull(widgetContext.getJsp());
  }

  @Test
  void doesNothingWhenTheWikiIsNotFound() {
    addQueryParameter(widgetContext, "wikiId", "999");

    try (MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class)) {
      wikiRepository.when(() -> WikiRepository.findById(999L)).thenReturn(null);

      new WikiPageListWidget().execute(widgetContext);
    }

    Assertions.assertNull(widgetContext.getJsp());
  }
}
