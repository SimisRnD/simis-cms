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

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Covers {@link WikiEditorWidget}: the explicit-title override for new pages (used by the new
 * "New Page" entry point so the editor shows exactly what was typed, not a lossy
 * dashes-to-spaces reconstruction of the URL slug), and the preview action.
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
