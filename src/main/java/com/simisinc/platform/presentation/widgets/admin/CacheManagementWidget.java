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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Admin-only cache management dashboard (issue #463): lists the application's in-memory Caffeine
 * caches with entry counts and hit/miss/eviction stats, and lets an admin clear one cache or all of
 * them without an application restart.
 *
 * <p>
 * Deliberately scoped to inventory + clear-all/clear-one, mirroring {@link DatabaseMaintenanceWidget}
 * (issue #469). TTL/max-size/eviction-policy configuration, clear-by-pattern, and byte-level memory
 * usage are left for a follow-up -- see the issue for why each is out of scope for this slice.
 * </p>
 *
 * @author SimIS
 * @created 8/3/2026
 */
public class CacheManagementWidget extends GenericWidget {

  static final long serialVersionUID = 4361509049183624381L;

  static String JSP = "/admin/cache-management.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    loadDashboardData(context);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    context.getUserSession().renewFormToken();

    String command = context.getParameter("command");
    if ("clearAll".equals(command)) {
      CacheManager.invalidateAllCaches();
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "cache.clear_all",
          AuditEventCommand.SUCCESS, "cache", null, null, "All caches were cleared");
      context.setSuccessMessage("All caches were cleared");
    } else if ("clearCache".equals(command)) {
      String cacheName = context.getParameter("cache");
      // The requested name is validated against the live registry before it's used anywhere,
      // including in the audit log entry below -- so only a known, fixed cache name is ever
      // recorded, the same guard DatabaseMaintenanceWidget uses for a VACUUM table target.
      Set<String> knownCaches = CacheManager.getCacheNames();
      if (cacheName == null || !knownCaches.contains(cacheName)) {
        context.setErrorMessage("Unknown cache");
      } else {
        CacheManager.invalidateAll(cacheName);
        AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "cache.clear",
            AuditEventCommand.SUCCESS, "cache", cacheName, cacheName, "Cache cleared: " + cacheName);
        context.setSuccessMessage("Cache cleared: " + cacheName);
      }
    }

    context.setRedirect("/admin/cache-management");
    return context;
  }

  private void loadDashboardData(WidgetContext context) {
    context.getRequest().setAttribute("cacheSummaryList", buildCacheSummaryList());

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
  }

  /** Alphabetical by cache name, so the dashboard's row order doesn't shift between page loads. */
  private List<CacheSummary> buildCacheSummaryList() {
    List<String> cacheNames = new ArrayList<>(CacheManager.getCacheNames());
    Collections.sort(cacheNames);

    List<CacheSummary> results = new ArrayList<>();
    for (String cacheName : cacheNames) {
      Cache cache = CacheManager.getCache(cacheName);
      if (cache == null) {
        continue;
      }
      CacheStats stats = cache.stats();
      Instant lastClearedAt = CacheManager.getLastClearedAt(cacheName);
      results.add(new CacheSummary(
          cacheName,
          cache.estimatedSize(),
          stats.hitCount(),
          stats.missCount(),
          stats.hitRate(),
          stats.missRate(),
          stats.evictionCount(),
          lastClearedAt == null ? null : Timestamp.from(lastClearedAt)));
    }
    return results;
  }

  public static class CacheSummary {
    private final String name;
    private final long estimatedSize;
    private final long hitCount;
    private final long missCount;
    private final double hitRate;
    private final double missRate;
    private final long evictionCount;
    private final Timestamp lastClearedAt;

    public CacheSummary(String name, long estimatedSize, long hitCount, long missCount, double hitRate,
        double missRate, long evictionCount, Timestamp lastClearedAt) {
      this.name = name;
      this.estimatedSize = estimatedSize;
      this.hitCount = hitCount;
      this.missCount = missCount;
      this.hitRate = hitRate;
      this.missRate = missRate;
      this.evictionCount = evictionCount;
      this.lastClearedAt = lastClearedAt;
    }

    public String getName() {
      return name;
    }

    public long getEstimatedSize() {
      return estimatedSize;
    }

    public long getHitCount() {
      return hitCount;
    }

    public long getMissCount() {
      return missCount;
    }

    public double getHitRate() {
      return hitRate;
    }

    public double getMissRate() {
      return missRate;
    }

    public long getEvictionCount() {
      return evictionCount;
    }

    public Timestamp getLastClearedAt() {
      return lastClearedAt;
    }

    public boolean isNeverCleared() {
      return lastClearedAt == null;
    }
  }
}
