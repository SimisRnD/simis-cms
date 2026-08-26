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

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.widgets.cms.WikiWidget;

/**
 * Lists the pages within a single wiki, and is the entry point for creating a new one. Placed
 * alongside {@link WikiFormWidget} on the "Wiki Details" admin page.
 *
 * <p>
 * Before this widget, {@link WikiPageRepository#findAll(WikiPageSpecification, DataConstraints)}
 * had no caller anywhere in the app: there was no way to see what pages a wiki contained, or to
 * create one without already knowing (or guessing) its URL.
 * </p>
 *
 * @author SimIS
 * @created 7/28/2026
 */
public class WikiPageListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/wiki-page-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    long wikiId = context.getParameterAsLong("wikiId");
    if (wikiId == -1) {
      return context;
    }
    Wiki wiki = WikiRepository.findById(wikiId);
    if (wiki == null) {
      return context;
    }
    context.getRequest().setAttribute("pageListWiki", wiki);
    context.getRequest().setAttribute("wikiViewPrefix", determineViewPrefix(wiki));

    WikiPageSpecification specification = new WikiPageSpecification();
    specification.setWikiId(wikiId);
    DataConstraints constraints = new DataConstraints();
    constraints.setColumnToSortBy("title", "asc");
    List<WikiPage> wikiPageList = WikiPageRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("wikiPageList", wikiPageList);

    context.setJsp(JSP);
    return context;
  }

  /**
   * Where this wiki's pages are actually readable, or null if nowhere.
   *
   * A wiki has no route of its own -- it is rendered by whichever web page hosts the wiki widget,
   * either with a uniqueId baked into that page's layout or, for the built-in documentation page,
   * through a site property. The page list used to assume {@code /<wikiUniqueId>/<pageUniqueId>},
   * which is only correct when a site happens to have built a page at that path. On a site whose
   * only wiki is the documentation one, every View link led to "This is a new page and is not
   * available to users yet."
   *
   * Checked in the order a reader would find them: the documentation page if this is the wiki it is
   * pointed at, then the conventional path if a page really exists there, then nothing -- an absent
   * link being better than one that leads somewhere broken.
   */
  static String determineViewPrefix(Wiki wiki) {
    String documentationWikiUniqueId = LoadSitePropertyCommand.loadByName("documentation.wiki.uniqueId");
    if (StringUtils.isNotBlank(documentationWikiUniqueId)
        && documentationWikiUniqueId.equals(wiki.getUniqueId())) {
      return "/admin/documentation/wiki";
    }
    if (LoadWebPageCommand.loadByLink("/" + wiki.getUniqueId()) != null) {
      return "/" + wiki.getUniqueId();
    }
    return null;
  }

  /**
   * The per-row Delete control (wiki-page-list.jsp) submits via a real POST (postAction()/
   * confirmPostAction() in main.jsp), which WebContainerContext dispatches here rather than to
   * action() below -- delegate before falling through, mirroring BlogPostWidget.post()'s
   * identical pattern for a POST-submitted action.
   */
  public WidgetContext post(WidgetContext context) {
    if ("deletePage".equals(context.getParameter("action"))) {
      return action(context);
    }
    return context;
  }

  /**
   * Deletes a single page from within this wiki's admin page list -- previously the only delete
   * affordance for a wiki page was deleting the entire wiki (cascading every page). Reuses
   * WikiWidget's exact permission check and deletion/audit logic rather than a separately
   * maintained copy that could drift out of sync with it.
   */
  public WidgetContext action(WidgetContext context) {
    if (!WikiWidget.canManageWikiPages(context)) {
      context.setErrorMessage("Permission denied");
      return context;
    }

    long wikiPageId = context.getParameterAsLong("wikiPageId");
    WikiPage wikiPage = LoadWikiPageCommand.loadWikiPageById(wikiPageId);
    if (wikiPage == null) {
      context.setErrorMessage("The record was not found");
      return context;
    }

    // Return to the wiki's admin page list, not the public wiki page WikiWidget's own deletePost
    // redirects to -- this control lives in the admin UI.
    String returnPage = UrlCommand.getValidReturnPage(context.getParameter("returnPage"));
    if (StringUtils.isEmpty(returnPage)) {
      returnPage = "/admin/wiki?wikiId=" + wikiPage.getWikiId();
    }
    context.setRedirect(returnPage);

    String action = context.getParameter("action");
    if ("deletePage".equals(action)) {
      return WikiWidget.deletePost(context, wikiPage);
    }
    return context;
  }
}
