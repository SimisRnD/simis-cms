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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers {@link WikiPageListWidget}. Before this widget existed,
 * {@link WikiPageRepository#findAll(com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification, com.simisinc.platform.infrastructure.database.DataConstraints)}
 * had never had a caller anywhere in the app.
 *
 * <p>
 * The deletePage tests below cover the new per-row Delete control (wiki-page-list.jsp): before it
 * existed, {@code WikiWidget}'s deletePost action correctly re-checked role but had no admin UI
 * trigger at all, so deleting a single page was only reachable by deleting the entire wiki. The
 * control submits via a real POST (postAction()/confirmPostAction() in main.jsp), which
 * WebContainerContext dispatches to post(), not action(). These tests call post() directly, the
 * same method a real request reaches, mirroring BlogPostWidgetTest's identical regression guard
 * for BlogPostWidget's own POST-submitted deletePost action.
 * </p>
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

  private static WikiPage wikiPage(long id, long wikiId, String title) {
    WikiPage wikiPage = new WikiPage();
    wikiPage.setId(id);
    wikiPage.setWikiId(wikiId);
    wikiPage.setTitle(title);
    return wikiPage;
  }

  @Test
  void deletePageViaPostReusesWikiWidgetsRoleCheckAndAudits() {
    setRoles(widgetContext, CONTENT_MANAGER);
    addQueryParameter(widgetContext, "action", "deletePage");
    addQueryParameter(widgetContext, "wikiPageId", "9");

    WikiPage page = wikiPage(9L, 5L, "Setup Guide");

    try (MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageById(9L)).thenReturn(page);
      wikiPageRepository.when(() -> WikiPageRepository.remove(page)).thenReturn(true);

      WidgetContext result = new WikiPageListWidget().post(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(page), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("wiki_page"), eq("9"), eq("Setup Guide"), any()), times(1));
      Assertions.assertEquals("Page was deleted", result.getSuccessMessage());
      Assertions.assertEquals("/admin/wiki?wikiId=5", result.getRedirect());
    }
  }

  @Test
  void deletePageIsDeniedForAnUnauthorizedRole() {
    // A role with no wiki-editing permission at all (mirrors WikiWidget's own role check, which
    // this control reuses -- must not be weakened for the admin UI trigger)
    setRoles(widgetContext, DATA_MANAGER);
    addQueryParameter(widgetContext, "action", "deletePage");
    addQueryParameter(widgetContext, "wikiPageId", "9");

    try (MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      WidgetContext result = new WikiPageListWidget().post(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(any()), never());
      loadWikiPage.verify(() -> LoadWikiPageCommand.loadWikiPageById(9L), never());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void deletePageIsDeniedForALoggedOutVisitor() {
    logout(widgetContext);
    addQueryParameter(widgetContext, "action", "deletePage");
    addQueryParameter(widgetContext, "wikiPageId", "9");

    try (MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      WidgetContext result = new WikiPageListWidget().post(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(any()), never());
      Assertions.assertNotNull(result.getErrorMessage());
    }
  }

  @Test
  void aPlainSaveRequestPostIsNotTreatedAsADelete() {
    // post() only delegates to the delete flow when the action param is exactly "deletePage" --
    // confirms it does not accidentally intercept some other POST to this widget
    setRoles(widgetContext, CONTENT_MANAGER);

    try (MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      new WikiPageListWidget().post(widgetContext);

      wikiPageRepository.verify(() -> WikiPageRepository.remove(any()), never());
    }
  }
}
