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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Tests the report-only policy header, the Google Analytics image hosts, and the connect-src each
 * feature a site switches on adds.
 *
 * @author elizabeth houser
 */
class CspPolicyCommandTest {

  private MockedStatic<LoadSitePropertyCommand> configured(String policy) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(CspPolicyCommand.REPORT_ONLY_PROPERTY)).thenReturn(policy);
    return m;
  }

  @Test
  void noPolicyMeansNoHeaderAtAll() {
    // How it ships. Nothing about the enforced policy changes, and the endpoint accepts nothing.
    try (MockedStatic<LoadSitePropertyCommand> m = configured("")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
      assertFalse(CspPolicyCommand.isReportingEnabled());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = configured(null)) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
      assertFalse(CspPolicyCommand.isReportingEnabled());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = configured("   ")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
    }
  }

  @Test
  void aPolicyGetsBothReportingDirectivesAdded() {
    // Without one of these the browser evaluates the policy and reports to nobody, which is
    // indistinguishable from a policy that passes -- the most misleading way this could fail.
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self'")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertTrue(policy.startsWith("default-src 'self'"));
      assertTrue(policy.contains("report-uri /csp-report"));
      assertTrue(policy.contains("report-to csp-endpoint"));
      assertTrue(CspPolicyCommand.isReportingEnabled());
    }
  }

  @Test
  void anAdministratorsOwnReportingDirectivesAreNotDuplicated() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self'; report-uri /elsewhere")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertEquals(1, policy.split("report-uri", -1).length - 1);
      assertTrue(policy.contains("/elsewhere"));
    }
  }

  @Test
  void theNoncePlaceholderIsFilledInPerRequest() {
    // An administrator cannot know the nonce when typing the policy, so a candidate carrying
    // script-src would be untestable without this
    try (MockedStatic<LoadSitePropertyCommand> m = configured("script-src 'self' 'nonce-{nonce}'")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertTrue(policy.contains("'nonce-abc123'"));
      assertFalse(policy.contains("{nonce}"));
    }
  }

  @Test
  void aTrailingSemicolonDoesNotProduceAnEmptyDirective() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self';  ")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertFalse(policy.contains(";;"));
      assertTrue(policy.startsWith("default-src 'self'; report-uri"));
    }
  }

  @Test
  void aPolicyOfOnlySemicolonsIsTreatedAsUnset() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured(";;;")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
    }
  }

  @Test
  void theReportingEndpointsHeaderNamesTheSamePathTheServletServes() {
    assertEquals("csp-endpoint=\"/csp-report\"", CspPolicyCommand.reportingEndpointsHeader());
    assertEquals("/csp-report", CspPolicyCommand.REPORT_PATH);
  }

  private MockedStatic<LoadSitePropertyCommand> analytics(String service, String key) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName("analytics.service")).thenReturn(service);
    m.when(() -> LoadSitePropertyCommand.loadByName("analytics.google.key")).thenReturn(key);
    return m;
  }

  @Test
  void googleAnalyticsAddsItsImageHostsWhenTheSiteLoadsIt() {
    // GA's tag diagnostics reported img-src blocking www.google.* and www.googletagmanager.com
    try (MockedStatic<LoadSitePropertyCommand> m = analytics("google", "G-4PCLCXQE3N")) {
      assertEquals(" https://*.google.com https://*.googletagmanager.com", CspPolicyCommand.googleAnalyticsImageSources());
    }
  }

  @Test
  void aSiteWithoutGoogleAnalyticsKeepsTheNarrowerImgSrc() {
    // img-src closes the CSS exfiltration channel (#1430); a host nothing needs is not added
    try (MockedStatic<LoadSitePropertyCommand> m = analytics(null, null)) {
      assertEquals("", CspPolicyCommand.googleAnalyticsImageSources());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = analytics("google", "  ")) {
      assertEquals("", CspPolicyCommand.googleAnalyticsImageSources(), "selected but no key: main.jsp loads nothing");
    }
    try (MockedStatic<LoadSitePropertyCommand> m = analytics("matomo", "G-4PCLCXQE3N")) {
      assertEquals("", CspPolicyCommand.googleAnalyticsImageSources());
    }
  }

  // --- connect-src ---------------------------------------------------------------------------

  /** Site properties as a map; anything absent reads as unset, or as the caller's default. */
  private MockedStatic<LoadSitePropertyCommand> properties(Map<String, String> values) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenAnswer(inv -> values.get(inv.getArgument(0)));
    m.when(() -> LoadSitePropertyCommand.loadByName(anyString(), anyString()))
        .thenAnswer(inv -> values.getOrDefault(inv.getArgument(0), inv.getArgument(1)));
    return m;
  }

  private static final String NOTHING_SWITCHED_ON = "'self' blob: https://vimeo.com/api/oembed.json";

  @Test
  void withNothingSwitchedOnScriptCanReachThisSiteAndVimeosOembedEndpoint() {
    // video.jsp calls the endpoint from the browser whenever a page has a Vimeo video, which is up to
    // content rather than configuration, so it is always present -- by path, not the whole host
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of())) {
      assertEquals(NOTHING_SWITCHED_ON, CspPolicyCommand.connectSourceList());
    }
  }

  @Test
  void googleAnalyticsAddsTheHostsGtagSendsTo() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(
        Map.of("analytics.service", "google", "analytics.google.key", "G-4PCLCXQE3N"))) {
      String sources = CspPolicyCommand.connectSourceList();
      assertTrue(sources.startsWith(NOTHING_SWITCHED_ON + " "), sources);
      for (String host : List.of("https://www.googletagmanager.com", "https://*.google-analytics.com",
          "https://*.google.com", "https://*.g.doubleclick.net")) {
        assertTrue(sources.contains(" " + host), host + " missing: " + sources);
      }
      // Signals' audience pings to Google's country domains stay refused, as they are in img-src
      for (String source : sources.split(" ")) {
        assertTrue(!source.contains("google.") || source.endsWith("google.com"), "a country domain was added: " + source);
      }
      assertFalse(sources.contains("googlesyndication"), sources);
    }
    // The same condition as the image hosts: selected but no key loads nothing
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of("analytics.service", "google"))) {
      assertEquals(NOTHING_SWITCHED_ON, CspPolicyCommand.connectSourceList());
    }
  }

  @Test
  void recaptchaAddsItsPathAndTurnstileAddsNothing() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(
        Map.of("captcha.service", "google", "captcha.google.sitekey", "site-key"))) {
      assertEquals(NOTHING_SWITCHED_ON + " https://www.google.com/recaptcha/", CspPolicyCommand.connectSourceList());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = properties(
        Map.of("captcha.service", "turnstile", "captcha.google.sitekey", "site-key"))) {
      assertEquals(NOTHING_SWITCHED_ON, CspPolicyCommand.connectSourceList(), "Turnstile is selected");
    }
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of("captcha.service", "google"))) {
      assertEquals(NOTHING_SWITCHED_ON, CspPolicyCommand.connectSourceList(), "no site key: no widget renders");
    }
  }

  /** The two rules are written separately; a form that renders reCAPTCHA needs both its frame and its calls. */
  @Test
  void recaptchasCallsAreAllowedExactlyWhenItsFrameIs() {
    List<Map<String, String>> configurations = List.of(
        Map.of(),
        Map.of("captcha.service", "google", "captcha.google.sitekey", "site-key"),
        Map.of("captcha.service", "Google", "captcha.google.sitekey", "site-key"),
        Map.of("captcha.service", "google"),
        Map.of("captcha.service", "turnstile", "captcha.google.sitekey", "site-key"),
        Map.of("captcha.google.sitekey", "site-key"));
    for (Map<String, String> configuration : configurations) {
      try (MockedStatic<LoadSitePropertyCommand> m = properties(configuration)) {
        assertEquals(AllowedIframeHostCommand.allowedHosts().contains("www.google.com"),
            CspPolicyCommand.connectSourceList().contains(CspPolicyCommand.RECAPTCHA_CONNECT_SOURCE),
            "frame-src and connect-src disagree for " + configuration);
      }
    }
  }

  @Test
  void stripeAddsItsApiHost() {
    // PaymentFormWidget matches the processor name ignoring case, so this does too
    for (String name : List.of("Stripe", "stripe")) {
      try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of("ecommerce.paymentProcessor", name))) {
        assertEquals(NOTHING_SWITCHED_ON + " https://api.stripe.com", CspPolicyCommand.connectSourceList());
      }
    }
  }

  @Test
  void squareAddsItsSandboxHostsUntilProductionIsSwitchedOn() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of("ecommerce.paymentProcessor", "Square"))) {
      assertEquals(NOTHING_SWITCHED_ON + " https://sandbox.web.squarecdn.com https://pci-connect.squareupsandbox.com"
          + " https://o160250.ingest.sentry.io", CspPolicyCommand.connectSourceList());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = properties(
        Map.of("ecommerce.paymentProcessor", "Square", "ecommerce.production", "true"))) {
      assertEquals(NOTHING_SWITCHED_ON + " https://web.squarecdn.com https://pci-connect.squareup.com"
          + " https://o160250.ingest.sentry.io", CspPolicyCommand.connectSourceList());
    }
  }

  @Test
  void anUnrecognizedPaymentProcessorAddsNothing() {
    // PaymentFormWidget falls back to its own form, which loads no vendor SDK
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of("ecommerce.paymentProcessor", "PayPal"))) {
      assertEquals(NOTHING_SWITCHED_ON, CspPolicyCommand.connectSourceList());
    }
  }

  @Test
  void everythingSwitchedOnListsEachSourceOnce() {
    // reCAPTCHA's path sits inside *.google.com when Google Analytics is on too; listing both is harmless,
    // but no source is listed twice
    try (MockedStatic<LoadSitePropertyCommand> m = properties(Map.of(
        "analytics.service", "google", "analytics.google.key", "G-4PCLCXQE3N",
        "captcha.service", "google", "captcha.google.sitekey", "site-key",
        "ecommerce.paymentProcessor", "Square"))) {
      List<String> sources = List.of(CspPolicyCommand.connectSourceList().split(" "));
      assertEquals(sources.size(), new HashSet<>(sources).size(), "a source is repeated: " + sources);
    }
  }
}
