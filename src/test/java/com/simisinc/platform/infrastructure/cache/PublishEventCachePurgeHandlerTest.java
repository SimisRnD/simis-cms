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

package com.simisinc.platform.infrastructure.cache;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.WebPage;

/**
 * #420: there is no live Azure Front Door subscription for this milestone yet (confirmed against
 * CI's dormant Azure steps), so a real purge call cannot be exercised here -- but the "not
 * configured" path is exactly what every deployment hits today, and that IS testable: none of the
 * four required AZURE_FRONTDOOR_... / AZURE_SUBSCRIPTION_ID env vars are set in this test
 * environment, so these tests exercise the real skip path (not a mock of it) and confirm it never
 * throws, never blocks publishing, and returns fast enough that it plainly never touched the
 * network or the Azure SDK -- mirroring how the Application Insights agent self-disables with no
 * connection string set.
 */
class PublishEventCachePurgeHandlerTest {

  // Generous relative to a real network attempt (DNS + TLS + an HTTP round trip, or a credential
  // chain probing IMDS/CLI/env), tight enough to fail if the skip path stopped short-circuiting.
  private static final long MAX_SKIP_MILLIS = 2000L;

  @org.junit.jupiter.api.BeforeAll
  static void confirmTestEnvironmentIsUnconfigured() {
    // If a developer's shell happens to export these, the "skip" tests below would no longer be
    // exercising the no-op path -- skip rather than give a false pass/fail.
    Assumptions.assumeTrue(System.getenv(PublishEventCachePurgeHandler.PROFILE_NAME_ENV) == null
        && System.getenv(PublishEventCachePurgeHandler.RESOURCE_GROUP_ENV) == null
        && System.getenv(PublishEventCachePurgeHandler.ENDPOINT_NAME_ENV) == null
        && System.getenv(PublishEventCachePurgeHandler.SUBSCRIPTION_ID_ENV) == null,
        "AFD env vars are set in this environment; the unconfigured-skip path can't be exercised here");
  }

  private static void assertSkipsFast(Runnable call) {
    long start = System.currentTimeMillis();
    assertDoesNotThrow(call::run);
    long elapsed = System.currentTimeMillis() - start;
    assertTrue(elapsed < MAX_SKIP_MILLIS,
        "Expected the unconfigured path to return immediately, took " + elapsed + "ms");
  }

  @Test
  void onPagePublishedSkipsGracefullyWhenAfdIsNotConfigured() {
    WebPage webPage = new WebPage();
    webPage.setLink("/about");
    assertSkipsFast(() -> PublishEventCachePurgeHandler.onPagePublished(webPage));
  }

  @Test
  void onPageUpdatedSkipsGracefullyWhenAfdIsNotConfigured() {
    WebPage webPage = new WebPage();
    webPage.setLink("/about");
    assertSkipsFast(() -> PublishEventCachePurgeHandler.onPageUpdated(webPage));
  }

  @Test
  void onPageDeletedSkipsGracefullyWhenAfdIsNotConfigured() {
    assertSkipsFast(() -> PublishEventCachePurgeHandler.onPageDeleted("/about"));
  }

  @Test
  void purgeUrlsSkipsGracefullyWhenAfdIsNotConfigured() {
    assertSkipsFast(() -> PublishEventCachePurgeHandler.purgeUrls(new String[]{"/about", "/news/a"}));
  }

  @Test
  void purgeUrlsIsANoOpWithNoUrls() {
    assertSkipsFast(() -> PublishEventCachePurgeHandler.purgeUrls(null));
    assertSkipsFast(() -> PublishEventCachePurgeHandler.purgeUrls(new String[0]));
  }

  @Test
  void onPagePublishedIgnoresAPageWithNoLink() {
    // Guards the existing null-safety this handler already had -- a page with no link has nothing
    // to purge, and must not NPE building the URL.
    assertSkipsFast(() -> PublishEventCachePurgeHandler.onPagePublished(new WebPage()));
    assertSkipsFast(() -> PublishEventCachePurgeHandler.onPagePublished(null));
  }
}
