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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * Admin UI for sitemap.xml (issue #622): a preview link to the live output, and a bulk view/toggle
 * of each web page's "Show in Sitemap.xml?" inclusion, rather than only being settable one page at
 * a time via the page edit form. Named "/admin/seo-sitemap" (not "/admin/sitemap") to avoid the
 * existing naming collision with the Navigation Menu Editor at that path.
 *
 * <p>
 * There is no reusable entry point into SitemapServlet's own rendering (its generation methods are
 * private instance methods with no precedent elsewhere in the codebase for one servlet/widget
 * calling another's internals), so "preview" is a real link to the live endpoint rather than a
 * duplicated render.
 * </p>
 *
 * @author SimIS Inc.
 */
public class SeoSitemapWidget extends GenericWidget {

  static final long serialVersionUID = -7267981320457831044L;

  static String JSP = "/admin/seo-sitemap.jsp";

  public WidgetContext execute(WidgetContext context) {
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    List<WebPage> webPageList = WebPageRepository.findAll();
    context.getRequest().setAttribute("webPageList", webPageList);

    Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
    boolean sitemapEnabled = "true".equals(sitePropertyMap.getOrDefault("site.sitemap.xml", "false"));
    context.getRequest().setAttribute("sitemapEnabled", sitemapEnabled);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Defense in depth: this page is already role-gated at the page-config level
    // (admin-layout.xml), but a state-changing POST re-checks explicitly, matching the convention
    // other admin form widgets follow (e.g. WebPageFormWidget.action()).
    if (!context.hasRole("admin") && !context.hasRole("content-manager")) {
      return execute(context);
    }

    List<WebPage> webPageList = WebPageRepository.findAll();
    int updatedCount = 0;
    for (WebPage webPage : webPageList) {
      // Same "unchecked checkbox sends nothing" handling WebPageFormWidget already uses for this
      // same field, just applied per row instead of once
      boolean requestedShowInSitemap = StringUtils.isNotBlank(context.getParameter("showInSitemap_" + webPage.getId()));
      // Compare against what the form actually showed when it was rendered (a hidden field
      // carrying that snapshot; see seo-sitemap.jsp), not the current DB value. A row nobody
      // touched in the browser must never be written, even if a concurrent edit (another admin,
      // or the single-page edit form) changed it after this form loaded -- diffing against a
      // freshly re-queried DB value here would silently revert that concurrent change instead of
      // leaving it alone.
      boolean renderedShowInSitemap = "true".equals(context.getParameter("renderedShowInSitemap_" + webPage.getId()));
      if (requestedShowInSitemap == renderedShowInSitemap) {
        continue;
      }
      // Re-fetch immediately before writing so the staleness window is just this row, just this
      // moment -- matching SaveWebPageCommand's own re-fetch-before-update pattern -- rather than
      // writing back the copy of the row captured at the top of this whole request, which could
      // clobber some other field a concurrent edit changed on this same row in the meantime.
      WebPage current = WebPageRepository.findById(webPage.getId());
      if (current == null) {
        continue;
      }
      current.setShowInSitemap(requestedShowInSitemap);
      current.setModifiedBy(context.getUserId());
      WebPageRepository.save(current);
      ++updatedCount;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "sitemap.bulk_update", AuditEventCommand.SUCCESS,
        "web_page", null, null, updatedCount + " page(s) updated");

    context.setSuccessMessage(updatedCount == 0
        ? "No changes were made."
        : updatedCount + " page" + (updatedCount == 1 ? "" : "s") + " updated.");
    context.setRedirect("/admin/seo-sitemap");
    return context;
  }
}
