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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Tests the iframe host allowlist that backs both the content sanitizer and the CSP frame-src
 * directive.
 *
 * @author elizabeth houser
 */
class AllowedIframeHostCommandTest {

  /** A site with the given value in security.iframe.allowedHosts and Metabase switched off. */
  private MockedStatic<LoadSitePropertyCommand> siteWith(String allowedHosts) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(AllowedIframeHostCommand.SITE_PROPERTY))
        .thenReturn(allowedHosts);
    m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.enabled")).thenReturn("false");
    return m;
  }

  /** A site with a captcha provider selected, and the allowlist property empty. */
  private MockedStatic<LoadSitePropertyCommand> siteWithCaptcha(String service, String googleSiteKey) {
    MockedStatic<LoadSitePropertyCommand> m = siteWith("");
    m.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(service);
    m.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(googleSiteKey);
    return m;
  }

  @Test
  void googlesHostIsAllowedWhenRecaptchaIsConfigured() {
    // Without this the CSP refuses the reCAPTCHA widget, no token is produced, and the form's
    // submit button silently does nothing -- the failure this branch exists to prevent.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWithCaptcha("google", "6LcSomeSiteKey")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://www.google.com/recaptcha/api2/anchor"));
    }
  }

  @Test
  void cloudflaresHostIsAllowedWhenTurnstileIsSelected() {
    // Turnstile wins on the service name alone, matching CaptchaCommand: a Turnstile-only install
    // has no reason to have a Google site key set.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWithCaptcha("turnstile", null)) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://challenges.cloudflare.com/turnstile/v0/api.js"));
      assertFalse(AllowedIframeHostCommand.isAllowed("https://www.google.com/recaptcha/api2/anchor"));
    }
  }

  @Test
  void noVendorHostIsAllowedWhenTheBuiltInCaptchaIsInUse() {
    // A service named but no site key falls through to the built-in text captcha, which draws no
    // iframe. Allowing a vendor host here would widen frame-src for a feature that is not in use.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWithCaptcha("google", "")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("https://www.google.com/recaptcha/api2/anchor"));
    }
  }

  @Test
  void thePlatformsOwnHostsAreAllowedWithNothingConfigured() {
    // The default state of every site: the property is seeded empty. Empty must not mean "nothing
    // is allowed" -- the Video widget ships enabled and has to keep working untouched.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://www.youtube-nocookie.com/embed/abc123"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://player.vimeo.com/video/12345"));
    }
  }

  @Test
  void anUnconfiguredHostIsRefused() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("https://evil.example.com/x"));
    }
  }

  @Test
  void aSiteCannotRemoveAPlatformHostByOmittingIt() {
    // Listing only their own host must not drop YouTube and Vimeo -- otherwise adding one embed
    // silently breaks every video on the site.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("app.vendor.example.com")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://www.youtube-nocookie.com/embed/abc"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://app.vendor.example.com/embed"));
    }
  }

  @Test
  void configuredHostsAreSplitOnCommasAndWhitespace() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("a.example.com, b.example.com\nc.example.com")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://a.example.com/x"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://b.example.com/x"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://c.example.com/x"));
    }
  }

  @Test
  void aFullUrlPastedWhereAHostWasAskedForStillWorks() {
    // The label says host, but a URL is what people paste. Understanding it beats failing silently.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("https://app.vendor.example.com/embed/form")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://app.vendor.example.com/embed/form"));
    }
  }

  @Test
  void hostMatchingIsCaseInsensitive() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("App.Vendor.Example.COM")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://app.vendor.example.com/x"));
    }
  }

  @Test
  void aSubdomainOfAnAllowedHostIsNotItselfAllowed() {
    // Host matching is exact. "evil.app.vendor.example.com" is a different origin and a different
    // party; allowing it because it ends in an allowed host would be a suffix-match bug.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("app.vendor.example.com")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("https://evil.app.vendor.example.com/x"));
    }
  }

  @Test
  void aHostThatMerelyEndsWithAnAllowedNameIsRefused() {
    // "notyoutube-nocookie.com" and "www.youtube-nocookie.com.evil.test" both contain an allowed
    // host as a substring without being it.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("https://www.youtube-nocookie.com.evil.test/x"));
      assertFalse(AllowedIframeHostCommand.isAllowed("https://notwww.youtube-nocookie.com/x"));
    }
  }

  @Test
  void aRelativeSourceIsSameOriginAndAllowed() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("/embed/something"));
      assertTrue(AllowedIframeHostCommand.isAllowed("embed/something"));
    }
  }

  @Test
  void aProtocolRelativeSourceIsResolvedNotTreatedAsRelative() {
    // "//evil.example.com/x" looks like a path but loads from another origin.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("//evil.example.com/x"));
      assertTrue(AllowedIframeHostCommand.isAllowed("//player.vimeo.com/video/1"));
    }
  }

  @Test
  void scriptBearingSchemesAreRefused() {
    // Neither of these contains "://", so a naive absolute-URL check reads them as relative paths.
    // Both execute in the embedding page's context.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("javascript:alert(1)"));
      assertFalse(AllowedIframeHostCommand.isAllowed("JaVaScRiPt:alert(1)"));
      assertFalse(AllowedIframeHostCommand.isAllowed("data:text/html,<script>alert(1)</script>"));
      assertFalse(AllowedIframeHostCommand.isAllowed("vbscript:msgbox(1)"));
    }
  }

  @Test
  void aBlankOrUnparseableSourceIsRefused() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed(null));
      assertFalse(AllowedIframeHostCommand.isAllowed("   "));
      assertFalse(AllowedIframeHostCommand.isAllowed("https://"));
    }
  }

  @Test
  void theMetabaseHostIsAllowedOnlyWhenTheIntegrationIsEnabled() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadByName(AllowedIframeHostCommand.SITE_PROPERTY)).thenReturn("");
      m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.enabled")).thenReturn("true");
      m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.url")).thenReturn("https://bi.example.com");
      assertTrue(AllowedIframeHostCommand.isAllowed("https://bi.example.com/public/dashboard/x"));
    }
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadByName(AllowedIframeHostCommand.SITE_PROPERTY)).thenReturn("");
      m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.enabled")).thenReturn("false");
      m.when(() -> LoadSitePropertyCommand.loadByName("bi.metabase.url")).thenReturn("https://bi.example.com");
      assertFalse(AllowedIframeHostCommand.isAllowed("https://bi.example.com/public/dashboard/x"));
    }
  }

  @Test
  void theCspSourceListStartsWithSelfAndNamesEveryHostAsHttps() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("app.vendor.example.com")) {
      assertEquals(
          "'self' https://www.youtube-nocookie.com https://youtube-nocookie.com https://www.youtube.com https://youtube.com https://player.vimeo.com https://app.vendor.example.com",
          AllowedIframeHostCommand.cspFrameSourceList());
    }
  }

  @Test
  void theCspSourceListHasNoDuplicatesWhenAPlatformHostIsAlsoConfigured() {
    // A duplicate would not break the header, but it is a sign the set logic stopped working.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("player.vimeo.com")) {
      assertEquals("'self' https://www.youtube-nocookie.com https://youtube-nocookie.com https://www.youtube.com https://youtube.com https://player.vimeo.com",
          AllowedIframeHostCommand.cspFrameSourceList());
    }
  }

  @Test
  void aJunkEntryIsSkippedWithoutBreakingTheRestOfTheList() {
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith(":::, app.vendor.example.com")) {
      assertTrue(AllowedIframeHostCommand.isAllowed("https://app.vendor.example.com/x"));
      assertFalse(AllowedIframeHostCommand.cspFrameSourceList().contains(":::"));
    }
  }

  @Test
  void youTubesOwnEmbedMarkupIsAllowed() {
    // The regression this list was widened for. VideoWidget renders youtube-nocookie.com, but an
    // author pasting YouTube's "Copy embed code" gets www.youtube.com/embed -- and three published
    // news posts on the pilot carried exactly that, including the ?si= share parameter. Deriving the
    // list from what the widget emits stripped real content on save and refused it at render.
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertTrue(AllowedIframeHostCommand
          .isAllowed("https://www.youtube.com/embed/LFx-b-njZs0?si=xSeHTMlObQxvqrP9"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://youtube.com/embed/8elFL8KThY0"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://www.youtube-nocookie.com/embed/qYIRapHuDvU"));
      assertTrue(AllowedIframeHostCommand.isAllowed("https://youtube-nocookie.com/embed/qYIRapHuDvU"));
    }
  }

  @Test
  void wideningForYouTubeDidNotWidenToAnythingElse() {
    // The list gained a vendor's other domains, not a general relaxation
    try (MockedStatic<LoadSitePropertyCommand> m = siteWith("")) {
      assertFalse(AllowedIframeHostCommand.isAllowed("https://youtube.com.evil.test/embed/x"));
      assertFalse(AllowedIframeHostCommand.isAllowed("https://notyoutube.com/embed/x"));
      assertFalse(AllowedIframeHostCommand.isAllowed("https://evil.example.com/embed/x"));
    }
  }

  @Test
  void aCallerCanPassAListItAlreadyHas() {
    // The overload HtmlCommand uses so a document with many embeds reads the property once
    java.util.List<String> allowed = java.util.List.of("app.vendor.example.com");
    assertTrue(AllowedIframeHostCommand.isAllowed("https://app.vendor.example.com/x", allowed));
    assertFalse(AllowedIframeHostCommand.isAllowed("https://www.youtube.com/embed/x", allowed));
    assertFalse(AllowedIframeHostCommand.isAllowed("https://a.example.com/x", null));
  }
}
