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

package com.simisinc.platform.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * @author SimIS Inc.
 */
class SocialMediaLinkTest {

  @Test
  void getIconClassResolvesKnownPlatformsCaseInsensitively() {
    assertEquals("fa-facebook-square", iconFor("Facebook"));
    assertEquals("fa-instagram", iconFor("instagram"));
    assertEquals("fa-mastodon", iconFor("MASTODON"));
    assertEquals("fa-tiktok", iconFor("TikTok"));
    assertEquals("fa-discord", iconFor("Discord"));
    assertEquals("fa-github", iconFor("GitHub"));
  }

  @Test
  void getIconClassResolvesRssEvenThoughItIsNotABrandGlyph() {
    // Every other entry is a FontAwesome Brands glyph. RSS is a format rather than a company, so
    // its icon comes from Free (solid) instead -- worth pinning, because the widget renders the
    // shared "fa" prefix and relies on v4-shims to pick the right family. If the bundled shim
    // stopped covering Free, this would silently fall through to fa-link like any unknown name.
    assertEquals("fa-rss", iconFor("RSS"));
    assertEquals("fa-rss", iconFor("rss"));
    assertEquals("fa-rss", iconFor("  Rss  "));
  }

  @Test
  void getIconClassResolvesTwitterAndXToTheSameCurrentLogo() {
    // Twitter rebranded to X in 2023 -- both names should resolve to the current X glyph
    assertEquals("fa-x-twitter", iconFor("Twitter"));
    assertEquals("fa-x-twitter", iconFor("X"));
    assertEquals("fa-x-twitter", iconFor("x"));
  }

  @Test
  void getIconClassResolvesTheCompoundSpellingsOfTheRebrand() {
    // "X/Twitter" is the name the Add a Platform form's own help text offers, so it has to work --
    // it silently produced the generic link icon, which is how a live site ended up with one.
    assertEquals("fa-x-twitter", iconFor("X/Twitter"));
    assertEquals("fa-x-twitter", iconFor("x/twitter"));
    assertEquals("fa-x-twitter", iconFor("Twitter/X"));
    assertEquals("fa-x-twitter", iconFor("X (Twitter)"));
    assertEquals("fa-x-twitter", iconFor("Twitter (X)"));
    assertEquals("fa-x-twitter", iconFor("X (formerly Twitter)"));
  }

  @Test
  void getIconClassStillMatchesExactlyRatherThanBySubstring() {
    // The reason the spellings above are enumerated instead of matched fuzzily.
    assertEquals("fa-link", iconFor("Twitterrific"));
    assertEquals("fa-link", iconFor("Xbox"));
    assertEquals("fa-link", iconFor("X-Files Fan Club"));
  }

  @Test
  void getIconClassFallsBackForUnknownOrNewPlatforms() {
    // Bundled FontAwesome predates dedicated Threads/Bluesky glyphs
    assertEquals("fa-link", iconFor("Threads"));
    assertEquals("fa-link", iconFor("Bluesky"));
    assertEquals("fa-link", iconFor("SomeFuturePlatform"));
  }

  @Test
  void getIconClassHandlesMissingPlatformNameSafely() {
    SocialMediaLink link = new SocialMediaLink();
    assertEquals("fa-link", link.getIconClass());
  }

  private static String iconFor(String platformName) {
    SocialMediaLink link = new SocialMediaLink();
    link.setPlatformName(platformName);
    return link.getIconClass();
  }
}
