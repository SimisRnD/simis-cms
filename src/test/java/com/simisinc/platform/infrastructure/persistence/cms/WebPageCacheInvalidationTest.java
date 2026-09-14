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

package com.simisinc.platform.infrastructure.persistence.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.CacheManager;

/**
 * Guards the link cache added for issue #2034 — specifically the half that goes wrong silently.
 *
 * <p>Caching `findByLink` removed ~50 database queries from every anonymous page render. The cost
 * of getting it wrong is not a crash: it is an editor saving a page and the site continuing to
 * serve the old one, with nothing failing to say why. Every mutation path in
 * {@link WebPageRepository} therefore has to evict, and the risk is a future path that remembers
 * the layout cache and forgets this one.
 *
 * @author elizabeth houser
 */
class WebPageCacheInvalidationTest {

  private static final File REPOSITORY = new File(
      "src/main/java/com/simisinc/platform/infrastructure/persistence/cms/WebPageRepository.java");

  private static String source() throws IOException {
    assertTrue(REPOSITORY.isFile(), "not found (run from the project root): " + REPOSITORY.getAbsolutePath());
    return Files.readString(REPOSITORY.toPath(), StandardCharsets.UTF_8);
  }

  /**
   * The layout cache and the link cache must always be dropped together, which is why they live in
   * one helper. A bare call to the layout cache outside that helper is the exact shape of the bug
   * this test exists to prevent: half the caches cleared, a stale page served, nothing failing.
   */
  @Test
  void everyMutationPathEvictsThroughThePairedHelper() throws IOException {
    // Everything up to the helper's own definition. The helper legitimately calls
    // removeCustomPage -- that is the point of it -- so scanning the whole file would only ever
    // flag the fix itself, which is what the first run of this test did.
    String source = source();
    int helperAt = source.indexOf("private static void invalidateCachedPage(");
    assertTrue(helperAt > -1, "invalidateCachedPage(String) should exist");

    List<String> offenders = new ArrayList<>();
    for (String line : source.substring(0, helperAt).split("\n")) {
      String trimmed = line.trim();
      if (trimmed.startsWith("//") || trimmed.startsWith("*")) {
        continue;
      }
      if (trimmed.contains("WebPageXmlLayoutCommand.removeCustomPage(")) {
        offenders.add(trimmed);
      }
    }
    assertEquals(List.of(), offenders,
        "call invalidateCachedPage(link) instead — it drops the layout cache AND the #2034 link "
            + "cache, and a mutation path that clears only one serves a stale page until expiry");
  }

  @Test
  void theHelperDropsBothCaches() throws IOException {
    String helper = source();
    int start = helper.indexOf("private static void invalidateCachedPage(");
    assertTrue(start > -1, "invalidateCachedPage(String) should exist");
    String body = helper.substring(start, Math.min(helper.length(), start + 900));
    assertTrue(body.contains("WebPageXmlLayoutCommand.removeCustomPage("), "must drop the XML layout cache");
    assertTrue(body.contains("CacheManager.invalidateKey(CacheManager.WEB_PAGE_CACHE"),
        "must drop the link cache");
  }

  /** Insert included: a negative entry cached while the link 404'd would otherwise outlive the page. */
  @Test
  void insertAlsoEvicts() throws IOException {
    String insertSection = source();
    int add = insertSection.indexOf("private static WebPage add(");
    int update = insertSection.indexOf("private static WebPage update(");
    assertTrue(add > -1 && update > add, "add(...) should precede update(...)");
    assertTrue(insertSection.substring(add, update).contains("invalidateCachedPage("),
        "add() must evict too, or a WebPage.NONE cached before the page existed keeps it invisible");
  }

  @Test
  void theCacheIsRegisteredAndTheSentinelIsDistinct() {
    assertNotNull(CacheManager.WEB_PAGE_CACHE);
    assertNotNull(WebPage.NONE, "the cache stores this instead of null, which Caffeine will not cache");
    assertNotEquals(-1, WebPage.NONE.getId() + 1,
        "NONE is an unsaved placeholder; it must never look like a real row");
  }
}
