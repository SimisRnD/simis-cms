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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A WebPage is part of a WebSite, not part of a company. schema.org's isPartOf expects a
 * CreativeWork, so targeting the Organization is invalid and the schema.org validator reports it --
 * which is how this was found on the live site. Pinned here because the failure is invisible in a
 * browser and survives review: the page renders identically either way.
 */
class StructuredDataCommandWebSiteTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SITE_URL = "https://www.example.com";

  private static JsonNode graphFor(Map<String, String> siteProperties) throws Exception {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.setPageUrl(SITE_URL + "/");
    pageRenderInfo.setTitle("Example page");
    String json = StructuredDataCommand.generateJsonLdData(
        pageRenderInfo, SITE_URL, "/", siteProperties, null, null, null, null);
    assertNotNull(json, "expected JSON-LD to be generated");
    return MAPPER.readTree(json).get("@graph");
  }

  private static JsonNode nodeOfType(JsonNode graph, String type) {
    for (JsonNode node : graph) {
      if (node.has("@type") && type.equals(node.get("@type").asText())) {
        return node;
      }
    }
    return null;
  }

  private static Map<String, String> siteProperties() {
    Map<String, String> map = new HashMap<>();
    map.put("site.name", "Example, Inc.");
    return map;
  }

  @Test
  void webPageIsPartOfTheWebSiteRatherThanTheOrganization() throws Exception {
    JsonNode graph = graphFor(siteProperties());

    JsonNode webPage = nodeOfType(graph, "WebPage");
    assertNotNull(webPage, "expected a WebPage node");
    assertEquals(SITE_URL + "#website", webPage.get("isPartOf").get("@id").asText(),
        "isPartOf must target the WebSite; Organization is not a valid CreativeWork target");
  }

  @Test
  void theWebSiteExistsAndIsPublishedByTheOrganization() throws Exception {
    JsonNode graph = graphFor(siteProperties());

    JsonNode webSite = nodeOfType(graph, "WebSite");
    assertNotNull(webSite, "expected a WebSite node so isPartOf has something to point at");
    assertEquals(SITE_URL + "#website", webSite.get("@id").asText());
    assertEquals("Example, Inc.", webSite.get("name").asText());
    assertEquals(SITE_URL + "#organization", webSite.get("publisher").get("@id").asText());

    // the referenced Organization must actually be in the graph, not just named
    assertNotNull(nodeOfType(graph, "Organization"), "publisher must resolve within the graph");
  }

  @Test
  void everyIdReferenceResolvesWithinTheGraph() throws Exception {
    JsonNode graph = graphFor(siteProperties());

    java.util.Set<String> declared = new java.util.HashSet<>();
    for (JsonNode node : graph) {
      if (node.has("@id")) {
        declared.add(node.get("@id").asText());
      }
    }
    // isPartOf and publisher are @id references; a dangling one is a different defect, not a fix
    for (JsonNode node : graph) {
      for (String property : new String[] { "isPartOf", "publisher" }) {
        JsonNode ref = node.get(property);
        if (ref != null && ref.has("@id")) {
          assertTrue(declared.contains(ref.get("@id").asText()),
              property + " points at " + ref.get("@id").asText() + ", which is not a node in the graph");
        }
      }
    }
  }

  @Test
  void withNoSiteNameThereIsNoWebSiteAndNoDanglingIsPartOf() throws Exception {
    // site.name is what gates the Organization, and now the WebSite too. Without it neither node is
    // emitted, so the WebPage must not claim to be part of something absent.
    JsonNode graph = graphFor(new HashMap<>());

    assertNull_(nodeOfType(graph, "WebSite"));
    assertNull_(nodeOfType(graph, "Organization"));
    JsonNode webPage = nodeOfType(graph, "WebPage");
    assertNotNull(webPage, "the WebPage node is emitted regardless");
    assertFalse(webPage.has("isPartOf"), "isPartOf must be omitted rather than left dangling");
  }

  private static void assertNull_(JsonNode node) {
    assertTrue(node == null, "expected no such node");
  }
}
