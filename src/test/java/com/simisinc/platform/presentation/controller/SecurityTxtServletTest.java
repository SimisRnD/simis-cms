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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author SimIS Inc.
 */
class SecurityTxtServletTest {

  /** Runs the servlet against the given securitytxt.* and site.* property maps. */
  private String render(Map<String, String> securityProps, Map<String, String> siteProps,
      HttpServletResponse response) throws Exception {
    StringWriter out = new StringWriter();
    org.mockito.Mockito.when(response.getWriter()).thenReturn(new PrintWriter(out, true));
    try (MockedStatic<LoadSitePropertyCommand> props = mockStatic(LoadSitePropertyCommand.class)) {
      props.when(() -> LoadSitePropertyCommand.loadAsMap("securitytxt")).thenReturn(securityProps);
      props.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(siteProps);
      new SecurityTxtServlet().doGet(mock(HttpServletRequest.class), response);
    }
    return out.toString();
  }

  private Map<String, String> configured() {
    Map<String, String> p = new HashMap<>();
    p.put("securitytxt.contact", "security@example.com");
    return p;
  }

  // ---- the mandatory field ----

  @Test
  void aBlankContactIs404BecauseRfc9116MakesContactMandatory() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    String body = render(new HashMap<>(), new HashMap<>(), response);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.contains("securitytxt.contact"));
  }

  @Test
  void the404IsNotCachedSoFillingInAContactTakesEffectImmediately() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    render(new HashMap<>(), new HashMap<>(), response);
    verify(response, never()).setHeader(eq("Cache-Control"), anyString());
  }

  @Test
  void anExplicitFalseDisablesItEvenWithAContactSet() throws Exception {
    Map<String, String> p = configured();
    p.put("securitytxt.enabled", "false");
    HttpServletResponse response = mock(HttpServletResponse.class);
    String body = render(p, new HashMap<>(), response);
    verify(response).setStatus(HttpServletResponse.SC_NOT_FOUND);
    assertTrue(body.contains("securitytxt.enabled"));
  }

  @Test
  void aConfiguredContactIsServedAndCached() throws Exception {
    HttpServletResponse response = mock(HttpServletResponse.class);
    String body = render(configured(), new HashMap<>(), response);
    verify(response).setStatus(HttpServletResponse.SC_OK);
    verify(response).setHeader("Cache-Control", "public, max-age=86400");
    assertTrue(body.contains("Contact: mailto:security@example.com"), body);
  }

  // ---- Contact normalization: RFC 9116 requires a URI, admins type bare addresses ----

  @Test
  void aBareEmailBecomesAMailtoUri() {
    assertEquals(List.of("mailto:security@example.com"),
        SecurityTxtServlet.contactsFrom("security@example.com"));
  }

  @Test
  void aBareHostBecomesAnHttpsUri() {
    assertEquals(List.of("https://example.com/report"),
        SecurityTxtServlet.contactsFrom("example.com/report"));
  }

  @Test
  void anExplicitUriIsLeftAlone() {
    assertEquals(List.of("https://example.com/vdp"),
        SecurityTxtServlet.contactsFrom("https://example.com/vdp"));
    assertEquals(List.of("tel:+1-201-555-0123"),
        SecurityTxtServlet.contactsFrom("tel:+1-201-555-0123"));
  }

  @Test
  void severalContactsAreKeptInTheGivenOrderOfPreference() {
    assertEquals(List.of("mailto:a@example.com", "https://example.com/vdp"),
        SecurityTxtServlet.contactsFrom("a@example.com, https://example.com/vdp"));
    assertEquals(List.of("mailto:a@example.com", "mailto:b@example.com"),
        SecurityTxtServlet.contactsFrom("a@example.com\nb@example.com"));
  }

  @Test
  void blankAndWhitespaceOnlyEntriesAreDropped() {
    assertTrue(SecurityTxtServlet.contactsFrom("   ").isEmpty());
    assertTrue(SecurityTxtServlet.contactsFrom(null).isEmpty());
    assertEquals(List.of("mailto:a@example.com"),
        SecurityTxtServlet.contactsFrom("a@example.com, ,\n"));
  }

  // ---- Expires: mandatory, and generated so it cannot silently go stale ----

  @Test
  void expiresIsPresentInRfc3339AndAboutAYearOut() throws Exception {
    String body = render(configured(), new HashMap<>(), mock(HttpServletResponse.class));
    String expires = body.lines().filter(l -> l.startsWith("Expires: ")).findFirst().orElse("");
    assertFalse(expires.isEmpty(), "no Expires field in:\n" + body);
    Instant parsed = Instant.parse(expires.substring("Expires: ".length()));
    long days = ChronoUnit.DAYS.between(Instant.now(), parsed);
    assertTrue(days > SecurityTxtServlet.EXPIRES_DAYS - 2 && days <= SecurityTxtServlet.EXPIRES_DAYS,
        "Expires was " + days + " days out");
  }

  // ---- optional fields ----

  @Test
  void optionalFieldsAreOmittedWhenBlankAndEmittedWhenSet() throws Exception {
    String bare = render(configured(), new HashMap<>(), mock(HttpServletResponse.class));
    assertFalse(bare.contains("Policy:"), bare);
    assertFalse(bare.contains("Acknowledgments:"), bare);
    assertFalse(bare.contains("Encryption:"), bare);
    assertFalse(bare.contains("Preferred-Languages:"), bare);

    Map<String, String> p = configured();
    p.put("securitytxt.policy", "https://example.com/vdp");
    p.put("securitytxt.acknowledgments", "https://example.com/thanks");
    p.put("securitytxt.encryption", "https://example.com/pgp.txt");
    p.put("securitytxt.preferredLanguages", "en, es");
    String full = render(p, new HashMap<>(), mock(HttpServletResponse.class));
    assertTrue(full.contains("Policy: https://example.com/vdp"), full);
    assertTrue(full.contains("Acknowledgments: https://example.com/thanks"), full);
    assertTrue(full.contains("Encryption: https://example.com/pgp.txt"), full);
    assertTrue(full.contains("Preferred-Languages: en, es"), full);
  }

  // ---- Canonical comes from site.url, never the request's Host header ----

  @Test
  void canonicalIsDerivedFromSiteUrlWithoutADoubledSlash() throws Exception {
    Map<String, String> site = new HashMap<>();
    site.put("site.url", "https://www.example.com/");
    String body = render(configured(), site, mock(HttpServletResponse.class));
    assertTrue(body.contains("Canonical: https://www.example.com/.well-known/security.txt"), body);
  }

  @Test
  void canonicalIsOmittedWhenSiteUrlIsUnset() throws Exception {
    String body = render(configured(), new HashMap<>(), mock(HttpServletResponse.class));
    assertFalse(body.contains("Canonical:"), body);
  }

  // ---- the enabled toggle default ----

  @Test
  void enabledDefaultsToTrueSoOnlyAnExplicitFalseTurnsItOff() {
    assertTrue(SecurityTxtServlet.isEnabled(new HashMap<>()));
    assertTrue(SecurityTxtServlet.isEnabled(Map.of("securitytxt.enabled", "true")));
    assertFalse(SecurityTxtServlet.isEnabled(Map.of("securitytxt.enabled", "false")));
  }
}
