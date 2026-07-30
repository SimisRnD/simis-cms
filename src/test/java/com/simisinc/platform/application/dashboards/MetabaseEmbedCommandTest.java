/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.application.dashboards;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Verifies the JWT this command builds is one Metabase would actually accept: correct URL shape,
 * a valid HS256 signature independently re-derived with the same secret (not just "some token was
 * returned"), and the expected header/claims.
 *
 * @author elizabeth houser
 */
class MetabaseEmbedCommandTest {

  private static final String SECRET = "test-embedding-secret-key";
  private static final String SERVER_URL = "https://metabase.example.com";

  private static MockedStatic<LoadSitePropertyCommand> mockEnabledAndConfigured() {
    MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class);
    mock.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("bi.metabase.enabled")).thenReturn(true);
    mock.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.url")).thenReturn(SERVER_URL);
    mock.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.secret")).thenReturn(SECRET);
    return mock;
  }

  @Test
  void returnsNullWhenDashboardIdIsBlank() {
    try (MockedStatic<LoadSitePropertyCommand> mock = mockEnabledAndConfigured()) {
      assertNull(MetabaseEmbedCommand.generateDashboardIframeUrl("", null));
      assertNull(MetabaseEmbedCommand.generateDashboardIframeUrl(null, null));
    }
  }

  @Test
  void returnsNullWhenMetabaseIsNotEnabled() {
    try (MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class)) {
      mock.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("bi.metabase.enabled")).thenReturn(false);
      assertNull(MetabaseEmbedCommand.generateDashboardIframeUrl("12", null));
    }
  }

  @Test
  void returnsNullWhenUrlOrSecretIsNotConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> mock = mockStatic(LoadSitePropertyCommand.class)) {
      mock.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("bi.metabase.enabled")).thenReturn(true);
      mock.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.url")).thenReturn("");
      mock.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.secret")).thenReturn(SECRET);
      assertNull(MetabaseEmbedCommand.generateDashboardIframeUrl("12", null));
    }
  }

  @Test
  void buildsAValidSignedIframeUrl() throws Exception {
    try (MockedStatic<LoadSitePropertyCommand> mock = mockEnabledAndConfigured()) {
      String url = MetabaseEmbedCommand.generateDashboardIframeUrl("12", null);

      assertTrue(url.startsWith(SERVER_URL + "/embed/dashboard/"), "unexpected URL shape: " + url);
      String token = url.substring((SERVER_URL + "/embed/dashboard/").length());

      String[] segments = token.split("\\.");
      assertEquals(3, segments.length, "a JWT has exactly 3 dot-separated segments");

      String header = decode(segments[0]);
      assertEquals("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", header);

      String payload = decode(segments[1]);
      assertTrue(payload.contains("\"dashboard\":\"12\""), "payload missing dashboard id: " + payload);
      assertTrue(payload.contains("\"exp\":"), "payload missing exp claim: " + payload);

      // Independently re-derive the signature with the same secret and confirm it matches -
      // proves this is a token Metabase's own HMAC verification would actually accept.
      String signingInput = segments[0] + "." + segments[1];
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] expectedSignature = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
      byte[] actualSignature = Base64.getUrlDecoder().decode(segments[2]);
      assertArrayEquals(expectedSignature, actualSignature);
    }
  }

  @Test
  void appendsHashParametersWhenProvided() {
    try (MockedStatic<LoadSitePropertyCommand> mock = mockEnabledAndConfigured()) {
      String url = MetabaseEmbedCommand.generateDashboardIframeUrl("12", "bordered=true&titled=false");
      assertTrue(url.endsWith("#bordered=true&titled=false"), "unexpected URL: " + url);
    }
  }

  private static String decode(String base64UrlSegment) {
    return new String(Base64.getUrlDecoder().decode(base64UrlSegment), StandardCharsets.UTF_8);
  }
}
