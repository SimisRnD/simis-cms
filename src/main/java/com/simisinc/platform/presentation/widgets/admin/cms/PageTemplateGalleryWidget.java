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

import java.util.Comparator;
import java.util.List;

import com.simisinc.platform.application.FeatureFlagCommand;
import com.simisinc.platform.domain.model.cms.WebPageTemplate;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageTemplateRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.controller.XMLWebPageTemplateLoader;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Read-only gallery of every page template available when creating a new web page (issue #1197 --
 * previously the only way to browse them was to start creating a specific page first, via
 * WebPageDesignerWidget's picker). Shares that widget's exact template-loading/sort logic, but
 * doesn't offer a way to pick one -- an admin planning a page's layout ahead of time doesn't yet
 * have a link value to create against.
 *
 * @author SimIS Inc.
 */
public class PageTemplateGalleryWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/page-template-gallery.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Filesystem templates, plus any database-authored ones (mirrors WebPageDesignerWidget)
    List<WebPageTemplate> webPageTemplateList = XMLWebPageTemplateLoader.retrieveTemplateList(context.getRequest().getServletContext());
    List<WebPageTemplate> databaseTemplateList = WebPageTemplateRepository.findAll();
    if (!databaseTemplateList.isEmpty()) {
      webPageTemplateList.addAll(databaseTemplateList);
    }

    // Issue #410: matches WebPageDesignerWidget -- don't browse-list the composition-canvas
    // template ("Webpage Designer", tags a page editor="designer") when its feature flag is off,
    // since picking it while actually creating a page wouldn't be offered either.
    boolean hasDesignerTemplate = webPageTemplateList.stream()
        .anyMatch(template -> template.getPageXml() != null && template.getPageXml().contains("editor=\"designer\""));
    if (hasDesignerTemplate && !FeatureFlagCommand.isEnabled("layout-editor")) {
      webPageTemplateList.removeIf(template -> template.getPageXml() != null
          && template.getPageXml().contains("editor=\"designer\""));
    }

    // Same sort as the picker, so browsing here matches what a new page's template step shows
    webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getName));
    webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getTemplateOrder));
    webPageTemplateList.sort(Comparator.comparing(WebPageTemplate::getCategory));

    context.getRequest().setAttribute("webPageTemplateList", webPageTemplateList);
    context.setJsp(JSP);
    return context;
  }
}
