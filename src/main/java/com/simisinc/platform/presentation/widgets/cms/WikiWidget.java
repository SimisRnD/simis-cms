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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.application.cms.RenderWikiMarkdownCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
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

    // Determine the wiki -- either a fixed uniqueId baked into this page's own layout config
    // (the normal case: a page built from the "Wiki" web-template), or, when this widget instance
    // declares wikiUniqueIdProperty instead, a site property the admin can repoint without editing
    // the layout (used by the built-in /admin/documentation page so an admin can choose which of
    // their own wikis appears there).
    String wikiUniqueId = context.getPreferences().get("wikiUniqueId");
    String wikiUniqueIdProperty = context.getPreferences().get("wikiUniqueIdProperty");
    boolean wikiUniqueIdFromProperty = StringUtils.isNotBlank(wikiUniqueIdProperty);
    if (wikiUniqueIdFromProperty) {
      wikiUniqueId = LoadSitePropertyCommand.loadByName(wikiUniqueIdProperty);
    }
    if (StringUtils.isBlank(wikiUniqueId)) {
      if (wikiUniqueIdFromProperty) {
        // Nothing has been chosen yet -- show the same status page as an unresolved wiki id below,
        // rather than silently rendering nothing. The reason is passed through so the page can say
        // which of the two situations it is; they need different things done about them.
        context.getRequest().setAttribute("wikiSetupIssue", "none-selected");
        context.getRequest().setAttribute("wikiSetupProperty", wikiUniqueIdProperty);
        context.setJsp(WIKI_NOT_SETUP_JSP);
        return context;
      }
      LOG.warn("Wiki preference not found");
      return null;
    }
    Wiki wiki = LoadWikiCommand.loadWikiByUniqueId(wikiUniqueId);
    if (wiki == null) {
      LOG.warn("Wiki unique id not found: " + wikiUniqueId);
      context.getRequest().setAttribute("wikiSetupIssue", "not-found");
      context.getRequest().setAttribute("wikiSetupUniqueId", wikiUniqueId);
      if (wikiUniqueIdFromProperty) {
        context.getRequest().setAttribute("wikiSetupProperty", wikiUniqueIdProperty);
      }
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

    // Convert the markup to sanitized html -- see RenderWikiMarkdownCommand for why sanitizing at
    // render time is the safety boundary here, not storage time.
    String contentHtml = RenderWikiMarkdownCommand.toHtml(wikiPage.getBody(), wikiLinkPrefix);
    context.getRequest().setAttribute("contentHtml", contentHtml);

    if (wikiPage.getBody().contains("```mermaid")) {
      context.getRequest().setAttribute("mermaid", "true");
    }

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
    if (!canManageWikiPages(context)) {
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

  /**
   * The permission required to delete (or otherwise manage) a wiki page. Shared with
   * {@code WikiPageListWidget}'s admin delete-this-page control (wiki-page-list.jsp) so that UI
   * trigger enforces the exact same check as this widget's own deletePost action, rather than a
   * separately-maintained (and possibly diverging) copy.
   */
  public static boolean canManageWikiPages(WidgetContext context) {
    return context.hasRole("admin") || context.hasRole("content-manager") || context.hasRole("community-manager");
  }

  /**
   * Deletes a wiki page and records the audit event. Public and static so
   * {@code WikiPageListWidget}'s admin delete-this-page control can reuse this exact logic
   * (repository call, audit event shape, success/error messaging) instead of duplicating it.
   * Callers are responsible for their own permission check (see {@link #canManageWikiPages}) and
   * for setting any redirect appropriate to where the control was triggered from.
   */
  public static WidgetContext deletePost(WidgetContext context, WikiPage wikiPage) {
    String targetId = String.valueOf(wikiPage.getId());
    String targetLabel = wikiPage.getTitle();
    // Attempt to delete the wiki page
    try {
      // remove() returns false on a swallowed DB failure rather than throwing, so branch on its result
      boolean removed = WikiPageRepository.remove(wikiPage);
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE, "wiki_page", targetId, targetLabel, null);
      context.setSuccessMessage("Page was deleted");
    } catch (Exception e) {
      AuditEventCommand.record(context, AuditEventCommand.CONTENT, "content.delete", AuditEventCommand.FAILURE,
          "wiki_page", targetId, targetLabel, e.getMessage());
      context.setErrorMessage("The page could not be deleted: " + e.getMessage());
    }
    return context;
  }
}
