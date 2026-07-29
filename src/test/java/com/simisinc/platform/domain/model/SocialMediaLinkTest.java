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
