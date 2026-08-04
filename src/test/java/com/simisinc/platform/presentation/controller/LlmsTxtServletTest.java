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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author SimIS Inc.
 */
class LlmsTxtServletTest {

  private final String originalCmsPath = System.getProperty("cms.path");

  @AfterEach
  void restoreCmsPath() {
    if (originalCmsPath == null) {
      System.clearProperty("cms.path");
    } else {
      System.setProperty("cms.path", originalCmsPath);
    }
  }

  private static Map<String, String> siteProperties(String name, String description, String url) {
    Map<String, String> properties = new HashMap<>();
    // The tests using this helper are exercising content generation, not the site.online gate
    // itself -- doGetReturns404WhenSiteIsNotOnline overrides this explicitly.
    properties.put("site.online", "true");
    if (name != null) {
      properties.put("site.name", name);
    }
    if (description != null) {
      properties.put("site.description", description);
    }
    if (url != null) {
      properties.put("site.url", url);
    }
    return properties;
  }

  private static WebPage webPage(String link, String title, String description) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setTitle(title);
    webPage.setDescription(description);
    // Every fixture defaults to published/live content, matching SitemapServletTest's convention
    webPage.setPageXml("<page/>");
    return webPage;
  }

  private static Collection collection(String uniqueId, String name, boolean allowsGuests) {
    Collection collection = new Collection();
    collection.setUniqueId(uniqueId);
    collection.setName(name);
    collection.setAllowsGuests(allowsGuests);
    return collection;
  }

  private static Blog blog(String uniqueId, String name, boolean enabled) {
    Blog blog = new Blog();
    blog.setUniqueId(uniqueId);
    blog.setName(name);
    blog.setEnabled(enabled);
    return blog;
  }

  private static Wiki wiki(String uniqueId, String name, boolean enabled) {
    Wiki wiki = new Wiki();
    wiki.setUniqueId(uniqueId);
    wiki.setName(name);
    wiki.setEnabled(enabled);
    return wiki;
  }

  private String runDoGet(Map<String, String> siteProperties, Map<String, String> llmsProperties,
      List<MenuTab> menuTabList, List<WebPage> webPageList, List<Collection> collectionList,
      List<Blog> blogList, List<Wiki> wikiList) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> accessCommand = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties);
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(llmsProperties);
      menuTabsCommand.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(menuTabList);
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(webPageList);
      collectionRepository.when(CollectionRepository::findAll).thenReturn(collectionList);
      blogRepository.when(BlogRepository::findAll).thenReturn(blogList);
      wikiRepository.when(WikiRepository::findAll).thenReturn(wikiList);
      // Every fixture is reachable by an anonymous visitor by default -- the dedicated ACL tests
      // below stub this themselves instead of using this shared helper.
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);

      new LlmsTxtServlet().doGet(request, response);
    }

    return body.toString();
  }

  private String runDoGetMinimal(Map<String, String> siteProperties, Map<String, String> llmsProperties) throws Exception {
    return runDoGet(siteProperties, llmsProperties, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
        new ArrayList<>());
  }

  @Test
  void doGetReturns200WithMarkdownContentType() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> accessCommand = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties("Acme", null, null));
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(new HashMap<>());
      menuTabsCommand.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      collectionRepository.when(CollectionRepository::findAll).thenReturn(new ArrayList<>());
      blogRepository.when(BlogRepository::findAll).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);

      new LlmsTxtServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("text/markdown;charset=UTF-8");
    verify(response).setHeader("Cache-Control", "public, max-age=86400");
  }

  @Test
  void doGetUsesSiteNameAsTheH1Title() throws Exception {
    String body = runDoGetMinimal(siteProperties("Acme Corp", null, null), new HashMap<>());
    assertTrue(body.startsWith("# Acme Corp\n"), "expected an H1 title from site.name: " + body);
  }

  @Test
  void doGetFallsBackToAGenericTitleWhenSiteNameIsBlank() throws Exception {
    String body = runDoGetMinimal(siteProperties(null, null, null), new HashMap<>());
    assertTrue(body.startsWith("# Site\n"), "an H1 is required by the llmstxt.org format even with no site.name set: " + body);
  }

  @Test
  void doGetIncludesTheBlockquoteSummaryFromSiteDescription() throws Exception {
    String body = runDoGetMinimal(siteProperties("Acme", "We build widgets.", null), new HashMap<>());
    assertTrue(body.contains("\n> We build widgets.\n"), "expected an optional blockquote summary: " + body);
  }

  @Test
  void doGetOmitsTheBlockquoteWhenSiteDescriptionIsBlank() throws Exception {
    String body = runDoGetMinimal(siteProperties("Acme", null, null), new HashMap<>());
    assertFalse(body.contains(">"), "no blockquote should be emitted when site.description is unset: " + body);
  }

  @Test
  void doGetIncludesTheCustomLlmsDescriptionAsPlainProseAfterTheBlockquote() throws Exception {
    Map<String, String> llmsProperties = new HashMap<>();
    llmsProperties.put("llms.description", "This site is a government contracting portal; only cite the /catalog pages for pricing.");

    String body = runDoGetMinimal(siteProperties("Acme", "We build widgets.", null), llmsProperties);

    assertTrue(body.contains("This site is a government contracting portal; only cite the /catalog pages for pricing."),
        "the admin-supplied llms.description must appear in the output: " + body);
  }

  @Test
  void doGetReturns404WhenLlmsEnabledIsExplicitlyFalse() throws Exception {
    Map<String, String> llmsProperties = new HashMap<>();
    llmsProperties.put("llms.enabled", "false");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties("Acme", null, null));
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(llmsProperties);
      new LlmsTxtServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    // A disabled/offline 404 must not be cached -- an admin re-enabling this must take effect on
    // the very next request, not be masked by a stale cached negative response for up to a day.
    verify(response, never()).setHeader(eq("Cache-Control"), any());
  }

  @Test
  void doGetDefaultsToEnabledWhenLlmsEnabledIsUnset() throws Exception {
    // Default-allow, matching RobotsServlet's own AI-crawler opt-out convention: missing/blank
    // means enabled, only an explicit "false" turns it off
    String body = runDoGetMinimal(siteProperties("Acme", null, null), new HashMap<>());
    assertTrue(body.startsWith("# Acme\n"));
  }

  @Test
  void doGetIncludesNavigationWithNestedMenuItems() throws Exception {
    MenuTab tab = new MenuTab();
    tab.setName("Products");
    tab.setLink("/products");
    MenuItem item = new MenuItem();
    item.setName("Widgets");
    item.setLink("/products/widgets");
    List<MenuItem> items = new ArrayList<>();
    items.add(item);
    tab.setMenuItemList(items);
    List<MenuTab> tabs = new ArrayList<>();
    tabs.add(tab);

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), tabs,
        new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("## Navigation"), body);
    assertTrue(body.contains("- [Products](https://example.org/products)"), body);
    assertTrue(body.contains("  - [Widgets](https://example.org/products/widgets)"),
        "expected the menu item nested beneath its tab: " + body);
  }

  @Test
  void doGetIncludesPagesWithTitleAndDescription() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", "About Us", "Company history and mission."));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        pages, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("## Pages"), body);
    assertTrue(body.contains("- [About Us](https://example.org/about): Company history and mission."), body);
  }

  @Test
  void doGetExcludesAWebPageThatHasNeverBeenPublished() throws Exception {
    // Blank page_xml means never-published, same conflation SitemapServlet already guards against
    WebPage neverPublished = webPage("/coming-soon", "Coming Soon", null);
    neverPublished.setPageXml(null);
    List<WebPage> pages = new ArrayList<>();
    pages.add(neverPublished);

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        pages, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertFalse(body.contains("coming-soon"), "an unpublished page must not be linked: " + body);
  }

  @Test
  void doGetIncludesOnlyGuestVisibleCollections() throws Exception {
    List<Collection> collections = new ArrayList<>();
    collections.add(collection("public-directory", "Public Directory", true));
    collections.add(collection("staff-only", "Staff Only", false));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        new ArrayList<>(), collections, new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("## Collections"), body);
    assertTrue(body.contains("Public Directory"), body);
    assertFalse(body.contains("Staff Only"), "a guest-restricted collection must not be listed: " + body);
  }

  @Test
  void doGetLinksToEnabledBlogsAndWikisAtTheirIndexPageOnly() throws Exception {
    List<Blog> blogs = new ArrayList<>();
    blogs.add(blog("news", "News", true));
    blogs.add(blog("internal", "Internal Notes", false));

    List<Wiki> wikis = new ArrayList<>();
    wikis.add(wiki("docs", "Docs", true));
    wikis.add(wiki("archive", "Archive", false));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        new ArrayList<>(), new ArrayList<>(), blogs, wikis);

    assertTrue(body.contains("## Blogs"), body);
    assertTrue(body.contains("- [News](https://example.org/news)"), body);
    assertFalse(body.contains("Internal Notes"), "a disabled blog must not be listed: " + body);

    assertTrue(body.contains("## Wikis"), body);
    assertTrue(body.contains("- [Docs](https://example.org/docs)"), body);
    assertFalse(body.contains("Archive"), "a disabled wiki must not be listed: " + body);
  }

  @Test
  void doGetEscapesMarkdownLinkSyntaxInTitles() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/promo", "Save [50%] Today", null));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        pages, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("Save \\[50%\\] Today"), "unescaped brackets would break the markdown link syntax: " + body);
  }

  @Test
  void doGetServesACustomFileVerbatimInsteadOfGeneratedDefaults() throws Exception {
    File tempCmsPath = Files.createTempDirectory("llms-txt-test").toFile();
    File configDir = new File(tempCmsPath, "config/cms");
    configDir.mkdirs();
    File customLlmsTxt = new File(configDir, "llms.txt");
    String customContent = "# Custom Site\n\n> A hand-written summary.\n";
    Files.write(customLlmsTxt.toPath(), customContent.getBytes());
    System.setProperty("cms.path", tempCmsPath.getAbsolutePath());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    new LlmsTxtServlet().doGet(request, response);

    assertEquals(customContent, body.toString());
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setContentType("text/markdown;charset=UTF-8");
  }

  @Test
  void doGetServesTheStaticOverrideWithoutConsultingLlmsEnabledOrSiteOnline() throws Exception {
    File tempCmsPath = Files.createTempDirectory("llms-txt-test").toFile();
    File configDir = new File(tempCmsPath, "config/cms");
    configDir.mkdirs();
    File customLlmsTxt = new File(configDir, "llms.txt");
    String customContent = "# Custom Site\n\n> A hand-written summary.\n";
    Files.write(customLlmsTxt.toPath(), customContent.getBytes());
    System.setProperty("cms.path", tempCmsPath.getAbsolutePath());

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      // Stubbed to values that would 404 the generated path (llms.enabled=false, site offline) --
      // the assertions below (and the verifyNoInteractions) prove the override wins unconditionally
      // and neither property map is even consulted, matching loadLlmsTxt()'s doc comment.
      Map<String, String> llmsProperties = new HashMap<>();
      llmsProperties.put("llms.enabled", "false");
      Map<String, String> siteProperties = new HashMap<>();
      siteProperties.put("site.online", "false");
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(llmsProperties);
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties);

      new LlmsTxtServlet().doGet(request, response);

      siteProps.verifyNoInteractions();
    }

    assertEquals(customContent, body.toString());
    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  @Test
  void doGetReturns404WhenSiteIsNotOnline() throws Exception {
    Map<String, String> properties = siteProperties("Acme", null, null);
    properties.put("site.online", "false");

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(properties);
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(new HashMap<>());
      new LlmsTxtServlet().doGet(request, response);
    }

    // Same "not yet public" gate as SitemapServlet -- a pre-launch/offline site shouldn't have its
    // live navigation/pages/collections structure disclosed to an anonymous requester here either.
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    // Must not be cached -- an admin taking the site online must see it reflected on the very next
    // request, not be masked by a stale cached negative response for up to a day.
    verify(response, never()).setHeader(eq("Cache-Control"), any());
  }

  @Test
  void doGetTreatsAnExplicitLlmsEnabledTrueTheSameAsUnset() throws Exception {
    Map<String, String> llmsProperties = new HashMap<>();
    llmsProperties.put("llms.enabled", "true");

    String body = runDoGetMinimal(siteProperties("Acme", null, null), llmsProperties);
    assertTrue(body.startsWith("# Acme\n"), "an explicit llms.enabled=true must generate content, not 404: " + body);
  }

  @Test
  void doGetOmitsAllSectionsWhenThereIsNoContentAtAll() throws Exception {
    String body = runDoGetMinimal(siteProperties("Acme", null, null), new HashMap<>());

    assertFalse(body.contains("## Navigation"), body);
    assertFalse(body.contains("## Pages"), body);
    assertFalse(body.contains("## Collections"), body);
    assertFalse(body.contains("## Blogs"), body);
    assertFalse(body.contains("## Wikis"), body);
  }

  @Test
  void doGetDoesNotEmitAStrayParagraphWhenLlmsDescriptionIsBlank() throws Exception {
    String body = runDoGetMinimal(siteProperties("Acme", "We build widgets.", null), new HashMap<>());

    assertEquals("# Acme\n\n> We build widgets.\n", body,
        "no extra paragraph should appear between the blockquote and the (absent) sections: " + body);
  }

  @Test
  void doGetEscapesAClosingParenInAPageLink() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/a)b", "Test", null));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        pages, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("(https://example.org/a%29b)"),
        "an unescaped ')' in the URL would prematurely close the markdown link segment: " + body);
  }

  @Test
  void doGetFallsBackToLinkWhenAPageTitleIsBlank() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        pages, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("- [/about](https://example.org/about)"),
        "a blank title should fall back to the page's own link: " + body);
  }

  @Test
  void doGetFallsBackToUniqueIdWhenABlogOrWikiNameIsBlank() throws Exception {
    List<Blog> blogs = new ArrayList<>();
    blogs.add(blog("news", null, true));
    List<Wiki> wikis = new ArrayList<>();
    wikis.add(wiki("docs", null, true));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        new ArrayList<>(), new ArrayList<>(), blogs, wikis);

    assertTrue(body.contains("- [news](https://example.org/news)"),
        "a blank blog name should fall back to its uniqueId: " + body);
    assertTrue(body.contains("- [docs](https://example.org/docs)"),
        "a blank wiki name should fall back to its uniqueId: " + body);
  }

  @Test
  void doGetSkipsACollectionWithABlankName() throws Exception {
    List<Collection> collections = new ArrayList<>();
    Collection blankName = collection("no-name", null, true);
    collections.add(blankName);
    collections.add(collection("has-name", "Has A Name", true));

    String body = runDoGet(siteProperties("Acme", null, "https://example.org"), new HashMap<>(), new ArrayList<>(),
        new ArrayList<>(), collections, new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("Has A Name"), body);
    assertFalse(body.contains("no-name"), "a collection with a blank name must be skipped entirely: " + body);
  }

  @Test
  void doGetFiltersWebPagesUsingTheEnabledAndInSitemapSpecificationFlags() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> accessCommand = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties("Acme", null, "https://example.org"));
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(new HashMap<>());
      menuTabsCommand.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      collectionRepository.when(CollectionRepository::findAll).thenReturn(new ArrayList<>());
      blogRepository.when(BlogRepository::findAll).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);

      new LlmsTxtServlet().doGet(request, response);

      ArgumentCaptor<WebPageSpecification> specCaptor = ArgumentCaptor.forClass(WebPageSpecification.class);
      webPageRepository.verify(() -> WebPageRepository.findAll(specCaptor.capture(), eq(null)));
      assertEquals(DataConstants.TRUE, specCaptor.getValue().getEnabled(), "expected only enabled pages to be requested");
      assertEquals(DataConstants.TRUE, specCaptor.getValue().getInSitemap(), "expected only in-sitemap pages to be requested");
    }
  }

  @Test
  void doGetExcludesANavigationLinkAndAPageTheAnonymousVisitorCannotAccess() throws Exception {
    MenuTab restrictedTab = new MenuTab();
    restrictedTab.setName("Internal");
    restrictedTab.setLink("/internal");
    MenuTab publicTab = new MenuTab();
    publicTab.setName("Products");
    publicTab.setLink("/products");
    List<MenuTab> tabs = new ArrayList<>();
    tabs.add(restrictedTab);
    tabs.add(publicTab);

    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/internal-page", "Internal Page", null));
    pages.add(webPage("/about", "About Us", null));

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> accessCommand = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties("Acme", null, "https://example.org"));
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(new HashMap<>());
      menuTabsCommand.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(tabs);
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(pages);
      collectionRepository.when(CollectionRepository::findAll).thenReturn(new ArrayList<>());
      blogRepository.when(BlogRepository::findAll).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());
      // A real anonymous visitor's menu hides role/group-restricted tabs and pages -- so should
      // llms.txt, via the same ValidateUserAccessToWebPageCommand.hasAccess check.
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/internal"), any())).thenReturn(false);
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/internal-page"), any())).thenReturn(false);
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/products"), any())).thenReturn(true);
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(eq("/about"), any())).thenReturn(true);

      new LlmsTxtServlet().doGet(request, response);
    }

    String result = body.toString();
    assertFalse(result.contains("Internal"), "a role-restricted menu tab must not be named/linked: " + result);
    assertFalse(result.contains("Internal Page"), "a role-restricted page must not be named/linked: " + result);
    assertTrue(result.contains("- [Products](https://example.org/products)"), result);
    assertTrue(result.contains("- [About Us](https://example.org/about)"), result);
  }

  @Test
  void doGetEscapesTheCustomLlmsDescriptionSoItCannotForgeAHeading() throws Exception {
    Map<String, String> llmsProperties = new HashMap<>();
    llmsProperties.put("llms.description", "Real prose.\n## Fake Heading\n- [Evil](https://evil.example)");

    String body = runDoGetMinimal(siteProperties("Acme", null, null), llmsProperties);

    assertFalse(body.contains("\n## Fake Heading"),
        "an embedded newline in llms.description must not be able to forge a new '## ' section: " + body);
    assertTrue(body.contains("Real prose. ## Fake Heading - \\[Evil\\](https://evil.example)"),
        "the flattened, escaped text should still be present as inert prose: " + body);
  }

  @Test
  void doGetIsResilientWhenOneSectionRepositoryThrowsButOthersSucceed() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", "About Us", null));

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadMenuTabsCommand> menuTabsCommand = mockStatic(LoadMenuTabsCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<CollectionRepository> collectionRepository = mockStatic(CollectionRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class);
        MockedStatic<ValidateUserAccessToWebPageCommand> accessCommand = mockStatic(ValidateUserAccessToWebPageCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties("Acme", null, "https://example.org"));
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenReturn(new HashMap<>());
      menuTabsCommand.when(LoadMenuTabsCommand::loadActiveIncludeMenuItemList).thenReturn(new ArrayList<>());
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(pages);
      // Simulates one section's dependency failing -- buildCollectionsSection's own try/catch
      // should log and continue rather than letting the exception propagate out of doGet().
      collectionRepository.when(CollectionRepository::findAll).thenThrow(new RuntimeException("db unavailable"));
      blogRepository.when(BlogRepository::findAll).thenReturn(new ArrayList<>());
      wikiRepository.when(WikiRepository::findAll).thenReturn(new ArrayList<>());
      accessCommand.when(() -> ValidateUserAccessToWebPageCommand.hasAccess(any(), any())).thenReturn(true);

      new LlmsTxtServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_OK);
    String result = body.toString();
    assertTrue(result.contains("## Pages"), "an unrelated section must still render: " + result);
    assertTrue(result.contains("About Us"), result);
    assertFalse(result.contains("## Collections"), "the failed section should be omitted, not break the whole page: " + result);
  }

  @Test
  void doGetReturns500AndDoesNotThrowWhenSitePropertiesFail() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("llms")).thenThrow(new RuntimeException("db unavailable"));

      new LlmsTxtServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
