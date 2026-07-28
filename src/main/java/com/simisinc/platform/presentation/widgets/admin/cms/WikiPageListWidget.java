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

import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

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

    WikiPageSpecification specification = new WikiPageSpecification();
    specification.setWikiId(wikiId);
    DataConstraints constraints = new DataConstraints();
    constraints.setColumnToSortBy("title", "asc");
    List<WikiPage> wikiPageList = WikiPageRepository.findAll(specification, constraints);
    context.getRequest().setAttribute("wikiPageList", wikiPageList);

    context.setJsp(JSP);
    return context;
  }
}
