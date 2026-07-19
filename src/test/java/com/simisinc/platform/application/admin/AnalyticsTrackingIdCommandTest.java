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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests validation and sanitization of third-party analytics tracking ids
 *
 * @author elizabeth houser
 */
class AnalyticsTrackingIdCommandTest {

  @Test
  void wellFormedTrackingIdsAreValid() {
    assertTrue(AnalyticsTrackingIdCommand.isValid("G-XXXXXXXX"));
    assertTrue(AnalyticsTrackingIdCommand.isValid("UA-123456-1"));
    assertTrue(AnalyticsTrackingIdCommand.isValid("GTM-ABCD123"));
    assertTrue(AnalyticsTrackingIdCommand.isValid("AW-1234567890"));
    assertTrue(AnalyticsTrackingIdCommand.isValid("1a2b3c4d5e"));
    // Blank is allowed -- the tag is simply not rendered
    assertTrue(AnalyticsTrackingIdCommand.isValid(""));
    assertTrue(AnalyticsTrackingIdCommand.isValid(null));
  }

  @Test
  void valuesThatCanBreakOutOfThePageAreInvalid() {
    // The exact escaped-quote attribute-breakout class that this defends against
    assertFalse(AnalyticsTrackingIdCommand.isValid("x\" onerror=alert(1) x=\""));
    assertFalse(AnalyticsTrackingIdCommand.isValid("GTM-X\" onload=alert(1)"));
    assertFalse(AnalyticsTrackingIdCommand.isValid("</script><script>alert(1)</script>"));
    assertFalse(AnalyticsTrackingIdCommand.isValid("G-XX'+alert(1)+'"));
    assertFalse(AnalyticsTrackingIdCommand.isValid("id with spaces"));
    assertFalse(AnalyticsTrackingIdCommand.isValid("a/b"));
    assertFalse(AnalyticsTrackingIdCommand.isValid("back\\slash"));
  }

  @Test
  void sanitizeBlanksMalformedIdsAndKeepsValidOnes() {
    Map<String, String> analytics = new HashMap<>();
    analytics.put("analytics.service", "google");
    analytics.put("analytics.google.key", "G-GOOD123");
    analytics.put("analytics.google.tagmanager", "GTM-X\" onload=alert(1)");
    analytics.put("analytics.simplifi.value", "abc123");
    analytics.put("analytics.brandcdn.value", "</script><script>x");

    AnalyticsTrackingIdCommand.sanitize(analytics);

    // Valid ids are untouched
    assertEquals("G-GOOD123", analytics.get("analytics.google.key"));
    assertEquals("abc123", analytics.get("analytics.simplifi.value"));
    // Malformed ids are blanked so the page template will not render them
    assertEquals("", analytics.get("analytics.google.tagmanager"));
    assertEquals("", analytics.get("analytics.brandcdn.value"));
    // Non-tracking-id properties are left alone
    assertEquals("google", analytics.get("analytics.service"));
  }

  @Test
  void isTrackingIdPropertyIdentifiesOnlyTheRenderedIds() {
    assertTrue(AnalyticsTrackingIdCommand.isTrackingIdProperty("analytics.google.key"));
    assertTrue(AnalyticsTrackingIdCommand.isTrackingIdProperty("analytics.brandcdn.value2"));
    assertFalse(AnalyticsTrackingIdCommand.isTrackingIdProperty("analytics.service"));
    assertFalse(AnalyticsTrackingIdCommand.isTrackingIdProperty(null));
  }
}
