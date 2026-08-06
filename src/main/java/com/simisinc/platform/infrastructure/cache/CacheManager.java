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

package com.simisinc.platform.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.StylesheetRepository;
import com.simisinc.platform.infrastructure.persistence.cms.TableOfContentsRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebRedirectRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages the available caches
 *
 * @author matt rajkowski
 * @created 5/3/18 12:30 PM
 */
public class CacheManager {

  public static String SYSTEM_PROPERTY_PREFIX_CACHE = "SystemPropertyPrefixCache";
  public static String APP_CACHE = "AppCache";
  public static String USER_CREDENTIALS_CACHE = "UserCredentialsCache";
  public static String STYLESHEET_WEB_PAGE_ID_CACHE = "StylesheetWebPageIdCache";
  public static String CONTENT_UNIQUE_ID_CACHE = "ContentUniqueIdCache";
  public static String CONTENT_REMOTE_URL_CACHE = "ContentRemoteUrlCache";
  public static String COLLECTION_UNIQUE_ID_CACHE = "CollectionUniqueIdCache";
  public static String TABLE_OF_CONTENTS_UNIQUE_ID_CACHE = "TableOfContentsUniqueIdCache";
  public static String WEB_REDIRECT_CACHE = "WebRedirectCache";
  public static String RATE_LIMIT_LOGIN_ATTEMPT_BY_USERNAME_CACHE = "RateLimitLoginAttemptByUsernameCache";
  public static String RATE_LIMIT_ATTEMPT_BY_IP_CACHE = "RateLimitAttemptByIpCache";
  public static String RATE_LIMIT_BY_APP_CACHE = "RateLimitByAppCache";
  public static String RATE_LIMIT_BY_APP_USER_CACHE = "RateLimitByAppUserCache";
  public static String OBJECT_CACHE = "ObjectCache";

  // Object cache keys
  public static final String MENU_TAB_LIST = "MenuTabList";
  public static final String WEBSITE_HEADER = "Header";
  public static final String WEBSITE_PLAIN_HEADER = "PlainHeader";
  public static final String WEBSITE_FOOTER = "Footer";

  private static final Map<String, Cache> cacheManager = new ConcurrentHashMap<>();

  // Tracks the last time each cache was explicitly cleared via #invalidateAll/#invalidateAllCaches,
  // for the admin cache management dashboard's "last cleared" column (issue #463). Intentionally
  // in-memory only and not persisted -- it resets on restart, same as the caches themselves.
  private static final Map<String, Instant> lastClearedAt = new ConcurrentHashMap<>();

  private CacheManager() {
  }

  /**
   * Idempotent: a second call (e.g. a redeploy re-firing the context listener) is a no-op rather than
   * replacing every cache with a fresh, empty one.
   */
  public static synchronized void startup() {

    if (!cacheManager.isEmpty()) {
      return;
    }

    // @todo Menu Tab/Item Cache

    // System Property Cache (prefix = map)
    LoadingCache<String, List<SiteProperty>> sitePropertyListCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(SitePropertyRepository::findAllByPrefix);
    cacheManager.put(SYSTEM_PROPERTY_PREFIX_CACHE, sitePropertyListCache);

    // App Cache (publicKey = app)
    LoadingCache<String, App> appCache = Caffeine.newBuilder()
        .maximumSize(1_000)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(AppRepository::findByPublicKey);
    cacheManager.put(APP_CACHE, appCache);

    // User Credentials Cache (credentials = user id)
    Cache<Long, String> userCredentialsCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterAccess(20, TimeUnit.HOURS)
        .recordStats()
        .build();
    cacheManager.put(USER_CREDENTIALS_CACHE, userCredentialsCache);

    // Stylesheet Cache (webPageId = stylesheet)
    LoadingCache<Long, Stylesheet> stylesheetCache = Caffeine.newBuilder()
        .maximumSize(100)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(StylesheetRepository::findByWebPageId);
    cacheManager.put(STYLESHEET_WEB_PAGE_ID_CACHE, stylesheetCache);

    // Content Cache (contentUniqueId = content)
    LoadingCache<String, Content> contentCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(ContentRepository::findByUniqueId);
    cacheManager.put(CONTENT_UNIQUE_ID_CACHE, contentCache);

    // Remote Content Cache (contentRemoteUrl = remote content)
    Cache<String, Content> remoteContentCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .recordStats()
        .build();
    cacheManager.put(CONTENT_REMOTE_URL_CACHE, remoteContentCache);

    // Collection Unique Id Cache (collectionUniqueId = collection)
    LoadingCache<String, Collection> collectionCache = Caffeine.newBuilder()
        .maximumSize(100)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(CollectionRepository::findByUniqueId);
    cacheManager.put(COLLECTION_UNIQUE_ID_CACHE, collectionCache);

    // Collection Unique Id Cache (collectionUniqueId = collection)
    LoadingCache<String, TableOfContents> tableOfContentsCache = Caffeine.newBuilder()
        .maximumSize(100)
        .recordStats()
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(TableOfContentsRepository::findByUniqueId);
    cacheManager.put(TABLE_OF_CONTENTS_UNIQUE_ID_CACHE, tableOfContentsCache);

    // Web Redirect Cache (fromPath = redirect, enabled or not) (issue #408). A short expireAfterWrite
    // -- rather than the commented-out pattern above -- is used deliberately here: unlike the other
    // uniqueId caches (which rely entirely on their repository's explicit invalidateKey() calls on
    // write), an admin edit that goes through WebRedirectRepository will also invalidate this cache
    // directly, but the TTL is kept as a defense-in-depth backstop (e.g. a direct SQL edit, or a
    // multi-node deployment where only the writing node's cache gets invalidated).
    //
    // The loader is WebRedirectRepository::findByFromPath (not the enabled-only
    // findEnabledByFromPath) so a disabled row is still cached as itself, rather than as a "miss" --
    // WebRequestFilter checks getEnabled() and, critically, treats "a row exists but is disabled" as
    // final (never falls through to the legacy CSV fallback for that path), where treating it as a
    // plain miss would let a disabled admin-managed redirect that shares a from_path with a legacy
    // redirects.csv entry be silently resurrected by that fallback.
    //
    // A genuine miss (no row at all) is wrapped as WebRedirect.NONE rather than returned as null:
    // Caffeine's LoadingCache never caches a null loader result, so without this the overwhelmingly
    // common case -- a request path with no redirect at all -- would hit the database on every single
    // request instead of being served from cache like every other outcome. LoadWebRedirectCommand
    // translates NONE back to null for callers.
    LoadingCache<String, WebRedirect> webRedirectCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build(fromPath -> {
          WebRedirect record = WebRedirectRepository.findByFromPath(fromPath);
          return record != null ? record : WebRedirect.NONE;
        });
    cacheManager.put(WEB_REDIRECT_CACHE, webRedirectCache);

    // Login attempt by username cache
    Cache<String, Object> loginAttemptByUsernameCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .recordStats()
        .build();
    cacheManager.put(RATE_LIMIT_LOGIN_ATTEMPT_BY_USERNAME_CACHE, loginAttemptByUsernameCache);

    // Attempt by IP cache
    Cache<String, Object> accessAttemptByIpCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .recordStats()
        .build();
    cacheManager.put(RATE_LIMIT_ATTEMPT_BY_IP_CACHE, accessAttemptByIpCache);

    // Rate limit by app cache
    Cache<String, Object> rateLimitByAppCache = Caffeine.newBuilder()
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .recordStats()
        .build();
    cacheManager.put(RATE_LIMIT_BY_APP_CACHE, rateLimitByAppCache);

    // Rate limit by app+user cache
    Cache<String, Object> rateLimitByAppUserCache = Caffeine.newBuilder()
        .maximumSize(1_000_000)
        .expireAfterAccess(15, TimeUnit.MINUTES)
        .recordStats()
        .build();
    cacheManager.put(RATE_LIMIT_BY_APP_USER_CACHE, rateLimitByAppUserCache);

    // Generic object cache
    Cache<String, Object> objectCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(24, TimeUnit.HOURS)
        .recordStats()
        .build();
    cacheManager.put(OBJECT_CACHE, objectCache);
  }

  /**
   * Callers (including {@code ContextListener} at webapp startup) are expected to have called
   * {@link #startup()} already; this is a safety net for anything that reaches a cache first --
   * notably the unit test suite, which never runs the servlet lifecycle. Idempotent, so paying this
   * lazily costs nothing once startup has genuinely happened.
   */
  private static void ensureStarted() {
    if (cacheManager.isEmpty()) {
      startup();
    }
  }

  public static Cache getCache(String cacheName) {
    ensureStarted();
    return cacheManager.get(cacheName);
  }

  public static LoadingCache getLoadingCache(String cacheName) {
    ensureStarted();
    return (LoadingCache) cacheManager.get(cacheName);
  }

  public static void invalidateKey(String cacheName, Object key) {
    Cache cache = getCache(cacheName);
    if (cache != null) {
      cache.invalidate(key);
    }
  }

  /**
   * Returns the registry's cache names (issue #463 admin dashboard's inventory). Callers -- notably
   * a mutating admin action -- should validate any user-supplied cache name against this set before
   * acting on it, the same way {@code DatabaseMaintenanceWidget} validates a table name against
   * {@code DatabaseMaintenanceRepository.findTableNames()} before it reaches raw SQL.
   */
  public static Set<String> getCacheNames() {
    ensureStarted();
    return Collections.unmodifiableSet(cacheManager.keySet());
  }

  /**
   * Clears every entry in the named cache without an application restart (issue #463). A no-op if
   * the name isn't registered. Records the clear time for the dashboard's "last cleared" column.
   */
  public static void invalidateAll(String cacheName) {
    Cache cache = getCache(cacheName);
    if (cache == null) {
      return;
    }
    cache.invalidateAll();
    lastClearedAt.put(cacheName, Instant.now());
  }

  /**
   * Clears every entry in every registered cache without an application restart (issue #463).
   */
  public static void invalidateAllCaches() {
    ensureStarted();
    for (String cacheName : cacheManager.keySet()) {
      invalidateAll(cacheName);
    }
  }

  /**
   * The last time {@link #invalidateAll(String)} (directly, or via {@link #invalidateAllCaches()})
   * cleared this cache, or {@code null} if it hasn't been explicitly cleared since this JVM started.
   * In-memory only -- resets on restart along with the caches themselves.
   */
  public static Instant getLastClearedAt(String cacheName) {
    return lastClearedAt.get(cacheName);
  }

  public static void addToObjectCache(String key, Object value) {
    if (value == null) {
      return;
    }
    Cache cache = getCache(OBJECT_CACHE);
    cache.put(key, value);
  }

  public static Object getFromObjectCache(String key) {
    Cache cache = getCache(OBJECT_CACHE);
    return cache.getIfPresent(key);
  }

  public static void invalidateObjectCacheKey(String key) {
    invalidateKey(OBJECT_CACHE, key);
  }
}
