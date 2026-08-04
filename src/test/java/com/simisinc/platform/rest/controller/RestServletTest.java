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

package com.simisinc.platform.rest.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.rest.services.cms.WebPageResponse;

/**
 * Regression test for the shared {@link RestServlet#JSONB} instance: before {@link
 * TimestampJsonbAdapter} was registered, serializing any response DTO carrying a raw
 * java.sql.Timestamp field (e.g. {@link WebPageResponse}, populated by real rows from
 * PagesListService/PageService) threw InaccessibleObjectException at request time, turning a
 * successful lookup into an HTTP 500 -- see RestServlet.service()'s catch-all "Could not render".
 */
class RestServletTest {

  @Test
  void serializesWebPageResponseWithTimestampFields() {
    WebPage webPage = new WebPage();
    webPage.setLink("/about");
    webPage.setTitle("About");
    webPage.setPublishAt(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
    webPage.setExpiresAt(null);
    webPage.setModified(Timestamp.from(Instant.parse("2026-08-04T12:34:56.789Z")));

    String json = assertDoesNotThrow(() -> RestServlet.JSONB.toJson(new WebPageResponse(webPage)));

    assertTrue(json.contains("\"publishAt\":\"2026-01-01T00:00:00Z\""));
    assertTrue(json.contains("\"modified\":\"2026-08-04T12:34:56.789Z\""));
  }

  @Test
  void stillSerializesPlainMapValues() {
    Map<String, Object> meta = new HashMap<>();
    meta.put("total", 3);
    assertDoesNotThrow(() -> RestServlet.JSONB.toJson(meta));
  }
}
