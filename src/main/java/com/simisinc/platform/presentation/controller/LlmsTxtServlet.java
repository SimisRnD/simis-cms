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
import com.simisinc.platform.application.cms.LoadMenuTabsCommand;
import com.simisinc.platform.application.cms.ValidateUserAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.MenuItem;
import com.simisinc.platform.domain.model.cms.MenuTab;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * Serves {@code /llms.txt} (issue #417): an llmstxt.org-formatted markdown summary of the site --
 * name, description, primary navigation, and curated links to pages, collections, blogs, and
 * wikis -- for LLM/agentic-browsing consumers rather than crawlers (the {@code robots.txt}
 * equivalent for that audience). Chrome's Lighthouse 13.3.0 "Agentic Browsing" audit (shipped to
 * the default configuration 2026-05-05) checks for this file's presence.
 *
 * <p>
 * Mirrors {@link RobotsServlet}'s response shape and its {@code cms.path}-based static-file
 * override convention exactly (same resolution order, same {@code config/cms/} directory, just a
 * different filename), and mirrors {@link SitemapServlet}'s annotation-based servlet mapping and
 * its pattern of pulling real, live content straight from the repositories rather than a static
 * template. Unlike {@code sitemap.xml} (deliberately exhaustive -- every item, every blog post,
 * every wiki page), the llmstxt.org spec describes a <em>curated</em> summary: this intentionally
 * links to each enabled Blog/Wiki's own index page rather than enumerating every post/page within
 * it, matching the recommendation from the research pass behind this issue.
 * </p>
 *
 * @author SimIS Inc.
 */
@WebServlet(name = "LlmsTxtServlet", urlPatterns = {"/llms.txt"})
public class LlmsTxtServlet extends HttpServlet {

  private static final Log LOG = LogFactory.getLog(LlmsTxtServlet.class);

  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    response.setContentType("text/markdown;charset=UTF-8");

    try {
      String llmsTxtContent = loadLlmsTxt();
      if (StringUtils.isBlank(llmsTxtContent)) {
        // A static override file always wins, unconditionally, matching RobotsServlet -- neither
        // the llms.enabled toggle nor the site.online gate below apply to it, since a hand-authored
        // override contains no live repository content for either one to protect.
        Map<String, String> llmsPropertyMap = LoadSitePropertyCommand.loadAsMap("llms");
        if (!isEnabled(llmsPropertyMap)) {
          // Not cached -- an admin flipping this toggle back on must take effect immediately, not
          // be masked by a stale cached 404 in a browser or CDN for up to a day.
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          response.getWriter().print("<!-- llms.txt is disabled (llms.enabled) -->\n");
          return;
        }

        Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");
        // Same "not yet public" gate SitemapServlet applies (site.online) -- a site an admin hasn't
        // taken online shouldn't have its live navigation/pages/collections structure disclosed to
        // an anonymous requester here either.
        if (!"true".equals(sitePropertyMap.getOrDefault("site.online", "false"))) {
          // Not cached, for the same reason as the llms.enabled branch above -- taking the site
          // online must be reflected on the very next request.
          response.setStatus(HttpServletResponse.SC_NOT_FOUND);
          response.getWriter().print("<!-- Site is not yet online -->\n");
          return;
        }

        llmsTxtContent = generateDefaultLlmsTxt(sitePropertyMap, llmsPropertyMap);
      }

      // Only a real, successfully generated (or statically overridden) body is cacheable.
      response.setHeader("Cache-Control", "public, max-age=86400");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().print(llmsTxtContent);
    } catch (Exception e) {
      LOG.error("Error serving llms.txt: " + e.getMessage(), e);
      response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      response.getWriter().print("<!-- Error generating llms.txt -->\n");
    }
  }

  /**
   * Same resolution order as {@link RobotsServlet#loadRobotsTxt()}: the {@code cms.path} system
   * property (falling back to {@code user.dir}), then {@code config/cms/llms.txt} beneath it,
   * served byte-for-byte verbatim when present -- no parsing, no templating.
   */
  private String loadLlmsTxt() {
    try {
      String configPath = System.getProperty("cms.path");
      if (StringUtils.isBlank(configPath)) {
        configPath = System.getProperty("user.dir");
      }

      File llmsTxtFile = new File(configPath, "config/cms/llms.txt");
      if (llmsTxtFile.exists() && llmsTxtFile.canRead()) {
        LOG.debug("Loading custom llms.txt from: " + llmsTxtFile.getAbsolutePath());
        return new String(Files.readAllBytes(llmsTxtFile.toPath()));
      }
    } catch (Exception e) {
      LOG.warn("Error loading custom llms.txt file: " + e.getMessage());
    }
    return null;
  }

  /**
   * Default-enabled: missing/blank/anything other than a literal (case-insensitive) "false" is
   * treated as enabled, matching RobotsServlet's addAiCrawlerRules() default-allow convention.
   */
  private boolean isEnabled(Map<String, String> llmsPropertyMap) {
    String value = llmsPropertyMap.get("llms.enabled");
    return StringUtils.isBlank(value) || !"false".equalsIgnoreCase(value.trim());
  }

  private String generateDefaultLlmsTxt(Map<String, String> sitePropertyMap, Map<String, String> llmsPropertyMap) {
    String siteUrl = StringUtils.trimToEmpty(sitePropertyMap.get("site.url"));
    String siteName = sitePropertyMap.get("site.name");
    String siteDescription = sitePropertyMap.get("site.description");
    String llmsDescription = llmsPropertyMap.get("llms.description");

    // An anonymous, unauthenticated stand-in for whoever is requesting this public file -- used to
    // apply the same per-tab/page role-and-group access check MainMenuWidget applies to a real
    // anonymous visitor's menu, so a role-restricted tab or page isn't named and linked here even
    // though a real anonymous visitor would never see it rendered or be able to load it directly.
    UserSession anonymousSession = new UserSession();

    StringBuilder sb = new StringBuilder();

    // H1 title -- required by the llmstxt.org format. Falls back to a generic label rather than
    // 404ing when site.name hasn't been set, since RobotsServlet's own precedent never 404s for a
    // missing/optional site property either.
    sb.append("# ").append(escapeMarkdownText(StringUtils.isNotBlank(siteName) ? siteName : "Site")).append("\n");

    // Optional blockquote summary
    if (StringUtils.isNotBlank(siteDescription)) {
      sb.append("\n> ").append(escapeMarkdownText(siteDescription)).append("\n");
    }

    // Optional free-text supplement a site owner can add from the admin UI without editing files
    // directly (this issue's acceptance criteria) -- rendered as plain prose directly after the
    // blockquote, matching the llmstxt.org spec's optional additional-context paragraph(s).
    if (StringUtils.isNotBlank(llmsDescription)) {
      // Escaped like every other dynamic string below -- this field is free text an admin can
      // enter without going through markdown-safe rendering, and an embedded newline could
      // otherwise forge a fake "## Heading" that reads as part of the auto-generated structure.
      sb.append("\n").append(escapeMarkdownText(llmsDescription)).append("\n");
    }

    appendSection(sb, "Navigation", buildNavigationSection(siteUrl, anonymousSession));
    appendSection(sb, "Pages", buildPagesSection(siteUrl, anonymousSession));
    appendSection(sb, "Collections", buildCollectionsSection(siteUrl));
    appendSection(sb, "Blogs", buildBlogsSection(siteUrl));
    appendSection(sb, "Wikis", buildWikisSection(siteUrl));

    return sb.toString();
  }

  private void appendSection(StringBuilder sb, String heading, String body) {
    if (StringUtils.isNotBlank(body)) {
      sb.append("\n## ").append(heading).append("\n\n").append(body);
    }
  }

  /**
   * One bullet per active (enabled, non-draft -- already filtered by
   * {@code MenuTabRepository.findAllActive()}) top-level menu tab, with its own menu items nested
   * beneath it -- the same structure rendered in the public site header today. Each tab/item is
   * additionally required to pass {@link ValidateUserAccessToWebPageCommand#hasAccess}, exactly the
   * per-page role/group check {@code MainMenuWidget} applies before showing a tab or item to a real
   * anonymous visitor -- otherwise a role-restricted tab would be named and linked here even though
   * no anonymous visitor could ever see or reach it.
   */
  private String buildNavigationSection(String siteUrl, UserSession anonymousSession) {
    StringBuilder sb = new StringBuilder();
    try {
      List<MenuTab> menuTabList = LoadMenuTabsCommand.loadActiveIncludeMenuItemList();
      if (menuTabList != null) {
        for (MenuTab menuTab : menuTabList) {
          if (menuTab == null || StringUtils.isBlank(menuTab.getLink())
              || !ValidateUserAccessToWebPageCommand.hasAccess(menuTab.getLink(), anonymousSession)) {
            continue;
          }
          String name = StringUtils.isNotBlank(menuTab.getName()) ? menuTab.getName() : menuTab.getLink();
          sb.append("- [").append(escapeMarkdownText(name)).append("](")
              .append(escapeMarkdownUrl(siteUrl + menuTab.getLink())).append(")\n");
          if (menuTab.getMenuItemList() != null) {
            for (MenuItem menuItem : menuTab.getMenuItemList()) {
              if (menuItem == null || StringUtils.isBlank(menuItem.getLink())
                  || !ValidateUserAccessToWebPageCommand.hasAccess(menuItem.getLink(), anonymousSession)) {
                continue;
              }
              String itemName = StringUtils.isNotBlank(menuItem.getName()) ? menuItem.getName() : menuItem.getLink();
              sb.append("  - [").append(escapeMarkdownText(itemName)).append("](")
                  .append(escapeMarkdownUrl(siteUrl + menuItem.getLink())).append(")\n");
            }
          }
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding navigation to llms.txt: " + e.getMessage());
    }
    return sb.toString();
  }

  /**
   * Same live-page filter SitemapServlet's webPageEntries() uses (enabled, marked for inclusion,
   * and actually published -- draft is deliberately not filtered at the specification level; see
   * that method's own comment for why a published page can still have draft=true). Unlike
   * sitemap.xml's bare {@code <loc>}, llms.txt's markdown link-list format wants the page's own
   * title and description, so both are read here. Also requires
   * {@link ValidateUserAccessToWebPageCommand#hasAccess}, the same per-page role/group check
   * applied to the Navigation section, so a role-restricted page isn't named and described here.
   */
  private String buildPagesSection(String siteUrl, UserSession anonymousSession) {
    StringBuilder sb = new StringBuilder();
    try {
      WebPageSpecification spec = new WebPageSpecification();
      spec.setEnabled(1);
      spec.setInSitemap(true);
      // Mirrors SitemapServlet.webPageEntries(), which deliberately excludes archived pages the
      // same way -- archivedOnly defaults to UNDEFINED (includes both), so without this an
      // archived page's title/description/link would still be named here even though
      // sitemap.xml, the other fully-public unauthenticated listing of this same entity, does not.
      spec.setArchivedOnly(false);
      List<WebPage> pages = WebPageRepository.findAll(spec, null);

      if (pages != null) {
        for (WebPage page : pages) {
          if (page == null || StringUtils.isBlank(page.getLink()) || StringUtils.isBlank(page.getPageXml())
              || !ValidateUserAccessToWebPageCommand.hasAccess(page.getLink(), anonymousSession)) {
            continue;
          }
          String title = StringUtils.isNotBlank(page.getTitle()) ? page.getTitle() : page.getLink();
          sb.append("- [").append(escapeMarkdownText(title)).append("](")
              .append(escapeMarkdownUrl(siteUrl + page.getLink())).append(")");
          if (StringUtils.isNotBlank(page.getDescription())) {
            sb.append(": ").append(escapeMarkdownText(page.getDescription()));
          }
          sb.append("\n");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding pages to llms.txt: " + e.getMessage());
    }
    return sb.toString();
  }

  /**
   * Only collections a guest (anonymous) visitor could actually reach belong in a public,
   * unauthenticated file -- {@code allowsGuests} is the domain model's own signal for that (see
   * {@code Collection.java}). No reusable specification-level flag exists for this the way
   * WebPage/Item/BlogPost each have their own "public" specification flag (confirmed during the
   * research pass behind this issue), so the field is checked directly here instead.
   */
  private String buildCollectionsSection(String siteUrl) {
    StringBuilder sb = new StringBuilder();
    try {
      List<Collection> collections = CollectionRepository.findAll();
      if (collections != null) {
        for (Collection collection : collections) {
          if (collection == null || !collection.getAllowsGuests() || StringUtils.isBlank(collection.getName())) {
            continue;
          }
          sb.append("- [").append(escapeMarkdownText(collection.getName())).append("](")
              .append(escapeMarkdownUrl(siteUrl + collection.createListingsLink())).append(")");
          if (StringUtils.isNotBlank(collection.getDescription())) {
            sb.append(": ").append(escapeMarkdownText(collection.getDescription()));
          }
          sb.append("\n");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding collections to llms.txt: " + e.getMessage());
    }
    return sb.toString();
  }

  /**
   * llms.txt is meant to be a curated summary (unlike sitemap.xml's deliberately exhaustive
   * enumeration) -- this links to each enabled blog's own index page rather than every post in it.
   */
  private String buildBlogsSection(String siteUrl) {
    StringBuilder sb = new StringBuilder();
    try {
      List<Blog> blogs = BlogRepository.findAll();
      if (blogs != null) {
        for (Blog blog : blogs) {
          if (blog == null || !blog.getEnabled() || StringUtils.isBlank(blog.getUniqueId())) {
            continue;
          }
          String name = StringUtils.isNotBlank(blog.getName()) ? blog.getName() : blog.getUniqueId();
          sb.append("- [").append(escapeMarkdownText(name)).append("](")
              .append(escapeMarkdownUrl(siteUrl + blog.getLink())).append(")");
          if (StringUtils.isNotBlank(blog.getDescription())) {
            sb.append(": ").append(escapeMarkdownText(blog.getDescription()));
          }
          sb.append("\n");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding blogs to llms.txt: " + e.getMessage());
    }
    return sb.toString();
  }

  /** Same curated, index-page-only approach as {@link #buildBlogsSection(String)}, for wikis. */
  private String buildWikisSection(String siteUrl) {
    StringBuilder sb = new StringBuilder();
    try {
      List<Wiki> wikis = WikiRepository.findAll();
      if (wikis != null) {
        for (Wiki wiki : wikis) {
          if (wiki == null || !wiki.getEnabled() || StringUtils.isBlank(wiki.getUniqueId())) {
            continue;
          }
          String name = StringUtils.isNotBlank(wiki.getName()) ? wiki.getName() : wiki.getUniqueId();
          sb.append("- [").append(escapeMarkdownText(name)).append("](")
              .append(escapeMarkdownUrl(siteUrl + "/" + wiki.getUniqueId())).append(")");
          if (StringUtils.isNotBlank(wiki.getDescription())) {
            sb.append(": ").append(escapeMarkdownText(wiki.getDescription()));
          }
          sb.append("\n");
        }
      }
    } catch (Exception e) {
      LOG.warn("Error adding wikis to llms.txt: " + e.getMessage());
    }
    return sb.toString();
  }

  /**
   * Escapes the two characters that would otherwise prematurely break a markdown link's
   * {@code [text]} segment, and flattens embedded line breaks so a title/description with a stray
   * newline can't split one bullet into two.
   */
  private static String escapeMarkdownText(String text) {
    if (text == null) {
      return "";
    }
    return text.replace("\\", "\\\\")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("\r", " ")
        .replace("\n", " ")
        .trim();
  }

  /** Escapes the one character that would prematurely close a markdown link's {@code (url)} segment. */
  private static String escapeMarkdownUrl(String url) {
    if (url == null) {
      return "";
    }
    return url.replace(")", "%29");
  }
}
