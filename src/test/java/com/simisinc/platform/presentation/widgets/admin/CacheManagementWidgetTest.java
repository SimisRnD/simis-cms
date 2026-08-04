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

package com.simisinc.platform.presentation.widgets.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class CacheManagementWidgetTest extends WidgetBase {

  private static Cache<?, ?> mockCache(long estimatedSize, long hits, long misses, long evictions) {
    @SuppressWarnings("unchecked")
    Cache<Object, Object> cache = Mockito.mock(Cache.class);
    when(cache.estimatedSize()).thenReturn(estimatedSize);
    when(cache.stats()).thenReturn(CacheStats.of(hits, misses, 0, 0, 0, evictions, 0));
    return cache;
  }

  @Test
  void executeLoadsTheCacheSummaryListForAnAdmin() {
    setRoles(widgetContext, ADMIN);

    // Built before any MockedStatic#when() call is opened below -- mockCache() itself calls
    // Mockito's when(), and nesting that inside an unfinished cacheManager.when(...).thenReturn(...)
    // throws UnfinishedStubbingException (Mockito can't tell which when() the next thenReturn()
    // belongs to).
    Cache<?, ?> objectCacheMock = mockCache(5, 10, 2, 0);
    Cache<?, ?> appCacheMock = mockCache(3, 0, 0, 0);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(CacheManager::getCacheNames)
          .thenReturn(Set.of(CacheManager.OBJECT_CACHE, CacheManager.APP_CACHE));
      cacheManager.when(() -> CacheManager.getCache(CacheManager.OBJECT_CACHE)).thenReturn(objectCacheMock);
      cacheManager.when(() -> CacheManager.getCache(CacheManager.APP_CACHE)).thenReturn(appCacheMock);
      cacheManager.when(() -> CacheManager.getLastClearedAt(CacheManager.OBJECT_CACHE))
          .thenReturn(Instant.now());
      cacheManager.when(() -> CacheManager.getLastClearedAt(CacheManager.APP_CACHE)).thenReturn(null);

      WidgetContext result = new CacheManagementWidget().execute(widgetContext);

      assertEquals("/admin/cache-management.jsp", result.getJsp());
      @SuppressWarnings("unchecked")
      List<CacheManagementWidget.CacheSummary> cacheSummaryList =
          (List<CacheManagementWidget.CacheSummary>) result.getRequest().getAttribute("cacheSummaryList");
      assertEquals(2, cacheSummaryList.size());

      // Alphabetical: AppCache before ObjectCache
      CacheManagementWidget.CacheSummary appCacheSummary = cacheSummaryList.get(0);
      assertEquals(CacheManager.APP_CACHE, appCacheSummary.getName());
      assertEquals(3, appCacheSummary.getEstimatedSize());
      assertTrue(appCacheSummary.isNeverCleared());

      CacheManagementWidget.CacheSummary objectCacheSummary = cacheSummaryList.get(1);
      assertEquals(CacheManager.OBJECT_CACHE, objectCacheSummary.getName());
      assertEquals(5, objectCacheSummary.getEstimatedSize());
      assertEquals(10, objectCacheSummary.getHitCount());
      assertEquals(2, objectCacheSummary.getMissCount());
      assertFalse(objectCacheSummary.isNeverCleared());
    }
  }

  @Test
  void executeDoesNothingForAUserWithoutAdmin() {
    // WidgetBase's default logged-in test user has no roles at all
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      WidgetContext result = new CacheManagementWidget().execute(widgetContext);

      assertNull(result.getJsp());
      cacheManager.verifyNoInteractions();
    }
  }

  @Test
  void postClearAllInvalidatesEveryCacheRecordsAnAuditEventAndRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "clearAll");

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {

      WidgetContext result = new CacheManagementWidget().post(widgetContext);

      cacheManager.verify(CacheManager::invalidateAllCaches);
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION),
          eq("cache.clear_all"), eq(AuditEventCommand.SUCCESS), eq("cache"), isNull(), isNull(), anyString()));
      assertEquals("/admin/cache-management", result.getRedirect());
      assertEquals("All caches were cleared", result.getSuccessMessage());
    }
  }

  @Test
  void postClearCacheClearsAKnownCacheRecordsAnAuditEventAndRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "clearCache");
    addQueryParameter(widgetContext, "cache", CacheManager.OBJECT_CACHE);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      cacheManager.when(CacheManager::getCacheNames).thenReturn(Set.of(CacheManager.OBJECT_CACHE));

      WidgetContext result = new CacheManagementWidget().post(widgetContext);

      cacheManager.verify(() -> CacheManager.invalidateAll(CacheManager.OBJECT_CACHE));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION), eq("cache.clear"),
          eq(AuditEventCommand.SUCCESS), eq("cache"), eq(CacheManager.OBJECT_CACHE), eq(CacheManager.OBJECT_CACHE),
          anyString()));
      assertEquals("/admin/cache-management", result.getRedirect());
      assertEquals("Cache cleared: " + CacheManager.OBJECT_CACHE, result.getSuccessMessage());
    }
  }

  @Test
  void postClearCacheRejectsACacheNameNotInTheLiveRegistry() {
    // Guards both correctness (only a real cache can be cleared) and audit-log integrity: a
    // rejected name is never handed to invalidateAll or to the audit event, so untrusted input
    // can never reach the log format as anything other than a plain, allow-listed cache name.
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "clearCache");
    addQueryParameter(widgetContext, "cache", "NotARealCache\nuser=admin forged-entry");

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      cacheManager.when(CacheManager::getCacheNames).thenReturn(Set.of(CacheManager.OBJECT_CACHE));

      WidgetContext result = new CacheManagementWidget().post(widgetContext);

      cacheManager.verify(() -> CacheManager.invalidateAll(anyString()), never());
      audit.verifyNoInteractions();
      assertEquals("Unknown cache", result.getErrorMessage());
      assertEquals("/admin/cache-management", result.getRedirect());
    }
  }

  @Test
  void postDoesNothingForAUserWithoutAdmin() {
    addQueryParameter(widgetContext, "command", "clearAll");

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      WidgetContext result = new CacheManagementWidget().post(widgetContext);

      assertNull(result.getRedirect());
      cacheManager.verifyNoInteractions();
      audit.verifyNoInteractions();
    }
  }

  @Test
  void postIgnoresAnUnrecognizedCommandButStillRedirects() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "command", "somethingElse");

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      WidgetContext result = new CacheManagementWidget().post(widgetContext);

      cacheManager.verify(CacheManager::invalidateAllCaches, never());
      cacheManager.verify(() -> CacheManager.invalidateAll(anyString()), never());
      audit.verifyNoInteractions();
      assertEquals("/admin/cache-management", result.getRedirect());
    }
  }
}
