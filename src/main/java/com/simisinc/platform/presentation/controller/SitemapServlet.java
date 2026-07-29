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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@WebServlet(name = "SitemapServlet", urlPatterns = {"/sitemap.xml"})
public class SitemapServlet extends HttpServlet {

  private static final Log LOG = LogFactory.getLog(SitemapServlet.class);

  // The sitemap protocol's own documented default -- omitted from output (see formatPriority)
  // rather than emitted, both because stating it explicitly is redundant and because it lets a
  // genuinely-unset priority (which now reads back as this same value, see WebPage's field default)
  // render identically to an explicit admin choice of 0.5
  private static final BigDecimal DEFAULT_PRIORITY = new BigDecimal("0.5");

  // The sitemap protocol's own hard cap: 50,000 URLs (or 50MB uncompressed, whichever comes
  // first) per sitemap file. Used both as the "is a single file still enough" threshold and as
  // the per-child chunk size once we're over it -- at plausible per-<url> sizes, 50,000 entries
  // stays far under the 50MB half of the cap, so URL count is the only limit worth tracking here.
  static final int MAX_URLS_PER_SITEMAP = 50_000;

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

      // Same "not yet public" gate PageServlet uses for anonymous visitors -- a site an admin
      // hasn't taken online shouldn't be discoverable via its sitemap either
      if (!"true".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      if (!"true".equals(sitePropertyMap.getOrDefault("site.sitemap.xml", "false"))) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      List<SitemapUrlEntry> allEntries = buildAllEntries(siteUrl);
      int totalUrls = allEntries.size();
      int totalPages = (int) Math.ceil(totalUrls / (double) MAX_URLS_PER_SITEMAP);

      PrintWriter writer = response.getWriter();

      String pageParam = request.getParameter("page");
      if (pageParam != null) {
        // A ?page=N sitemap chunk only exists as its own resource once pagination actually
        // kicked in -- below the threshold there's nothing for it to address but the single
        // unparameterized sitemap, so treat it as a 404 rather than silently re-serving that.
        Integer page = parsePositiveInt(pageParam);
        if (totalUrls <= MAX_URLS_PER_SITEMAP || page == null || page < 1 || page > totalPages) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        writeUrlset(writer, pageOf(allEntries, page));
        return;
      }

      response.setStatus(HttpServletResponse.SC_OK);
      if (totalUrls > MAX_URLS_PER_SITEMAP) {
        writeSitemapIndex(writer, siteUrl, totalPages);
      } else {
        writeUrlset(writer, allEntries);
      }
    } catch (Exception e) {
      LOG.error("Error generating sitemap.xml: " + e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().print("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      response.getWriter().print("<!-- Error generating sitemap -->\n");
    }
  }

  private List<SitemapUrlEntry> buildAllEntries(String siteUrl) {
    List<SitemapUrlEntry> entries = new ArrayList<>();
    entries.add(new SitemapUrlEntry(escapeXml(siteUrl) + "/", null, "daily", "1.0"));
    entries.addAll(webPageEntries(siteUrl));
    entries.addAll(itemEntries(siteUrl));
    return entries;
  }

  private static List<SitemapUrlEntry> pageOf(List<SitemapUrlEntry> allEntries, int page) {
    int fromIndex = (page - 1) * MAX_URLS_PER_SITEMAP;
    int toIndex = Math.min(fromIndex + MAX_URLS_PER_SITEMAP, allEntries.size());
    return allEntries.subList(fromIndex, toIndex);
  }

  private static Integer parsePositiveInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void writeUrlset(PrintWriter writer, List<SitemapUrlEntry> entries) {
    writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.println("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
    for (SitemapUrlEntry entry : entries) {
      writer.println("  <url>");
      writer.println("    <loc>" + entry.loc + "</loc>");
      if (entry.lastmod != null) {
        writer.println("    <lastmod>" + entry.lastmod + "</lastmod>");
      }
      if (entry.changefreq != null) {
        writer.println("    <changefreq>" + entry.changefreq + "</changefreq>");
      }
      if (entry.priority != null) {
        writer.println("    <priority>" + entry.priority + "</priority>");
      }
      writer.println("  </url>");
    }
    writer.println("</urlset>");
  }

  /**
   * Emitted in place of a single &lt;urlset&gt; once the total URL count exceeds
   * {@link #MAX_URLS_PER_SITEMAP} -- points at MAX_URLS_PER_SITEMAP-sized child sitemaps served
   * by this same servlet via {@code ?page=1..N}, per the sitemap protocol's own chunking scheme.
   */
  private void writeSitemapIndex(PrintWriter writer, String siteUrl, int totalPages) {
    writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
    writer.println("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
    for (int page = 1; page <= totalPages; page++) {
      writer.println("  <sitemap>");
      writer.println("    <loc>" + escapeXml(siteUrl + "/sitemap.xml?page=" + page) + "</loc>");
      writer.println("  </sitemap>");
    }
    writer.println("</sitemapindex>");
  }

  private List<SitemapUrlEntry> webPageEntries(String siteUrl) {
    List<SitemapUrlEntry> entries = new ArrayList<>();
    try {
      WebPageSpecification spec = new WebPageSpecification();
      spec.setEnabled(1);
      spec.setDraft(0);
      spec.setInSitemap(true);
      List<WebPage> pages = WebPageRepository.findAll(spec, null);

      if (pages != null) {
        for (WebPage page : pages) {
          if (page != null && StringUtils.isNotBlank(page.getLink())) {
            String lastmod = page.getModified() != null ? formatDate(page.getModified()) : null;
            String changefreq = StringUtils.isNotBlank(page.getSitemapChangeFrequency())
                ? escapeXml(page.getSitemapChangeFrequency())
                : null;
            String priority = formatPriority(page.getSitemapPriority());
            entries.add(new SitemapUrlEntry(escapeXml(siteUrl + page.getLink()), lastmod, changefreq, priority));
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding web pages to sitemap: " + e.getMessage());
    }
    return entries;
  }

  private List<SitemapUrlEntry> itemEntries(String siteUrl) {
    List<SitemapUrlEntry> entries = new ArrayList<>();
    try {
      ItemSpecification spec = new ItemSpecification();
      spec.setApprovedOnly(true);
      List<Item> items = ItemRepository.findAll(spec, null);

      if (items != null) {
        for (Item item : items) {
          if (item != null && item.getId() != null && StringUtils.isNotBlank(item.getUniqueId())) {
            String itemLink = "/show/" + item.getUniqueId();
            String lastmod = item.getModified() != null ? formatDate(item.getModified()) : null;
            entries.add(new SitemapUrlEntry(escapeXml(siteUrl + itemLink), lastmod, "monthly", "0.6"));
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding items to sitemap: " + e.getMessage());
    }
    return entries;
  }

  /**
   * Returns the priority formatted to one decimal place, or null when it should be omitted from
   * the output entirely -- either because it's genuinely unset, because it's the sitemap
   * protocol's own documented default of 0.5 (redundant to state explicitly), or because it's
   * exactly 0. WebPage's sitemapPriority field used to default to a bare `new BigDecimal(0)`
   * instead of the intended 0.5, so any page saved before that default was fixed has 0 stored even
   * though no admin ever deliberately chose the sitemap's lowest possible priority for their page.
   * Treating a stored 0 the same as "unset" for rendering avoids a data migration that couldn't
   * reliably tell that apart from a genuine (if unlikely) admin choice of exactly 0.0.
   */
  static String formatPriority(BigDecimal priority) {
    if (priority == null
        || priority.compareTo(BigDecimal.ZERO) == 0
        || priority.compareTo(DEFAULT_PRIORITY) == 0) {
      return null;
    }
    return priority.setScale(1, RoundingMode.HALF_UP).toPlainString();
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

  /** One &lt;url&gt; entry's already-formatted, already-escaped field values. */
  private static class SitemapUrlEntry {
    final String loc;
    final String lastmod;
    final String changefreq;
    final String priority;

    SitemapUrlEntry(String loc, String lastmod, String changefreq, String priority) {
      this.loc = loc;
      this.lastmod = lastmod;
      this.changefreq = changefreq;
      this.priority = priority;
    }
  }
}
