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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author SimIS Inc.
 */
class SitemapServletTest {

  @Test
  void formatPriorityOmitsANullPriority() {
    assertNull(SitemapServlet.formatPriority(null));
  }

  @Test
  void formatPriorityOmitsZero() {
    // WebPage.sitemapPriority used to default to a bare 0 instead of the intended 0.5, so any
    // page saved before that was fixed has a stored 0 nobody actually chose
    assertNull(SitemapServlet.formatPriority(BigDecimal.ZERO));
  }

  @Test
  void formatPriorityOmitsTheSitemapProtocolDefaultOfPointFive() {
    assertNull(SitemapServlet.formatPriority(new BigDecimal("0.5")));
    assertNull(SitemapServlet.formatPriority(new BigDecimal("0.50")));
  }

  @Test
  void formatPriorityReturnsAnExplicitNonDefaultValue() {
    assertEquals("0.8", SitemapServlet.formatPriority(new BigDecimal("0.8")));
    assertEquals("1.0", SitemapServlet.formatPriority(new BigDecimal("1")));
    assertEquals("0.1", SitemapServlet.formatPriority(new BigDecimal("0.1")));
  }

  @Test
  void formatPriorityRoundsToOneDecimalPlace() {
    assertEquals("0.3", SitemapServlet.formatPriority(new BigDecimal("0.33")));
  }

  private static Map<String, String> siteProperties(boolean online, boolean sitemapEnabled) {
    Map<String, String> properties = new HashMap<>();
    properties.put("site.url", "https://example.org");
    properties.put("site.online", String.valueOf(online));
    properties.put("site.sitemap.xml", String.valueOf(sitemapEnabled));
    return properties;
  }

  private static WebPage webPage(String link, String changeFrequency, BigDecimal priority) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setSitemapChangeFrequency(changeFrequency);
    if (priority != null) {
      webPage.setSitemapPriority(priority);
    }
    webPage.setModified(Timestamp.valueOf("2026-03-15 12:30:00"));
    return webPage;
  }

  private static Item item(long id, String uniqueId) {
    Item item = new Item();
    item.setId(id);
    item.setUniqueId(uniqueId);
    return item;
  }

  private String runDoGet(Map<String, String> properties, List<WebPage> webPageList, List<Item> itemList) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(properties);
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(webPageList);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(itemList);

      new SitemapServlet().doGet(request, response);
    }

    return body.toString();
  }

  @Test
  void doGetReturns404WhenSiteUrlIsBlank() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(new HashMap<>());
      new SitemapServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns404WhenSiteIsNotOnline() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(false, true));
      new SitemapServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns404WhenSitemapXmlIsDisabled() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, false));
      new SitemapServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns200AndIncludesTheHomepageWhenOnlineAndEnabled() throws Exception {
    String body = runDoGet(siteProperties(true, true), new ArrayList<>(), new ArrayList<>());

    assertTrue(body.contains("<loc>https://example.org/</loc>"));
    assertTrue(body.contains("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">"));
  }

  @Test
  void doGetFiltersOnShowInSitemap() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    ArgumentCaptor<WebPageSpecification> captor = ArgumentCaptor.forClass(WebPageSpecification.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(captor.capture(), any())).thenReturn(pages);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new SitemapServlet().doGet(request, response);
    }

    assertEquals(DataConstants.TRUE, captor.getValue().getInSitemap());
  }

  @Test
  void doGetUsesTheRealPerPageChangeFrequencyAndPriorityWhenSet() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", "daily", new BigDecimal("0.9")));

    String body = runDoGet(siteProperties(true, true), pages, new ArrayList<>());

    assertTrue(body.contains("<loc>https://example.org/about</loc>"));
    assertTrue(body.contains("<changefreq>daily</changefreq>"));
    assertTrue(body.contains("<priority>0.9</priority>"));
  }

  @Test
  void doGetOmitsChangeFrequencyAndPriorityWhenNeitherIsSet() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    String body = runDoGet(siteProperties(true, true), pages, new ArrayList<>());

    String[] lines = body.split("\n");
    boolean insideAboutUrl = false;
    for (String line : lines) {
      if (line.contains("<loc>https://example.org/about</loc>")) {
        insideAboutUrl = true;
      } else if (insideAboutUrl && line.contains("</url>")) {
        break;
      } else if (insideAboutUrl) {
        assertFalse(line.contains("<changefreq>"), "unset changefreq must be omitted, not fabricated: " + line);
        assertFalse(line.contains("<priority>"), "unset (0.5-default) priority must be omitted: " + line);
      }
    }
    assertTrue(insideAboutUrl, "the /about page should have been in the sitemap");
  }

  @Test
  void doGetUsesTheShowUrlPrefixForItems() throws Exception {
    List<Item> items = new ArrayList<>();
    items.add(item(1L, "widget"));

    String body = runDoGet(siteProperties(true, true), new ArrayList<>(), items);

    assertTrue(body.contains("<loc>https://example.org/show/widget</loc>"),
        "items must use /show/{uniqueId}, matching every other item link in the app: " + body);
    assertFalse(body.contains("/item/widget"));
  }

  private static BlogPost blogPost(long blogId, String uniqueId, Timestamp modified) {
    BlogPost post = new BlogPost();
    post.setBlogId(blogId);
    post.setUniqueId(uniqueId);
    post.setModified(modified);
    return post;
  }

  private static Blog blog(String uniqueId) {
    Blog blog = new Blog();
    blog.setUniqueId(uniqueId);
    return blog;
  }

  private static WikiPage wikiPage(long wikiId, String uniqueId, Timestamp modified) {
    WikiPage page = new WikiPage();
    page.setWikiId(wikiId);
    page.setUniqueId(uniqueId);
    page.setModified(modified);
    return page;
  }

  private static Wiki wiki(String uniqueId, boolean enabled) {
    Wiki wiki = new Wiki();
    wiki.setUniqueId(uniqueId);
    wiki.setEnabled(enabled);
    return wiki;
  }

  private String runDoGetWithBlogAndWiki(List<BlogPost> blogPostList, Map<Long, Blog> blogById,
      List<WikiPage> wikiPageList, Map<Long, Wiki> wikiById) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class);
        MockedStatic<BlogRepository> blogRepository = mockStatic(BlogRepository.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class);
        MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      blogPostRepository.when(() -> BlogPostRepository.findAll(any(), any())).thenReturn(blogPostList);
      blogRepository.when(() -> BlogRepository.findById(anyLong())).thenAnswer(invocation -> blogById.get((Long) invocation.getArgument(0)));
      wikiPageRepository.when(() -> WikiPageRepository.findAll(any(), any())).thenReturn(wikiPageList);
      wikiRepository.when(() -> WikiRepository.findById(anyLong())).thenAnswer(invocation -> wikiById.get((Long) invocation.getArgument(0)));

      new SitemapServlet().doGet(request, response);
    }

    return body.toString();
  }

  @Test
  void doGetOnlyQueriesPublishedBlogPosts() throws Exception {
    ArgumentCaptor<BlogPostSpecification> captor = ArgumentCaptor.forClass(BlogPostSpecification.class);
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class);
        MockedStatic<BlogPostRepository> blogPostRepository = mockStatic(BlogPostRepository.class);
        MockedStatic<WikiPageRepository> wikiPageRepository = mockStatic(WikiPageRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());
      blogPostRepository.when(() -> BlogPostRepository.findAll(captor.capture(), any())).thenReturn(new ArrayList<>());
      wikiPageRepository.when(() -> WikiPageRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new SitemapServlet().doGet(request, response);
    }

    assertEquals(DataConstants.TRUE, captor.getValue().getPublishedOnly());
  }

  @Test
  void doGetIncludesPublishedBlogPostsUsingTheBlogAndPostUniqueIds() throws Exception {
    List<BlogPost> posts = new ArrayList<>();
    posts.add(blogPost(1L, "welcome-post", Timestamp.valueOf("2026-03-15 12:30:00")));
    Map<Long, Blog> blogById = new HashMap<>();
    blogById.put(1L, blog("news"));

    String body = runDoGetWithBlogAndWiki(posts, blogById, new ArrayList<>(), new HashMap<>());

    assertTrue(body.contains("<loc>https://example.org/news/welcome-post</loc>"),
        "blog post URL must be /{blogUniqueId}/{postUniqueId}: " + body);
    assertTrue(body.contains("<lastmod>2026-03-15</lastmod>"));
  }

  @Test
  void doGetOmitsABlogPostWhoseBlogNoLongerExists() throws Exception {
    List<BlogPost> posts = new ArrayList<>();
    posts.add(blogPost(99L, "orphaned-post", Timestamp.valueOf("2026-03-15 12:30:00")));

    String body = runDoGetWithBlogAndWiki(posts, new HashMap<>(), new ArrayList<>(), new HashMap<>());

    assertFalse(body.contains("orphaned-post"), "a post whose Blog can't be resolved must not be linked: " + body);
  }

  @Test
  void doGetIncludesWikiPagesFromAnEnabledWiki() throws Exception {
    List<WikiPage> pages = new ArrayList<>();
    pages.add(wikiPage(1L, "getting-started", Timestamp.valueOf("2026-04-01 09:00:00")));
    Map<Long, Wiki> wikiById = new HashMap<>();
    wikiById.put(1L, wiki("docs", true));

    String body = runDoGetWithBlogAndWiki(new ArrayList<>(), new HashMap<>(), pages, wikiById);

    assertTrue(body.contains("<loc>https://example.org/docs/getting-started</loc>"),
        "wiki page URL must be /{wikiUniqueId}/{pageUniqueId}: " + body);
    assertTrue(body.contains("<lastmod>2026-04-01</lastmod>"));
  }

  @Test
  void doGetOmitsWikiPagesFromADisabledWiki() throws Exception {
    List<WikiPage> pages = new ArrayList<>();
    pages.add(wikiPage(1L, "internal-only", Timestamp.valueOf("2026-04-01 09:00:00")));
    Map<Long, Wiki> wikiById = new HashMap<>();
    wikiById.put(1L, wiki("archive", false));

    String body = runDoGetWithBlogAndWiki(new ArrayList<>(), new HashMap<>(), pages, wikiById);

    assertFalse(body.contains("internal-only"), "pages in a disabled wiki must not appear in the sitemap: " + body);
  private HttpServletResponse runDoGetForResponse(Map<String, String> properties, List<WebPage> webPageList,
      List<Item> itemList, HttpServletRequest request) throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(properties);
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(webPageList);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(itemList);

      new SitemapServlet().doGet(request, response);
    }
    return response;
  }

  @Test
  void doGetSetsETagAndLastModifiedForCachingClients() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), request);

    ArgumentCaptor<String> etagCaptor = ArgumentCaptor.forClass(String.class);
    verify(response).setHeader(eq("ETag"), etagCaptor.capture());
    assertTrue(etagCaptor.getValue().startsWith("\"") && etagCaptor.getValue().endsWith("\""),
        "ETag should be a quoted entity tag: " + etagCaptor.getValue());
    verify(response).setDateHeader("Last-Modified", Timestamp.valueOf("2026-03-15 12:30:00").getTime());
  }

  @Test
  void doGetReturns304WhenIfNoneMatchMatchesTheCurrentETag() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    // First request captures the real ETag computed for this exact content
    HttpServletRequest firstRequest = mock(HttpServletRequest.class);
    HttpServletResponse firstResponse = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), firstRequest);
    ArgumentCaptor<String> etagCaptor = ArgumentCaptor.forClass(String.class);
    verify(firstResponse).setHeader(eq("ETag"), etagCaptor.capture());

    // A second request presents that ETag back via If-None-Match
    HttpServletRequest secondRequest = mock(HttpServletRequest.class);
    when(secondRequest.getHeader("If-None-Match")).thenReturn(etagCaptor.getValue());
    HttpServletResponse secondResponse = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), secondRequest);

    verify(secondResponse).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
  }

  @Test
  void doGetReturns200WhenIfNoneMatchIsAStaleETag() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("If-None-Match")).thenReturn("\"stale-value-from-a-previous-version\"");
    HttpServletResponse response = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), request);

    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  @Test
  void doGetReturns304WhenIfModifiedSinceIsAtOrAfterTheMostRecentModification() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null)); // modified = 2026-03-15 12:30:00

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getDateHeader("If-Modified-Since")).thenReturn(Timestamp.valueOf("2026-03-15 12:30:00").getTime());
    HttpServletResponse response = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), request);

    verify(response).setStatus(HttpServletResponse.SC_NOT_MODIFIED);
  }

  @Test
  void doGetReturns200WhenIfModifiedSinceIsBeforeTheMostRecentModification() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null)); // modified = 2026-03-15 12:30:00

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getDateHeader("If-Modified-Since")).thenReturn(Timestamp.valueOf("2026-01-01 00:00:00").getTime());
    HttpServletResponse response = runDoGetForResponse(siteProperties(true, true), pages, new ArrayList<>(), request);

    verify(response).setStatus(HttpServletResponse.SC_OK);
  }

  @Test
  void doGetGzipsTheResponseWhenTheClientAcceptsGzipEncoding() throws Exception {
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));

    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getHeader("Accept-Encoding")).thenReturn("gzip, deflate, br");

    HttpServletResponse response = mock(HttpServletResponse.class);
    ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
    ServletOutputStream servletOutputStream = new ServletOutputStream() {
      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setWriteListener(WriteListener writeListener) {
      }

      @Override
      public void write(int b) {
        capturedOutput.write(b);
      }
    };
    when(response.getOutputStream()).thenReturn(servletOutputStream);

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(pages);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new SitemapServlet().doGet(request, response);
    }

    verify(response).setHeader("Content-Encoding", "gzip");

    ByteArrayOutputStream decompressed = new ByteArrayOutputStream();
    try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(capturedOutput.toByteArray()))) {
      gis.transferTo(decompressed);
    }
    assertTrue(decompressed.toString("UTF-8").contains("<loc>https://example.org/about</loc>"),
        "the decompressed body must still be the real sitemap content");
  }

  @Test
  void doGetReturns500AndDoesNotThrowWhenSitePropertiesFail() throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenThrow(new RuntimeException("db unavailable"));
      new SitemapServlet().doGet(request, response);
    }

    verify(response).setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
  }
}
