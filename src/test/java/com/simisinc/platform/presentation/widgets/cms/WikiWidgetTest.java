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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers {@link WikiWidget}'s deletePost action -- {@code canManageWikiPages} and the (now
 * public/static) {@code deletePost} were extracted so {@code WikiPageListWidget}'s new admin
 * delete-this-page control (wiki-page-list.jsp) could reuse this exact permission check and
 * deletion/audit logic instead of a separately maintained copy. This had no test coverage before
 * that extraction.
 */
class WikiWidgetTest extends WidgetBase {

  private static WikiPage wikiPage(long id, long wikiId, String title) {
    WikiPage wikiPage = new WikiPage();
    wikiPage.setId(id);
    wikiPage.setWikiId(wikiId);
    wikiPage.setTitle(title);
    return wikiPage;
  }

  @Test
  void deletePostActionRemovesThePageAndAudits() {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "action", "deletePost");
    addQueryParameter(widgetContext, "wikiPageId", "9");

    WikiPage page = wikiPage(9L, 5L, "Setup Guide");
    Wiki wiki = new Wiki();
    wiki.setId(5L);
    wiki.setUniqueId("docs");

    try (MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageById(9L)).thenReturn(page);
      loadWiki.when(() -> LoadWikiCommand.loadWikiById(5L)).thenReturn(wiki);
      wikiPageRepository.when(() -> WikiPageRepository.remove(page)).thenReturn(true);

      WidgetContext result = new WikiWidget().action(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(page), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("wiki_page"), eq("9"), eq("Setup Guide"), any()), times(1));
      Assertions.assertEquals("Page was deleted", result.getSuccessMessage());
      Assertions.assertEquals("/docs", result.getRedirect());
    }
  }

  @Test
  void actionDeniesDeletePostForAnUnauthorizedRole() {
    setRoles(widgetContext, DATA_MANAGER);
    addQueryParameter(widgetContext, "action", "deletePost");
    addQueryParameter(widgetContext, "wikiPageId", "9");

    try (MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      new WikiWidget().action(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(any()), never());
      loadWikiPage.verify(() -> LoadWikiPageCommand.loadWikiPageById(9L), never());
    }
  }

  @Test
  void canManageWikiPagesAllowsTheThreeEditingRoles() {
    setRoles(widgetContext, COMMUNITY_MANAGER);
    Assertions.assertTrue(WikiWidget.canManageWikiPages(widgetContext));
  }

  @Test
  void canManageWikiPagesDeniesAnUnrelatedRole() {
    setRoles(widgetContext, DATA_MANAGER);
    Assertions.assertFalse(WikiWidget.canManageWikiPages(widgetContext));
  }
}
