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
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
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
import java.util.Date;
import java.util.HashMap;
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

      // Add published blog posts
      addBlogPostsToSitemap(writer, siteUrl);

      // Add wiki pages (from enabled wikis)
      addWikiPagesToSitemap(writer, siteUrl);

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
      spec.setInSitemap(true);
      List<WebPage> pages = WebPageRepository.findAll(spec, null);

      if (pages != null) {
        for (WebPage page : pages) {
          if (page != null && StringUtils.isNotBlank(page.getLink())) {
            writer.println("  <url>");
            writer.println("    <loc>" + escapeXml(siteUrl + page.getLink()) + "</loc>");

            if (page.getModified() != null) {
              writer.println("    <lastmod>" + formatDate(page.getModified()) + "</lastmod>");
            }
            if (StringUtils.isNotBlank(page.getSitemapChangeFrequency())) {
              writer.println("    <changefreq>" + escapeXml(page.getSitemapChangeFrequency()) + "</changefreq>");
            }
            String priority = formatPriority(page.getSitemapPriority());
            if (priority != null) {
              writer.println("    <priority>" + priority + "</priority>");
            }
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
            String itemLink = "/show/" + item.getUniqueId();
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

  /**
   * Adds each published blog post's &lt;url&gt; entry. Mirrors BlogPostSearchResultsWidget's own
   * definition of "publicly findable" (setPublishedOnly(true) -- published IS NOT NULL) so the
   * sitemap never disagrees with the app's own search results about what's public. No
   * &lt;changefreq&gt;/&lt;priority&gt; is emitted since, unlike WebPage, BlogPost has no
   * per-post override for either -- inventing a value here would be a guess, not real data.
   */
  private void addBlogPostsToSitemap(PrintWriter writer, String siteUrl) {
    try {
      BlogPostSpecification spec = new BlogPostSpecification();
      spec.setPublishedOnly(true);
      List<BlogPost> posts = BlogPostRepository.findAll(spec, null);

      if (posts != null) {
        // BlogPost#getLink() re-queries its Blog on every call; batch-load each referenced Blog
        // once instead, the same way WikiSearchResultsWidget avoids the equivalent N+1 for wikis
        Map<Long, Blog> blogById = new HashMap<>();
        for (BlogPost post : posts) {
          if (post == null || StringUtils.isBlank(post.getUniqueId())) {
            continue;
          }
          Blog blog = blogById.computeIfAbsent(post.getBlogId(), BlogRepository::findById);
          if (blog == null) {
            continue;
          }
          writer.println("  <url>");
          writer.println("    <loc>" + escapeXml(siteUrl + "/" + blog.getUniqueId() + "/" + post.getUniqueId()) + "</loc>");
          if (post.getModified() != null) {
            writer.println("    <lastmod>" + formatDate(post.getModified()) + "</lastmod>");
          }
          writer.println("  </url>");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding blog posts to sitemap: " + e.getMessage());
    }
  }

  /**
   * Adds each wiki page's &lt;url&gt; entry, for pages belonging to an enabled wiki. Wiki pages
   * have no draft/published concept of their own (see WikiPage/WikiPageSpecification) -- the
   * parent Wiki's enabled flag is the only visibility signal that exists, so it's the only one
   * enforced here, matching what WikiSearchResultsWidget itself checks.
   */
  private void addWikiPagesToSitemap(PrintWriter writer, String siteUrl) {
    try {
      List<WikiPage> pages = WikiPageRepository.findAll(new WikiPageSpecification(), null);

      if (pages != null) {
        Map<Long, Wiki> wikiById = new HashMap<>();
        for (WikiPage page : pages) {
          if (page == null || StringUtils.isBlank(page.getUniqueId())) {
            continue;
          }
          Wiki wiki = wikiById.computeIfAbsent(page.getWikiId(), WikiRepository::findById);
          if (wiki == null || !wiki.getEnabled()) {
            continue;
          }
          writer.println("  <url>");
          writer.println("    <loc>" + escapeXml(siteUrl + "/" + wiki.getUniqueId() + "/" + page.getUniqueId()) + "</loc>");
          if (page.getModified() != null) {
            writer.println("    <lastmod>" + formatDate(page.getModified()) + "</lastmod>");
          }
          writer.println("  </url>");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding wiki pages to sitemap: " + e.getMessage());
    }
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
}
