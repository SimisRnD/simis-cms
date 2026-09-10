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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.cms.CspViolationReportCommand.Violation;

/**
 * Tests reading CSP violation reports in both formats browsers send.
 *
 * @author elizabeth houser
 */
class CspViolationReportCommandTest {

  private static ObjectMapper mapper = new ObjectMapper();

  private List<Violation> parse(String json) throws Exception {
    return CspViolationReportCommand.parse(mapper.readTree(json));
  }

  @Test
  void aLegacyReportUriPayloadIsRead() throws Exception {
    // What report-uri posts: one object, hyphenated field names
    List<Violation> violations = parse("{\"csp-report\":{"
        + "\"document-uri\":\"https://example.org/checkout\","
        + "\"violated-directive\":\"connect-src\","
        + "\"effective-directive\":\"connect-src\","
        + "\"blocked-uri\":\"https://api.stripe.com/v1/tokens\"}}");
    assertEquals(1, violations.size());
    assertEquals("connect-src", violations.get(0).getEffectiveDirective());
    assertEquals("api.stripe.com", violations.get(0).getBlockedHost());
    assertEquals("/checkout", violations.get(0).getDocumentPath());
  }

  @Test
  void aModernReportingApiPayloadIsRead() throws Exception {
    // What report-to posts: an array of envelopes, camel-cased field names
    List<Violation> violations = parse("[{\"type\":\"csp-violation\",\"body\":{"
        + "\"documentURL\":\"https://example.org/subscribe\","
        + "\"effectiveDirective\":\"connect-src\","
        + "\"blockedURL\":\"https://challenges.cloudflare.com/turnstile/v0/api.js\"}}]");
    assertEquals(1, violations.size());
    assertEquals("connect-src", violations.get(0).getEffectiveDirective());
    assertEquals("challenges.cloudflare.com", violations.get(0).getBlockedHost());
    assertEquals("/subscribe", violations.get(0).getDocumentPath());
  }

  @Test
  void nonViolationEnvelopesAreIgnored() throws Exception {
    // The Reporting API multiplexes: deprecation and intervention reports arrive the same way
    List<Violation> violations = parse("[{\"type\":\"deprecation\",\"body\":{\"id\":\"x\"}},"
        + "{\"type\":\"csp-violation\",\"body\":{\"effectiveDirective\":\"img-src\","
        + "\"blockedURL\":\"https://cdn.example.com/a.png\"}}]");
    assertEquals(1, violations.size());
    assertEquals("img-src", violations.get(0).getEffectiveDirective());
  }

  @Test
  void aDirectiveArrivingWithItsSourceListIsReducedToTheDirective() throws Exception {
    List<Violation> violations = parse("{\"csp-report\":{\"violated-directive\":\"script-src-elem 'self'\","
        + "\"blocked-uri\":\"https://cdn.example.com/x.js\"}}");
    assertEquals("script-src-elem", violations.get(0).getEffectiveDirective());
  }

  @Test
  void theBlockedUrlIsReducedToItsHost() {
    // A blocked url can carry a path and query string, which is one of the few ways a third party's
    // URL parameters could reach this database. The host is also all a source list needs.
    assertEquals("api.stripe.com",
        CspViolationReportCommand.normalizeBlockedUri("https://api.stripe.com/v1/tokens?key=sk_live_secret"));
    assertEquals("api.stripe.com", CspViolationReportCommand.normalizeBlockedUri("HTTPS://API.STRIPE.COM/x"));
  }

  @Test
  void keywordViolationsAreKeptQuotedAsAPolicyWouldWriteThem() {
    // Inline and eval violations are reported as a bare word, not a url. "An inline style would have
    // been refused" is a real finding, it just is not a host.
    assertEquals("'inline'", CspViolationReportCommand.normalizeBlockedUri("inline"));
    assertEquals("'eval'", CspViolationReportCommand.normalizeBlockedUri("eval"));
    assertEquals("'self'", CspViolationReportCommand.normalizeBlockedUri("self"));
    assertEquals("'data'", CspViolationReportCommand.normalizeBlockedUri("data:"));
  }

  @Test
  void anUnusableBlockedValueIsDropped() {
    assertNull(CspViolationReportCommand.normalizeBlockedUri(null));
    assertNull(CspViolationReportCommand.normalizeBlockedUri("   "));
    assertNull(CspViolationReportCommand.normalizeBlockedUri("nonsense"));
    assertNull(CspViolationReportCommand.normalizeBlockedUri("https://"));
  }

  @Test
  void theDocumentUrlIsReducedToItsPathWithNoQueryString() {
    // The page someone was on can carry anything in its query string
    assertEquals("/search",
        CspViolationReportCommand.normalizeDocumentUri("https://example.org/search?q=personal+information"));
    assertEquals("/a/b", CspViolationReportCommand.normalizeDocumentUri("/a/b?x=1#frag"));
    assertNull(CspViolationReportCommand.normalizeDocumentUri(null));
  }

  @Test
  void aReportWithNoDirectiveIsDropped() throws Exception {
    assertTrue(parse("{\"csp-report\":{\"blocked-uri\":\"https://a.example.com/x\"}}").isEmpty());
  }

  @Test
  void aBodyThatIsNotAViolationReportYieldsNothing() throws Exception {
    // The endpoint is open, so it is posted all sorts of things
    assertTrue(parse("{}").isEmpty());
    assertTrue(parse("[]").isEmpty());
    assertTrue(parse("{\"hello\":\"world\"}").isEmpty());
    assertTrue(CspViolationReportCommand.parse(null).isEmpty());
  }
}
