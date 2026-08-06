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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.application.cms.SaveWikiPageCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers {@link WikiEditorWidget}: the explicit-title override for new pages (used by the new
 * "New Page" entry point so the editor shows exactly what was typed, not a lossy
 * dashes-to-spaces reconstruction of the URL slug), the preview action, and the save-time fix
 * that stops a "New Page" title colliding with an existing page's title/slug from silently
 * routing the request into editing (and overwriting) that unrelated existing page.
 *
 * @author SimIS
 * @created 7/28/2026
 */
class WikiEditorWidgetTest extends WidgetBase {

  private Wiki wiki() {
    Wiki wiki = new Wiki();
    wiki.setId(1L);
    wiki.setUniqueId("docs");
    return wiki;
  }

  @Test
  void aNewPageUsesTheExplicitTitleParameterWhenPresent() {
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "pageUniqueId", "api-reference");
    addQueryParameter(widgetContext, "title", "API Reference (v2)");

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageByUniqueId(1L, "api-reference")).thenReturn(null);

      new WikiEditorWidget().execute(widgetContext);
    }

    WikiPage wikiPage = (WikiPage) request.getAttribute("wikiPage");
    Assertions.assertEquals("API Reference (v2)", wikiPage.getTitle(),
        "the exact typed title must be used, not a slug-derived reconstruction");
  }

  @Test
  void aNewPageFallsBackToASlugDerivedTitleWithoutAnExplicitOne() {
    // The path reached by clicking a [[WikiLink]] to a page that doesn't exist yet -- there's no
    // "New Page" title to pass through, only the URL slug.
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "pageUniqueId", "api-reference");

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageByUniqueId(1L, "api-reference")).thenReturn(null);

      new WikiEditorWidget().execute(widgetContext);
    }

    WikiPage wikiPage = (WikiPage) request.getAttribute("wikiPage");
    Assertions.assertEquals("api reference", wikiPage.getTitle());
  }

  @Test
  void theNewPageEntryPointOpensAGenuinelyBlankStateWithoutLookingUpAnyExistingPage() {
    // wiki-page-list.jsp's "New Page" control no longer pre-computes a client-side slug or
    // supplies a pageUniqueId at all -- only the typed title. Confirms execute() never even
    // attempts a lookup in this flow, so a title that happens to match an existing page's
    // title/slug can't be loaded here regardless of what LoadWikiPageCommand would have returned.
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "title", "FAQ");

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());

      new WikiEditorWidget().execute(widgetContext);

      loadWikiPage.verify(() -> LoadWikiPageCommand.loadWikiPageByUniqueId(any(), any()), never());
    }

    WikiPage wikiPage = (WikiPage) request.getAttribute("wikiPage");
    Assertions.assertEquals("FAQ", wikiPage.getTitle());
    Assertions.assertEquals(-1L, wikiPage.getId(), "a genuinely new page must keep the default -1 id");
    Assertions.assertEquals("", request.getAttribute("content"));
  }

  @Test
  void savingWithNoWikiPageIdAlwaysCreatesANewPageRegardlessOfWhetherTheTitleCollides() {
    // Proves the routing half of the collision fix: since the "New Page" flow no longer sends a
    // pageUniqueId at all, post() must decide this is a new page from the (absent) wikiPageId
    // field alone -- never from whether the typed title happens to match an existing page's
    // title/slug ("FAQ" is used here specifically because it is a very plausible real collision).
    // This test intentionally mocks SaveWikiPageCommand, so it does NOT exercise the server-side
    // slug-dedup itself (that a colliding title actually gets suffixed to "faq-2", not silently
    // reused) -- that half is covered directly against GenerateWikiPageUniqueIdCommand in
    // GenerateWikiPageUniqueIdCommandTest#aGenuinelyNewPageDedupesItsUniqueIdAgainstAnExistingCollisionInTheSameWiki.
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "title", "FAQ");
    addQueryParameter(widgetContext, "content", "Brand new content");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<SaveWikiPageCommand> saveWikiPage = mockStatic(SaveWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());

      new WikiEditorWidget().post(widgetContext);

      loadWikiPage.verify(() -> LoadWikiPageCommand.loadWikiPageById(anyLong()), never());

      ArgumentCaptor<WikiPage> savedPage = ArgumentCaptor.forClass(WikiPage.class);
      saveWikiPage.verify(() -> SaveWikiPageCommand.saveWikiPage(savedPage.capture()), times(1));
      Assertions.assertEquals(-1L, savedPage.getValue().getId(), "must be saved as a new record, not an update");
      Assertions.assertEquals("FAQ", savedPage.getValue().getTitle());
      Assertions.assertEquals("Brand new content", savedPage.getValue().getBody());
    }
  }

  @Test
  void clickingEditOnARealExistingPageLoadsAndUpdatesThatExactPage() {
    WikiPage existingPage = new WikiPage();
    existingPage.setId(42L);
    existingPage.setWikiId(1L);
    existingPage.setUniqueId("faq");
    existingPage.setTitle("FAQ");
    existingPage.setBody("Old content");

    // The "Edit" link (wiki-page-list.jsp) supplies pageUniqueId from a real, server-loaded page
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "pageUniqueId", "faq");

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageByUniqueId(1L, "faq")).thenReturn(existingPage);

      new WikiEditorWidget().execute(widgetContext);
    }

    WikiPage wikiPage = (WikiPage) request.getAttribute("wikiPage");
    Assertions.assertEquals(42L, wikiPage.getId());
    Assertions.assertEquals("Old content", request.getAttribute("content"));

    // Now submit the edit -- the hidden wikiPageId field (rendered from wikiPage.id above) is
    // what post() uses to identify the record, not the title
    addQueryParameter(widgetContext, "wikiPageId", "42");
    addQueryParameter(widgetContext, "title", "Frequently Asked Questions");
    addQueryParameter(widgetContext, "content", "Updated content");
    setRoles(widgetContext, ADMIN);

    try (MockedStatic<LoadWikiCommand> loadWiki = mockStatic(LoadWikiCommand.class);
        MockedStatic<LoadWikiPageCommand> loadWikiPage = mockStatic(LoadWikiPageCommand.class);
        MockedStatic<SaveWikiPageCommand> saveWikiPage = mockStatic(SaveWikiPageCommand.class)) {
      loadWiki.when(() -> LoadWikiCommand.loadWikiByUniqueId("docs")).thenReturn(wiki());
      loadWikiPage.when(() -> LoadWikiPageCommand.loadWikiPageById(42L)).thenReturn(existingPage);

      new WikiEditorWidget().post(widgetContext);

      ArgumentCaptor<WikiPage> savedPage = ArgumentCaptor.forClass(WikiPage.class);
      saveWikiPage.verify(() -> SaveWikiPageCommand.saveWikiPage(savedPage.capture()), times(1));
      Assertions.assertEquals(42L, savedPage.getValue().getId(), "must update the exact existing record, not create a new one");
      Assertions.assertEquals("Frequently Asked Questions", savedPage.getValue().getTitle());
      Assertions.assertEquals("Updated content", savedPage.getValue().getBody());
    }
  }

  @Test
  void previewSubmittedAsARealPostStillRendersThroughTheSameActionHandler() {
    // The Preview button now submits as a real POST body (issue: long pages could exceed a
    // request-line-length limit as a GET query string). WebContainerContext dispatches a real
    // POST to post(), not action() -- this fails if post() stops delegating to action() for it.
    addQueryParameter(widgetContext, "action", "preview");
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "content", "# Draft Heading");
    setRoles(widgetContext, ADMIN);

    WidgetContext result = new WikiEditorWidget().post(widgetContext);

    Assertions.assertNotNull(result.getJson());
    Assertions.assertTrue(result.getJson().contains("Draft Heading"));
    Assertions.assertFalse(result.getJson().contains("\"error\""));
  }

  @Test
  void previewRendersTheSubmittedContentWithoutSaving() {
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "content", "# Draft Heading");
    setRoles(widgetContext, ADMIN);

    WidgetContext result = new WikiEditorWidget().action(widgetContext);

    Assertions.assertNotNull(result.getJson());
    Assertions.assertTrue(result.getJson().contains("Draft Heading"));
    Assertions.assertFalse(result.getJson().contains("\"error\""));
  }

  @Test
  void previewRequiresAnEditingRole() {
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    addQueryParameter(widgetContext, "content", "# Draft Heading");
    // No role set -- an unauthenticated or unprivileged caller

    WidgetContext result = new WikiEditorWidget().action(widgetContext);

    Assertions.assertTrue(result.getJson().contains("\"error\""));
  }

  @Test
  void previewRequiresContentParameter() {
    addQueryParameter(widgetContext, "wikiUniqueId", "docs");
    setRoles(widgetContext, ADMIN);

    WidgetContext result = new WikiEditorWidget().action(widgetContext);

    Assertions.assertTrue(result.getJson().contains("\"error\""));
  }
}
