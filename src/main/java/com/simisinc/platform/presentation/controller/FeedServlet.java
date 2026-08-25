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
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Serves an Atom 1.0 feed of published blog posts (issue #1182).
 *
 * <p>Two routes, both handled here:
 *
 * <ul>
 * <li>{@code /feed.xml} -- every published post across every blog</li>
 * <li>{@code /feed/{blogUniqueId}.xml} -- one blog's posts</li>
 * </ul>
 *
 * <p>The per-blog route deliberately lives under {@code /feed/} rather than at
 * {@code /{blogUniqueId}/feed.xml}. A blog post's own URL is
 * {@code /{blogUniqueId}/{postUniqueId}} (see SitemapServlet#blogPostEntries), so the latter
 * shape would collide with a real post whose uniqueId happened to be "feed.xml" -- and which of
 * the two won would depend on servlet mapping precedence rather than on anything an author could
 * see. Keeping feeds in their own namespace makes the collision impossible.
 *
 * <p>Atom rather than RSS 2.0: Atom requires a globally unique {@code id} per entry and a
 * well-defined date format, both of which this content already has, and every consumer that
 * reads RSS also reads Atom.
 */
@WebServlet(name = "FeedServlet", urlPatterns = {"/feed.xml", "/feed/*"})
public class FeedServlet extends HttpServlet {

  private static final long serialVersionUID = 8465216157905L;
  private static Log LOG = LogFactory.getLog(FeedServlet.class);

  /** A feed is a summary surface, not an archive -- cap it so one long-lived blog can't grow unbounded. */
  private static final int MAX_ENTRIES = 50;

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
      String siteUrl = StringUtils.trimToNull(sitePropertyMap.get("site.url"));

      if (siteUrl == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // The same gates SitemapServlet applies, and for the same reason: a site an admin has not
      // taken online yet should not be syndicating its content either
      if (!"true".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      if (!"true".equals(sitePropertyMap.getOrDefault("site.feed.xml", "false"))) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      // Resolve the requested blog, if any. A /feed/... path that names a blog which does not
      // exist or is disabled is a 404 rather than a silent fall-through to the site-wide feed --
      // quietly serving a different feed than the one asked for is worse than saying no.
      Blog blog = null;
      String blogUniqueId = blogUniqueIdFrom(request.getPathInfo());
      if (blogUniqueId != null) {
        blog = BlogRepository.findByUniqueId(blogUniqueId);
        if (blog == null || !blog.getEnabled()) {
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          return;
        }
      } else if (isPerBlogPath(request.getPathInfo())) {
        // /feed/ with nothing after it names no blog at all
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      String feedXml = renderFeed(siteUrl, sitePropertyMap, blog);

      response.setContentType("application/atom+xml");
      response.setCharacterEncoding("UTF-8");
      response.getWriter().print(feedXml);
    } catch (Exception e) {
      LOG.error("Error generating feed: " + e.getMessage());
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * True when the request came in on the {@code /feed/*} mapping rather than {@code /feed.xml}.
   * Container behaviour differs on whether an exact {@code /feed} match yields null or "/", so
   * both are treated as the per-blog route with no blog named.
   */
  static boolean isPerBlogPath(String pathInfo) {
    return pathInfo != null;
  }

  /**
   * Extracts the blog uniqueId from a {@code /feed/*} path, tolerating the optional {@code .xml}
   * suffix and a trailing slash. Returns null when the path names no blog.
   */
  static String blogUniqueIdFrom(String pathInfo) {
    if (pathInfo == null) {
      return null;
    }
    String candidate = StringUtils.strip(pathInfo, "/");
    if (StringUtils.isBlank(candidate)) {
      return null;
    }
    if (candidate.toLowerCase().endsWith(".xml")) {
      candidate = candidate.substring(0, candidate.length() - 4);
    }
    // A nested path is not a blog id; only a single segment can name one
    if (candidate.contains("/")) {
      return null;
    }
    return StringUtils.trimToNull(candidate);
  }

  /**
   * Builds the Atom document. Posts are read with the same published/archived filters
   * SitemapServlet uses, so the feed, the sitemap and the site's own search agree about what is
   * public -- a feed that syndicates a post the site will not show is a content leak, not a
   * convenience.
   */
  private String renderFeed(String siteUrl, Map<String, String> sitePropertyMap, Blog blog) {
    BlogPostSpecification spec = new BlogPostSpecification();
    spec.setPublishedOnly(true);
    spec.setArchivedOnly(false);
    // Syndication opt-out (#1419): a post can stay published and searchable while being kept out
    // of the feed. Archiving would hide it from the site as well, which is a different decision.
    spec.setExcludedFromFeed(false);
    if (blog != null) {
      spec.setBlogId(blog.getId());
    }

    // Newest first. The <published> element below uses startDate when set and falls back to
    // published, so the ordering has to key on the same value or the feed's own dates come back
    // out of sequence. Without any ORDER BY the database returned rows in arbitrary (effectively
    // insertion) order and the MAX_ENTRIES cap kept whichever 50 arrived first -- on a site whose
    // posts were bulk-imported that was the oldest 50, so recent posts never reached subscribers.
    //
    // No page size is set: the cap is applied below, after posts belonging to a disabled blog are
    // skipped, so a SQL LIMIT here would silently under-fill the feed.
    DataConstraints constraints = new DataConstraints();
    constraints.setUseCount(false);
    constraints.setDefaultColumnToSortBy("COALESCE(start_date, published) DESC, post_id DESC");
    List<BlogPost> posts = BlogPostRepository.findAll(spec, constraints);
    List<FeedEntry> entries = new ArrayList<>();

    if (posts != null) {
      // BlogPost#getLink() re-queries its Blog on every call; batch-load each referenced Blog
      // once instead, the same way SitemapServlet avoids the equivalent N+1
      Map<Long, Blog> blogById = new HashMap<>();
      if (blog != null) {
        blogById.put(blog.getId(), blog);
      }
      for (BlogPost post : posts) {
        if (post == null || StringUtils.isBlank(post.getUniqueId())) {
          continue;
        }
        Blog postBlog = blogById.computeIfAbsent(post.getBlogId(), BlogRepository::findById);
        // A disabled blog's posts are not public even when the post itself is published
        if (postBlog == null || !postBlog.getEnabled() || StringUtils.isBlank(postBlog.getUniqueId())) {
          continue;
        }
        entries.add(new FeedEntry(post, siteUrl + "/" + postBlog.getUniqueId() + "/" + post.getUniqueId()));
        if (entries.size() >= MAX_ENTRIES) {
          break;
        }
      }
    }

    String siteName = StringUtils.defaultIfBlank(sitePropertyMap.get("site.name"), "Site");
    String feedTitle = blog != null ? siteName + " - " + blog.getName() : siteName;
    String selfUrl = blog != null
        ? siteUrl + "/feed/" + blog.getUniqueId() + ".xml"
        : siteUrl + "/feed.xml";

    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<feed xmlns=\"http://www.w3.org/2005/Atom\">\n");
    xml.append("  <title>").append(escapeXml(feedTitle)).append("</title>\n");
    xml.append("  <link href=\"").append(escapeXml(siteUrl)).append("/\"/>\n");
    xml.append("  <link rel=\"self\" href=\"").append(escapeXml(selfUrl)).append("\"/>\n");
    xml.append("  <id>").append(escapeXml(selfUrl)).append("</id>\n");
    // Atom requires <updated>; derive it from the newest entry rather than "now" so a feed whose
    // content has not changed keeps a stable value that conditional-GET tooling can rely on
    xml.append("  <updated>").append(formatDate(mostRecent(entries))).append("</updated>\n");
    if (StringUtils.isNotBlank(sitePropertyMap.get("site.description"))) {
      xml.append("  <subtitle>").append(escapeXml(sitePropertyMap.get("site.description"))).append("</subtitle>\n");
    }

    for (FeedEntry entry : entries) {
      BlogPost post = entry.post;
      xml.append("  <entry>\n");
      xml.append("    <title>").append(escapeXml(StringUtils.defaultString(post.getTitle()))).append("</title>\n");
      xml.append("    <link href=\"").append(escapeXml(entry.url)).append("\"/>\n");
      xml.append("    <id>").append(escapeXml(entry.url)).append("</id>\n");
      xml.append("    <updated>").append(formatDate(entry.updated())).append("</updated>\n");
      if (post.getStartDate() != null || post.getPublished() != null) {
        xml.append("    <published>")
            .append(formatDate(post.getStartDate() != null ? post.getStartDate() : post.getPublished()))
            .append("</published>\n");
      }
      String summary = summaryFor(post);
      if (summary != null) {
        xml.append("    <summary>").append(escapeXml(summary)).append("</summary>\n");
      }
      xml.append("  </entry>\n");
    }

    xml.append("</feed>\n");
    return xml.toString();
  }

  /**
   * Prefers the curated summary and falls back to the body with markup stripped. Atom's
   * {@code <summary>} is declared as text here, so raw HTML would have to be escaped into
   * unreadable noise rather than rendered.
   */
  static String summaryFor(BlogPost post) {
    String summary = StringUtils.trimToNull(post.getSummary());
    if (summary != null) {
      return summary;
    }
    if (StringUtils.isBlank(post.getBody())) {
      return null;
    }
    return StringUtils.trimToNull(StringUtils.abbreviate(HtmlCommand.text(post.getBody()), 500));
  }

  private static Timestamp mostRecent(List<FeedEntry> entries) {
    Timestamp newest = null;
    for (FeedEntry entry : entries) {
      Timestamp candidate = entry.updated();
      if (candidate != null && (newest == null || candidate.after(newest))) {
        newest = candidate;
      }
    }
    return newest;
  }

  /** Atom demands RFC 3339; a null date falls back to the epoch so the element is never empty. */
  static String formatDate(Timestamp timestamp) {
    if (timestamp == null) {
      return Instant.EPOCH.toString();
    }
    return timestamp.toInstant().toString();
  }

  static String escapeXml(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  /** A post paired with the absolute URL it was resolved to, so the blog lookup happens once. */
  private static class FeedEntry {
    private final BlogPost post;
    private final String url;

    FeedEntry(BlogPost post, String url) {
      this.post = post;
      this.url = url;
    }

    Timestamp updated() {
      if (post.getModified() != null) {
        return post.getModified();
      }
      if (post.getStartDate() != null) {
        return post.getStartDate();
      }
      return post.getPublished();
    }
  }
}
