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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.benmanes.caffeine.cache.Cache;

/**
 * Covers the CacheManager additions made for issue #463 (admin cache management dashboard):
 * inventory listing, clear-one, clear-all, and "last cleared" tracking.
 *
 * <p>
 * CacheManager is a real, JVM-wide static registry shared by every test that runs in this build
 * (test classes are launched inside a single forked JVM, see build.xml's junitlauncher). These
 * tests therefore avoid asserting "pristine"/"never touched" state up front -- some other test
 * class may have already caused a cache to be loaded or cleared before this class runs -- and
 * instead assert the effect each call under test has relative to its own setup.
 * </p>
 *
 * @author elizabeth houser
 */
class CacheManagerTest {

  @Test
  void getCacheNamesReturnsAllThirteenRegisteredCaches() {
    Set<String> names = CacheManager.getCacheNames();
    assertEquals(13, names.size());
    assertTrue(names.contains(CacheManager.SYSTEM_PROPERTY_PREFIX_CACHE));
    assertTrue(names.contains(CacheManager.APP_CACHE));
    assertTrue(names.contains(CacheManager.USER_CREDENTIALS_CACHE));
    assertTrue(names.contains(CacheManager.STYLESHEET_WEB_PAGE_ID_CACHE));
    assertTrue(names.contains(CacheManager.CONTENT_UNIQUE_ID_CACHE));
    assertTrue(names.contains(CacheManager.CONTENT_REMOTE_URL_CACHE));
    assertTrue(names.contains(CacheManager.COLLECTION_UNIQUE_ID_CACHE));
    assertTrue(names.contains(CacheManager.TABLE_OF_CONTENTS_UNIQUE_ID_CACHE));
    assertTrue(names.contains(CacheManager.RATE_LIMIT_LOGIN_ATTEMPT_BY_USERNAME_CACHE));
    assertTrue(names.contains(CacheManager.RATE_LIMIT_ATTEMPT_BY_IP_CACHE));
    assertTrue(names.contains(CacheManager.RATE_LIMIT_BY_APP_CACHE));
    assertTrue(names.contains(CacheManager.RATE_LIMIT_BY_APP_USER_CACHE));
    assertTrue(names.contains(CacheManager.OBJECT_CACHE));
  }

  @Test
  void getCacheNamesReturnsAnUnmodifiableView() {
    Set<String> names = CacheManager.getCacheNames();
    assertThrows(UnsupportedOperationException.class, () -> names.add("SomeNewCache"));
  }

  @Test
  void invalidateAllClearsAllEntriesInTheNamedCacheAndRecordsWhenItHappened() {
    @SuppressWarnings("unchecked")
    Cache<String, Object> objectCache = CacheManager.getCache(CacheManager.OBJECT_CACHE);
    objectCache.put("cache-manager-test-key", "value");
    assertNotNull(objectCache.getIfPresent("cache-manager-test-key"));

    Instant before = Instant.now();
    CacheManager.invalidateAll(CacheManager.OBJECT_CACHE);
    Instant after = Instant.now();

    assertNull(objectCache.getIfPresent("cache-manager-test-key"));
    Instant lastCleared = CacheManager.getLastClearedAt(CacheManager.OBJECT_CACHE);
    assertNotNull(lastCleared);
    assertFalse(lastCleared.isBefore(before));
    assertFalse(lastCleared.isAfter(after));
  }

  @Test
  void invalidateAllIsANoOpForAnUnregisteredCacheName() {
    assertDoesNotThrow(() -> CacheManager.invalidateAll("NotARealCache"));
    assertNull(CacheManager.getLastClearedAt("NotARealCache"));
  }

  @Test
  void recordStatsIsActuallyEnabledSoHitAndMissCountsMoveOffZero() {
    // The core of issue #463's CacheManager change is adding .recordStats() to every Caffeine
    // builder in startup() -- without it, Cache.stats() always returns CacheStats.empty() (all
    // zeros) regardless of how many times a cache is actually used. This drives a real
    // miss-then-hit cycle and asserts both counters move, rather than trusting that the
    // .recordStats() call site was wired up correctly. Uses deltas, not absolute counts, since
    // OBJECT_CACHE is real JVM-wide static state other test classes may also be touching (see the
    // class-level note above).
    @SuppressWarnings("unchecked")
    Cache<String, Object> objectCache = CacheManager.getCache(CacheManager.OBJECT_CACHE);
    long hitsBefore = objectCache.stats().hitCount();
    long missesBefore = objectCache.stats().missCount();

    String key = "cache-manager-test-recordstats-key";
    assertNull(objectCache.getIfPresent(key), "test setup: this key must not already be cached");
    objectCache.put(key, "value");
    assertNotNull(objectCache.getIfPresent(key));

    assertTrue(objectCache.stats().missCount() > missesBefore,
        "the first getIfPresent (a miss) must be recorded now that .recordStats() is enabled");
    assertTrue(objectCache.stats().hitCount() > hitsBefore,
        "the second getIfPresent (a hit) must be recorded now that .recordStats() is enabled");
  }

  @Test
  void invalidateAllCachesClearsEveryRegisteredCacheAndRecordsWhenEachWasCleared() {
    @SuppressWarnings("unchecked")
    Cache<String, Object> objectCache = CacheManager.getCache(CacheManager.OBJECT_CACHE);
    objectCache.put("cache-manager-test-key-2", "value");
    @SuppressWarnings("unchecked")
    Cache<Long, String> userCredentialsCache = CacheManager.getCache(CacheManager.USER_CREDENTIALS_CACHE);
    userCredentialsCache.put(999_999L, "some-hash");

    CacheManager.invalidateAllCaches();

    assertNull(objectCache.getIfPresent("cache-manager-test-key-2"));
    assertNull(userCredentialsCache.getIfPresent(999_999L));
    for (String cacheName : CacheManager.getCacheNames()) {
      assertNotNull(CacheManager.getLastClearedAt(cacheName), "Expected " + cacheName + " to have a last-cleared time");
    }
  }
}
