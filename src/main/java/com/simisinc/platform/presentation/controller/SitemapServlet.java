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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

@WebServlet(name = "SitemapServlet", urlPatterns = {"/sitemap.xml"})
public class SitemapServlet extends HttpServlet {

  private static final Log LOG = LogFactory.getLog(SitemapServlet.class);

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("application/xml;charset=UTF-8");
    response.setHeader("Cache-Control", "public, max-age=3600");

    try {
      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
      String siteUrl = sitePropertyMap.get("site.url");

      if (StringUtils.isBlank(siteUrl)) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        response.getWriter().print("<!-- Site URL not configured -->\n");
        return;
      }

      response.setStatus(HttpServletResponse.SC_OK);
      PrintWriter writer = response.getWriter();

      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      writer.println("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

      // Add homepage
      writer.println("  <url>");
      writer.println("    <loc>" + escapeXml(siteUrl) + "/</loc>");
      writer.println("    <changefreq>daily</changefreq>");
      writer.println("    <priority>1.0</priority>");
      writer.println("  </url>");

      // Add published web pages
      addWebPagesToSitemap(writer, siteUrl);

      // Add published items (products/catalog entries)
      addItemsToSitemap(writer, siteUrl);

      writer.println("</urlset>");
    } catch (Exception e) {
      LOG.error("Error generating sitemap.xml: " + e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      response.getWriter().print("<!-- Error generating sitemap -->\n");
    }
  }

  private void addWebPagesToSitemap(PrintWriter writer, String siteUrl) {
    try {
      WebPageSpecification spec = new WebPageSpecification();
      spec.setEnabled(1);
      spec.setDraft(0);
      List<WebPage> pages = WebPageRepository.findAll(spec, null);

      if (pages != null) {
        for (WebPage page : pages) {
          if (page != null && StringUtils.isNotBlank(page.getLink())) {
            writer.println("  <url>");
            writer.println("    <loc>" + escapeXml(siteUrl + page.getLink()) + "</loc>");

            if (page.getModified() != null) {
              writer.println("    <lastmod>" + formatDate(page.getModified()) + "</lastmod>");
            }
            writer.println("    <changefreq>weekly</changefreq>");
            writer.println("    <priority>0.8</priority>");
            writer.println("  </url>");
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding web pages to sitemap: " + e.getMessage());
    }
  }

  private void addItemsToSitemap(PrintWriter writer, String siteUrl) {
    try {
      ItemSpecification spec = new ItemSpecification();
      spec.setApprovedOnly(true);
      List<Item> items = ItemRepository.findAll(spec, null);

      if (items != null) {
        for (Item item : items) {
          if (item != null && item.getId() != null && StringUtils.isNotBlank(item.getUniqueId())) {
            String itemLink = "/item/" + item.getUniqueId();
            writer.println("  <url>");
            writer.println("    <loc>" + escapeXml(siteUrl + itemLink) + "</loc>");

            if (item.getModified() != null) {
              writer.println("    <lastmod>" + formatDate(item.getModified()) + "</lastmod>");
            }
            writer.println("    <changefreq>monthly</changefreq>");
            writer.println("    <priority>0.6</priority>");
            writer.println("  </url>");
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding items to sitemap: " + e.getMessage());
    }
  }

  private String formatDate(Date date) {
    if (date == null) return "";
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    return sdf.format(date);
  }

  private String escapeXml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
