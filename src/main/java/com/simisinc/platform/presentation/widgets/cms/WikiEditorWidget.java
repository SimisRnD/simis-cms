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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.application.cms.RenderWikiMarkdownCommand;
import com.simisinc.platform.application.cms.SaveWikiPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;

import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import javax.json.Json;
import javax.json.JsonObject;
import java.io.StringWriter;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/10/19 4:00 PM
 */
public class WikiEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/wiki-editor.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Determine the wiki and page being edited
    String wikiUniqueId = context.getParameter("wikiUniqueId");
    String pageUniqueId = context.getParameter("pageUniqueId");
    if (StringUtils.isEmpty(wikiUniqueId) || StringUtils.isEmpty(pageUniqueId)) {
      LOG.error("Incorrect parameters");
      return context;
    }

    // Load the wiki
    Wiki wiki = LoadWikiCommand.loadWikiByUniqueId(wikiUniqueId);
    if (wiki == null) {
      LOG.error("Wiki was not found");
      return context;
    }
    context.getRequest().setAttribute("wiki", wiki);

    // Determine if there's a page yet
    WikiPage wikiPage = LoadWikiPageCommand.loadWikiPageByUniqueId(wiki.getId(), pageUniqueId);
    if (wikiPage != null) {

      // Use the existing page and content
      context.getRequest().setAttribute("wikiPage", wikiPage);
      context.getRequest().setAttribute("content", wikiPage.getBody());

    } else {

      // Setup a new page. An explicit title (from the "New Page" entry point, which already knows
      // the exact title the user typed) takes precedence over deriving one from the URL slug --
      // that derivation is lossy (it can't recover capitalization or punctuation) and exists only
      // as a fallback for reaching this editor without one, e.g. clicking a [[WikiLink]] to a page
      // that doesn't exist yet.
      WikiPage tempWikiPage = new WikiPage();
      tempWikiPage.setWikiId(wiki.getId());
      String requestedTitle = context.getParameter("title");
      tempWikiPage.setTitle(StringUtils.isNotBlank(requestedTitle)
          ? requestedTitle
          : StringUtils.replaceChars(pageUniqueId, "-", " "));
      tempWikiPage.setUniqueId(pageUniqueId);
      context.getRequest().setAttribute("wikiPage", tempWikiPage);
      context.getRequest().setAttribute("content", "");

    }

    // Determine the return page
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    context.getRequest().setAttribute("returnPage", returnPage);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Determine the wiki and page being edited
    String wikiUniqueId = context.getParameter("wikiUniqueId");
    String pageUniqueId = context.getParameter("pageUniqueId");
    if (StringUtils.isEmpty(wikiUniqueId) || StringUtils.isEmpty(pageUniqueId)) {
      LOG.error("Incorrect parameters");
      return context;
    }

    // Load the wiki
    Wiki wiki = LoadWikiCommand.loadWikiByUniqueId(wikiUniqueId);
    if (wiki == null) {
      LOG.error("Wiki was not found");
      return context;
    }

    // Determine if there's a page yet
    WikiPage wikiPage = LoadWikiPageCommand.loadWikiPageByUniqueId(wiki.getId(), pageUniqueId);
    if (wikiPage == null) {
      wikiPage = new WikiPage();
      wikiPage.setWikiId(wiki.getId());
      wikiPage.setTitle(pageUniqueId);
      wikiPage.setUniqueId(pageUniqueId);
    }

    // Check for parameters
    String title = context.getRequest().getParameter("title");
    if (StringUtils.isNotBlank(title)) {
      wikiPage.setTitle(title);
    }
    String content = context.getParameter("content");
    if (content == null) {
      LOG.error("DEVELOPER: Content parameter was not found");
      context.setErrorMessage("A system error occurred");
      return context;
    }
    wikiPage.setBody(content);
    wikiPage.setCreatedBy(context.getUserId());
    wikiPage.setModifiedBy(context.getUserId());

    // Save it
    try {
      SaveWikiPageCommand.saveWikiPage(wikiPage);
    } catch (DataException e) {
      LOG.error("DEVELOPER: Content parameter was not found");
      context.setErrorMessage("A system error occurred");
      return context;
    }

    // Determine the page to return to
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = "/";
    }
    context.setRedirect(returnPage);
    return context;
  }

  /**
   * Renders the editor's current (unsaved) buffer through the same path the live page uses, so
   * preview can never show something different from what publishing would actually produce.
   * Reached through the widget action framework (WebContainerCommand.processWidgets checks the
   * CSRF token before this runs), same as every other widget action -- not a hand-rolled endpoint.
   */
  public WidgetContext action(WidgetContext context) {

    if (!(context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager"))) {
      context.setJson("{\"error\":\"Permission denied\"}");
      return context;
    }

    String wikiUniqueId = context.getParameter("wikiUniqueId");
    String content = context.getParameter("content");
    if (StringUtils.isEmpty(wikiUniqueId) || content == null) {
      context.setJson("{\"error\":\"Missing parameters\"}");
      return context;
    }

    String wikiLinkPrefix = "/" + wikiUniqueId;
    String contentHtml = RenderWikiMarkdownCommand.toHtml(content, wikiLinkPrefix);

    JsonObject json = Json.createObjectBuilder()
        .add("html", contentHtml)
        .build();
    StringWriter writer = new StringWriter();
    Json.createWriter(writer).writeObject(json);
    context.setJson(writer.toString());
    return context;
  }
}
