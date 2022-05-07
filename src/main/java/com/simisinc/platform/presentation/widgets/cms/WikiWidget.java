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

import java.util.Arrays;

import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.domain.model.cms.WikiParserExtension;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.ext.wikilink.WikiLinkExtension;
import com.vladsch.flexmark.ext.youtube.embedded.YouTubeLinkExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/10/19 3:12 PM
 */
public class WikiWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/wiki-page.jsp";
  static String WIKI_PAGE_NOT_FOUND_JSP = "/cms/wiki-page-not-found.jsp";
  static String WIKI_NOT_SETUP_JSP = "/cms/wiki-not-setup.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    if (context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager")) {
      context.getRequest().setAttribute("showEditor", "true");
      context.getRequest().setAttribute("returnPage", context.getRequest().getRequestURI());
    }

    // Determine the wiki
    String wikiUniqueId = context.getPreferences().get("wikiUniqueId");
    if (wikiUniqueId == null) {
      LOG.warn("Wiki preference not found");
      return null;
    }
    Wiki wiki = LoadWikiCommand.loadWikiByUniqueId(wikiUniqueId);
    if (wiki == null) {
      LOG.warn("Wiki unique id not found: " + wikiUniqueId);
      context.setJsp(WIKI_NOT_SETUP_JSP);
      return context;
    }
    if (!wiki.getEnabled() &&
        !(context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager"))) {
      return null;
    }
    context.getRequest().setAttribute("wiki", wiki);

    // Determine the base URL of this wiki
    String wikiLinkPrefix = context.getUri();
    if (context.getUri().lastIndexOf("/") != 0) {
      wikiLinkPrefix = wikiLinkPrefix.substring(0, context.getUri().lastIndexOf("/"));
    }
    LOG.debug("wikiLinkPrefix: " + wikiLinkPrefix);
    context.getRequest().setAttribute("wikiLinkPrefix", wikiLinkPrefix);

    // Determine the wiki page
    String wikiPageUniqueId = "home";
    if (context.getUri().lastIndexOf("/") != 0) {
      wikiPageUniqueId = context.getUri().substring(context.getUri().lastIndexOf("/") + 1);
    }
    WikiPage wikiPage = LoadWikiPageCommand.loadWikiPageByUniqueId(wiki.getId(), wikiPageUniqueId);
    if (wikiPage == null) {

      // Setup a new page
      WikiPage tempWikiPage = new WikiPage();
      tempWikiPage.setWikiId(wiki.getId());
      tempWikiPage.setTitle(StringUtils.replaceChars(wikiPageUniqueId, "-", " "));
      tempWikiPage.setUniqueId(wikiPageUniqueId);
      context.getRequest().setAttribute("wikiPage", tempWikiPage);

      LOG.debug("Wiki page not found: " + wiki.getId() + " " + wikiPageUniqueId);
      context.setJsp(WIKI_PAGE_NOT_FOUND_JSP);
      return context;
    }
    context.getRequest().setAttribute("wikiPage", wikiPage);

    // Set the markup conversion settings
    MutableDataSet options = new MutableDataSet();
    options.set(Parser.EXTENSIONS, Arrays.asList(
//        AnchorLinkExtension.create(),
        StrikethroughExtension.create(),
        TablesExtension.create(),
        TaskListExtension.create(),
        TypographicExtension.create(),
        WikiLinkExtension.create(),
        YouTubeLinkExtension.create(),
        WikiParserExtension.create()
        )
    );
    options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
    options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
    options.set(WikiLinkExtension.LINK_PREFIX, wikiLinkPrefix + "/");
    Parser parser = Parser.builder(options).build();
    HtmlRenderer renderer = HtmlRenderer.builder(options).build();

    // Convert the markup to html
    Node document = parser.parse(wikiPage.getBody());
    String contentHtml = renderer.render(document);
    context.getRequest().setAttribute("contentHtml", contentHtml);

    // Set the HTML page title
    if (wiki.getStartingPage() != wikiPage.getId()) {
      context.setPageTitle(wikiPage.getTitle());
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext action(WidgetContext context) {
    // Permission is required
    if (!(context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager"))) {
      return context;
    }

    // Find the wiki record
    long wikiPageId = context.getParameterAsLong("wikiPageId");
    WikiPage wikiPage = LoadWikiPageCommand.loadWikiPageById(wikiPageId);
    if (wikiPage == null) {
      context.setErrorMessage("The record was not found");
      return context;
    }
    Wiki wiki = LoadWikiCommand.loadWikiById(wikiPage.getWikiId());

    // Execute the action
    context.setRedirect("/" + wiki.getUniqueId());
    String action = context.getParameter("action");
    if ("deletePost".equals(action)) {
      return deletePost(context, wikiPage);
    }
    return context;
  }

  private WidgetContext deletePost(WidgetContext context, WikiPage wikiPage) {
    // Attempt to delete the wiki page
    try {
      WikiPageRepository.remove(wikiPage);
      context.setSuccessMessage("Page was deleted");
    } catch (Exception e) {
      context.setErrorMessage("The page could not be deleted: " + e.getMessage());
    }
    return context;
  }
}
