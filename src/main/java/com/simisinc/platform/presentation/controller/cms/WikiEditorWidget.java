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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.LoadWikiCommand;
import com.simisinc.platform.application.cms.LoadWikiPageCommand;
import com.simisinc.platform.application.cms.SaveWikiPageCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;

import org.apache.commons.lang3.StringUtils;

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

      // Setup a new page
      WikiPage tempWikiPage = new WikiPage();
      tempWikiPage.setWikiId(wiki.getId());
      tempWikiPage.setTitle(StringUtils.replaceChars(pageUniqueId, "-", " "));
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
}
