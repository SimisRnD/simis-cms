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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Verifies the admin-configurable rate-limit settings added for issue #487: the site properties
 * are read only when a new bucket is created (not on every request), values are safely bounded,
 * and the defaults exactly match the previously hardcoded limits.
 *
 * @author elizabeth houser
 */
class RateLimitCommandTest {

  // --- resolveLimitValue() ---

  @Test
  void resolveLimitValueUsesTheDefaultWhenBlank() {
    assertEquals(10, RateLimitCommand.resolveLimitValue("", 10, 1000));
    assertEquals(10, RateLimitCommand.resolveLimitValue(null, 10, 1000));
    assertEquals(10, RateLimitCommand.resolveLimitValue("   ", 10, 1000));
  }

  @Test
  void resolveLimitValueUsesTheDefaultWhenNonNumeric() {
    assertEquals(10, RateLimitCommand.resolveLimitValue("abc", 10, 1000));
    assertEquals(10, RateLimitCommand.resolveLimitValue("5; DROP TABLE site_properties", 10, 1000));
  }

  @Test
  void resolveLimitValueUsesTheDefaultWhenZeroOrNegative() {
    // A zero or negative value would mean "never allowed" or immediately disable the bucket --
    // not a valid rate limit, so it must not be able to accidentally disable protection entirely.
    assertEquals(10, RateLimitCommand.resolveLimitValue("0", 10, 1000));
    assertEquals(10, RateLimitCommand.resolveLimitValue("-5", 10, 1000));
  }

  @Test
  void resolveLimitValueParsesAValidValue() {
    assertEquals(3, RateLimitCommand.resolveLimitValue("3", 10, 1000));
  }

  @Test
  void resolveLimitValueBoundsAnAbsurdlyLargeValue() {
    assertEquals(1000, RateLimitCommand.resolveLimitValue("999999999", 10, 1000));
  }

  // --- isIpAllowedRightNow() / isUsernameAllowedRightNow() read the configured values ---

  // Note on the expected counts below: the very first call for a not-yet-seen key creates the
  // bucket and returns true WITHOUT consuming from it (pre-existing behavior, not something this
  // change alters) -- so a configured max of N attempts actually permits N+1 total calls (the
  // initial untouched-bucket call, plus N real consumes) before the first block.

  @Test
  void isIpAllowedRightNowAllowsUpToTheConfiguredMaxAttempts() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipMaxAttempts")).thenReturn("2");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipWindowMinutes")).thenReturn("30");

      String ip = "203.0.113.1-" + System.nanoTime();
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "1st attempt should be allowed");
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "2nd attempt should be allowed");
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "3rd attempt should be allowed (configured max of 2 + the untouched-bucket first call)");
      assertFalse(RateLimitCommand.isIpAllowedRightNow(ip, true), "4th attempt should be blocked");
    }
  }

  @Test
  void isIpAllowedRightNowFallsBackToTheDefaultWhenNoPropertyIsConfigured() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(org.mockito.ArgumentMatchers.anyString())).thenReturn("");

      String ip = "203.0.113.2-" + System.nanoTime();
      // Default is 10 attempts, plus the untouched-bucket first call -- 11 total should succeed.
      for (int i = 0; i < 11; i++) {
        assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "attempt " + (i + 1) + " should be allowed under the default limit");
      }
      assertFalse(RateLimitCommand.isIpAllowedRightNow(ip, true), "12th attempt should be blocked under the default limit of 10");
    }
  }

  // --- isApiIpAllowedRightNow() is bucketed separately from isIpAllowedRightNow() ---

  @Test
  void isApiIpAllowedRightNowAllowsUpToTheConfiguredMaxAttempts() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipMaxAttempts")).thenReturn("2");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipWindowMinutes")).thenReturn("30");

      String ip = "203.0.113.3-" + System.nanoTime();
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "1st attempt should be allowed");
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "2nd attempt should be allowed");
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "3rd attempt should be allowed (configured max of 2 + the untouched-bucket first call)");
      assertFalse(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "4th attempt should be blocked");
    }
  }

  @Test
  void exhaustingTheApiIpBucketDoesNotAffectTheSharedIpBucketForTheSameIp() {
    // The core of this change: an IP that exhausts its allowance via repeated bad API-key
    // attempts (isApiIpAllowedRightNow) must not also lock that same IP out of web login,
    // forgot-password, newsletter, or form-submission attempts (isIpAllowedRightNow) -- the two
    // methods must read/write separate cache buckets, not merely separate keys in the same one.
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipMaxAttempts")).thenReturn("1");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipWindowMinutes")).thenReturn("30");

      String ip = "203.0.113.4-" + System.nanoTime();

      // Exhaust the API-only bucket for this IP.
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "1st API attempt should be allowed (untouched-bucket call)");
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "2nd API attempt should be allowed (configured max is 1)");
      assertFalse(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "3rd API attempt should be blocked");

      // The shared (web login/forms/newsletter) bucket for the exact same IP must be untouched.
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "1st shared-bucket attempt should still be allowed (untouched-bucket call)");
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true), "2nd shared-bucket attempt should still be allowed (configured max is 1)");
      assertFalse(RateLimitCommand.isIpAllowedRightNow(ip, true), "3rd shared-bucket attempt should be blocked on its own terms, not because the API bucket was already exhausted");
    }
  }

  @Test
  void exhaustingTheSharedIpBucketDoesNotAffectTheApiIpBucketForTheSameIp() {
    // The mirror image of the test above: web login/forms/newsletter failures on an IP must not
    // throttle that IP's future API requests either.
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipMaxAttempts")).thenReturn("1");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.ipWindowMinutes")).thenReturn("30");

      String ip = "203.0.113.5-" + System.nanoTime();

      // Exhaust the shared bucket for this IP.
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true));
      assertTrue(RateLimitCommand.isIpAllowedRightNow(ip, true));
      assertFalse(RateLimitCommand.isIpAllowedRightNow(ip, true));

      // The API-only bucket for the exact same IP must be untouched.
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "1st API attempt should still be allowed (untouched-bucket call)");
      assertTrue(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "2nd API attempt should still be allowed (configured max is 1)");
      assertFalse(RateLimitCommand.isApiIpAllowedRightNow(ip, true), "3rd API attempt should be blocked on its own terms, not because the shared bucket was already exhausted");
    }
  }

  @Test
  void isUsernameAllowedRightNowAllowsUpToTheConfiguredMaxAttempts() {
    try (MockedStatic<LoadSitePropertyCommand> siteProperty = org.mockito.Mockito.mockStatic(LoadSitePropertyCommand.class)) {
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.usernameMaxAttempts")).thenReturn("1");
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName("security.rateLimit.usernameWindowMinutes")).thenReturn("30");

      String username = "test-user-" + System.nanoTime();
      assertTrue(RateLimitCommand.isUsernameAllowedRightNow(username, true), "1st attempt should be allowed (untouched-bucket call)");
      assertTrue(RateLimitCommand.isUsernameAllowedRightNow(username, true), "2nd attempt should be allowed (configured max is 1)");
      assertFalse(RateLimitCommand.isUsernameAllowedRightNow(username, true), "3rd attempt should be blocked");
    }
  }
}
