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

    // Determine the wiki being edited. pageUniqueId is optional here -- the "New Page" entry
    // point (wiki-page-list.jsp) intentionally omits it entirely, rather than pre-computing a
    // client-side slug guess, so a brand new page can never be silently routed into loading (and
    // later overwriting) an unrelated existing page whose title/slug happens to collide. See
    // post() below for the save-time half of this fix.
    String wikiUniqueId = context.getParameter("wikiUniqueId");
    if (StringUtils.isEmpty(wikiUniqueId)) {
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

    // Determine if there's a real, existing page to edit. This lookup is only attempted when a
    // pageUniqueId was actually supplied -- the "Edit" entry point (wiki-page-list.jsp's per-row
    // link) always supplies one, taken directly from a real WikiPage already loaded server-side,
    // not typed/guessed by the client.
    String pageUniqueId = context.getParameter("pageUniqueId");
    WikiPage wikiPage = null;
    if (StringUtils.isNotEmpty(pageUniqueId)) {
      wikiPage = LoadWikiPageCommand.loadWikiPageByUniqueId(wiki.getId(), pageUniqueId);
    }

    if (wikiPage != null) {

      // Use the existing page and content
      context.getRequest().setAttribute("wikiPage", wikiPage);
      context.getRequest().setAttribute("content", wikiPage.getBody());

    } else {

      // Setup a new page -- either the "New Page" entry point (no pageUniqueId at all, an
      // explicit typed title) or a [[WikiLink]] to a page that doesn't exist yet (pageUniqueId
      // present, derived from the URL slug, no title). Either way nothing is loaded from an
      // existing record: the resulting WikiPage keeps its default id (-1) and a null uniqueId,
      // so the title alone can never be mistaken for -- or silently overwrite -- an unrelated
      // existing page. The final uniqueId is generated server-side from the title at Save time
      // (see post() and GenerateWikiPageUniqueIdCommand, which already dedupes a collision).
      WikiPage tempWikiPage = new WikiPage();
      tempWikiPage.setWikiId(wiki.getId());
      String requestedTitle = context.getParameter("title");
      if (StringUtils.isNotBlank(requestedTitle)) {
        tempWikiPage.setTitle(requestedTitle);
      } else if (StringUtils.isNotBlank(pageUniqueId)) {
        tempWikiPage.setTitle(StringUtils.replaceChars(pageUniqueId, "-", " "));
      }
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

    // The preview request is submitted via a real POST body rather than a GET query string (long
    // pages could silently exceed a typical servlet-container/proxy request-line-length limit as
    // a query string), which WebContainerContext dispatches here rather than to action() below --
    // delegate before the save-flow logic, mirroring BlogPostWidget.post()'s identical pattern
    // for a POST-submitted action.
    if ("preview".equals(context.getParameter("action"))) {
      return action(context);
    }

    // Determine the wiki being edited
    String wikiUniqueId = context.getParameter("wikiUniqueId");
    if (StringUtils.isEmpty(wikiUniqueId)) {
      LOG.error("Incorrect parameters");
      return context;
    }

    // Load the wiki
    Wiki wiki = LoadWikiCommand.loadWikiByUniqueId(wikiUniqueId);
    if (wiki == null) {
      LOG.error("Wiki was not found");
      return context;
    }

    // Determine which page is being saved using the numeric id the editor was actually opened
    // with (a hidden field set server-side by execute(), from wikiPage.id -- see the JSP), never
    // the client-typed title or a slug derived from it. A brand new page's title can collide with
    // an existing page's title/slug, and must never be routed into silently editing (and
    // overwriting) that unrelated existing page -- see execute() above and
    // GenerateWikiPageUniqueIdCommand's save-time dedupe for how a genuinely new page's uniqueId
    // is produced instead.
    long wikiPageId = context.getParameterAsLong("wikiPageId", -1);
    WikiPage wikiPage;
    if (wikiPageId > -1) {
      wikiPage = LoadWikiPageCommand.loadWikiPageById(wikiPageId);
      if (wikiPage == null || !wiki.getId().equals(wikiPage.getWikiId())) {
        LOG.error("The wiki page to update was not found");
        context.setErrorMessage("The page could not be found");
        return context;
      }
    } else {
      wikiPage = new WikiPage();
      wikiPage.setWikiId(wiki.getId());
    }

    // Check for parameters
    String title = context.getParameter("title");
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

    // Save it. A null return (as opposed to a thrown DataException) means the insert/update itself
    // was rejected at the database level -- the one real-world case is two concurrent "New Page"
    // saves in the same wiki racing to the same title-derived uniqueId, where the loser's INSERT is
    // rejected by the wiki_pages unique index after both requests read the dedupe check as clear.
    // Without this check the loser was silently redirected as if their content had saved.
    WikiPage savedWikiPage;
    try {
      savedWikiPage = SaveWikiPageCommand.saveWikiPage(wikiPage);
    } catch (DataException e) {
      LOG.error("DEVELOPER: Content parameter was not found");
      context.setErrorMessage("A system error occurred");
      return context;
    }
    if (savedWikiPage == null) {
      LOG.error("Wiki page could not be saved");
      context.setErrorMessage("The page could not be saved. Please try again.");
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
