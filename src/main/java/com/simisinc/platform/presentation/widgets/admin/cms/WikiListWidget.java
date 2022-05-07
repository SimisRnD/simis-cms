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

package com.simisinc.platform.presentation.widgets.admin.cms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/10/19 11:27 AM
 */
public class WikiListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/wiki-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the wikis
    List<Wiki> wikiList = WikiRepository.findAll();
    context.getRequest().setAttribute("wikiList", wikiList);

    // Determine the wiki page count
    WikiPageSpecification wikiPageSpecification = new WikiPageSpecification();
    Map<Long, Long> wikiPageCount = new HashMap<>();
    for (Wiki wiki : wikiList) {
      wikiPageSpecification.setWikiId(wiki.getId());
      long count = WikiPageRepository.findCount(wikiPageSpecification);
      wikiPageCount.put(wiki.getId(), count);
    }
    context.getRequest().setAttribute("wikiPageCount", wikiPageCount);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext delete(WidgetContext context) {

    // Permission is required
    if (!(context.hasRole("admin"))) {
      context.setWarningMessage("Must be an admin");
      return context;
    }

    // Determine what's being deleted
    long wikiId = context.getParameterAsLong("id");
    if (wikiId > -1) {
      Wiki wiki = WikiRepository.findById(wikiId);
      if (wiki == null) {
        context.setErrorMessage("Wiki was not found");
      } else {
        if (WikiRepository.remove(wiki)) {
          context.setSuccessMessage("Wiki was deleted");
        } else {
          context.setWarningMessage("Wiki could not be deleted");
        }
      }
    }
    context.setRedirect(context.getUri());
    return context;
  }
}
