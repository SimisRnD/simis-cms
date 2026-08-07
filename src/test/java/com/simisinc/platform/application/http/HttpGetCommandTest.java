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

package com.simisinc.platform.application.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link HttpGetCommand#execute} logged every request URL verbatim at DEBUG, so any caller that
 * embeds a credential as a query parameter (rather than a header) -- an Instagram
 * {@code access_token}, a ZeroBounce {@code api_key}, a Moodle {@code wstoken}, etc. -- leaked it
 * to the log. This class has no way to know which parameter name a given caller considers secret,
 * so {@link HttpGetCommand#redactSecretQueryParams} redacts by a broad name-based heuristic rather
 * than an enumerated allowlist.
 */
class HttpGetCommandTest {

  @Test
  void redactsATokenQueryParam() {
    String url = "https://graph.instagram.com/me?fields=id&access_token=super-secret-value";
    String redacted = HttpGetCommand.redactSecretQueryParams(url);
    assertEquals("https://graph.instagram.com/me?fields=id&access_token=REDACTED", redacted);
  }

  @Test
  void redactsAnApiKeyQueryParam() {
    String url = "https://api.zerobounce.net/v2/validate?api_key=abc123&email=test@example.com";
    String redacted = HttpGetCommand.redactSecretQueryParams(url);
    assertEquals("https://api.zerobounce.net/v2/validate?api_key=REDACTED&email=test@example.com", redacted);
  }

  @Test
  void redactsAMoodleWstokenQueryParam() {
    String url = "https://lms.example.com/webservice/rest/server.php?wstoken=abc123&moodlewsrestformat=json";
    String redacted = HttpGetCommand.redactSecretQueryParams(url);
    assertEquals("https://lms.example.com/webservice/rest/server.php?wstoken=REDACTED&moodlewsrestformat=json",
        redacted);
  }

  @Test
  void leavesANonSecretQueryStringUnchanged() {
    String url = "https://example.com/search?query=widgets&page=2";
    assertEquals(url, HttpGetCommand.redactSecretQueryParams(url));
  }

  @Test
  void leavesAUrlWithNoQueryStringUnchanged() {
    String url = "https://example.com/status";
    assertEquals(url, HttpGetCommand.redactSecretQueryParams(url));
  }
}
