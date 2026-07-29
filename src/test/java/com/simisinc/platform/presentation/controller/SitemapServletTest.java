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
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
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

  private static List<WebPage> manyWebPages(int count) {
    List<WebPage> pages = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      pages.add(webPage("/page-" + i, null, null));
    }
    return pages;
  }

  private String runDoGetWithPage(String page, List<WebPage> webPageList) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("page")).thenReturn(page);
    HttpServletResponse response = mock(HttpServletResponse.class);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(webPageList);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new SitemapServlet().doGet(request, response);
    }

    return body.toString();
  }

  private HttpServletResponse runDoGetForResponseWithPage(String page, List<WebPage> webPageList) throws Exception {
    HttpServletRequest request = mock(HttpServletRequest.class);
    when(request.getParameter("page")).thenReturn(page);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<ItemRepository> itemRepository = mockStatic(ItemRepository.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProperties(true, true));
      webPageRepository.when(() -> WebPageRepository.findAll(any(), any())).thenReturn(webPageList);
      itemRepository.when(() -> ItemRepository.findAll(any(), any())).thenReturn(new ArrayList<>());

      new SitemapServlet().doGet(request, response);
    }
    return response;
  }

  @Test
  void doGetDoesNotPaginateWhenAtExactlyTheLimit() throws Exception {
    // MAX_URLS_PER_SITEMAP - 1 web pages + the homepage = exactly the limit
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP - 1);
    String body = runDoGet(siteProperties(true, true), pages, new ArrayList<>());

    assertTrue(body.contains("<urlset"), "at exactly the limit, a single file is still enough: " + body.substring(0, 200));
    assertFalse(body.contains("<sitemapindex"));
  }

  @Test
  void doGetWritesASitemapIndexWhenTotalUrlsExceedsTheLimit() throws Exception {
    // MAX_URLS_PER_SITEMAP web pages + the homepage = one over the limit, needs exactly 2 pages
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP);
    String body = runDoGet(siteProperties(true, true), pages, new ArrayList<>());

    assertTrue(body.contains("<sitemapindex"), "must switch to a sitemap index once over the limit: " + body.substring(0, 200));
    assertFalse(body.contains("<urlset"), "must not also emit a flat urlset: " + body.substring(0, 200));
    assertTrue(body.contains("<loc>https://example.org/sitemap.xml?page=1</loc>"));
    assertTrue(body.contains("<loc>https://example.org/sitemap.xml?page=2</loc>"));
    assertFalse(body.contains("page=3"), "one URL over the limit needs exactly 2 pages, not 3: " + body);
  }

  @Test
  void doGetServesEachChunkAsAUrlsetWithTheRequestedPage() throws Exception {
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP);
    int last = SitemapServlet.MAX_URLS_PER_SITEMAP - 1;

    String firstPage = runDoGetWithPage("1", pages);
    assertTrue(firstPage.contains("<urlset"));
    assertTrue(firstPage.contains("<loc>https://example.org/</loc>"), "the homepage is entry #1, so it belongs on page 1");
    assertTrue(firstPage.contains("<loc>https://example.org/page-" + (last - 1) + "</loc>"));
    assertFalse(firstPage.contains("<loc>https://example.org/page-" + last + "</loc>"),
        "the last web page must roll over onto page 2, not stay on page 1");

    String secondPage = runDoGetWithPage("2", pages);
    assertTrue(secondPage.contains("<urlset"));
    assertTrue(secondPage.contains("<loc>https://example.org/page-" + last + "</loc>"));
    assertFalse(secondPage.contains("<loc>https://example.org/</loc>"), "the homepage belongs on page 1 only");
  }

  @Test
  void doGetReturns404ForAnOutOfRangePageParameter() throws Exception {
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP); // exactly 2 pages
    HttpServletResponse response = runDoGetForResponseWithPage("3", pages);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns404ForPageZero() throws Exception {
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP);
    HttpServletResponse response = runDoGetForResponseWithPage("0", pages);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns404ForANonNumericPageParameter() throws Exception {
    List<WebPage> pages = manyWebPages(SitemapServlet.MAX_URLS_PER_SITEMAP);
    HttpServletResponse response = runDoGetForResponseWithPage("not-a-number", pages);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
  }

  @Test
  void doGetReturns404ForAPageParameterWhenNoPaginationIsNeeded() throws Exception {
    // Well under the limit -- ?page=1 doesn't exist as its own resource when unpaginated
    List<WebPage> pages = new ArrayList<>();
    pages.add(webPage("/about", null, null));
    HttpServletResponse response = runDoGetForResponseWithPage("1", pages);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
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
