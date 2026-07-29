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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;

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
