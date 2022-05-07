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
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.StylesheetRepository;
import com.simisinc.platform.infrastructure.persistence.cms.TableOfContentsRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
  public static String LOGIN_ATTEMPT_BY_USERNAME_CACHE = "LoginAttemptByUsernameCache";
  public static String LOGIN_ATTEMPT_BY_IP_CACHE = "LoginAttemptByIpCache";
  public static String OBJECT_CACHE = "ObjectCache";

  // Object cache keys
  public static final String MENU_TAB_LIST = "MenuTabList";
  public static final String WEBSITE_HEADER = "Header";
  public static final String WEBSITE_PLAIN_HEADER = "PlainHeader";
  public static final String WEBSITE_FOOTER = "Footer";

  private static Map<String, Cache> cacheManager = new HashMap<>();

  private CacheManager() {
  }

  public static void startup() {

    // @todo Menu Tab/Item Cache

    // System Property Cache (prefix = map)
    LoadingCache<String, List<SiteProperty>> sitePropertyListCache = Caffeine.newBuilder()
        .maximumSize(1_000)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(SitePropertyRepository::findAllByPrefix);
    cacheManager.put(SYSTEM_PROPERTY_PREFIX_CACHE, sitePropertyListCache);

    // App Cache (publicKey = app)
    LoadingCache<String, App> appCache = Caffeine.newBuilder()
        .maximumSize(50)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(AppRepository::findByPublicKey);
    cacheManager.put(APP_CACHE, appCache);

    // User Credentials Cache (credentials = user id)
    Cache<Long, String> userCredentialsCache = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(20, TimeUnit.HOURS)
        .build();
    cacheManager.put(USER_CREDENTIALS_CACHE, userCredentialsCache);

    // Stylesheet Cache (webPageId = stylesheet)
    LoadingCache<Long, Stylesheet> stylesheetCache = Caffeine.newBuilder()
        .maximumSize(100)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(StylesheetRepository::findByWebPageId);
    cacheManager.put(STYLESHEET_WEB_PAGE_ID_CACHE, stylesheetCache);

    // Content Cache (contentUniqueId = content)
    LoadingCache<String, Content> contentCache = Caffeine.newBuilder()
        .maximumSize(10_000)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(ContentRepository::findByUniqueId);
    cacheManager.put(CONTENT_UNIQUE_ID_CACHE, contentCache);

    // Remote Content Cache (contentRemoteUrl = remote content)
    Cache<String, Content> remoteContentCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build();
    cacheManager.put(CONTENT_REMOTE_URL_CACHE, remoteContentCache);

    // Collection Unique Id Cache (collectionUniqueId = collection)
    LoadingCache<String, Collection> collectionCache = Caffeine.newBuilder()
        .maximumSize(100)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(CollectionRepository::findByUniqueId);
    cacheManager.put(COLLECTION_UNIQUE_ID_CACHE, collectionCache);

    // Collection Unique Id Cache (collectionUniqueId = collection)
    LoadingCache<String, TableOfContents> tableOfContentsCache = Caffeine.newBuilder()
        .maximumSize(100)
//        .expireAfterWrite(5, TimeUnit.MINUTES)
//        .refreshAfterWrite(1, TimeUnit.MINUTES)
        .build(TableOfContentsRepository::findByUniqueId);
    cacheManager.put(TABLE_OF_CONTENTS_UNIQUE_ID_CACHE, tableOfContentsCache);

    // Login attempt by username cache
    Cache<String, Object> loginAttemptByUsernameCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();
    cacheManager.put(LOGIN_ATTEMPT_BY_USERNAME_CACHE, loginAttemptByUsernameCache);

    // Login attempt by IP cache
    Cache<String, Object> loginAttemptByIpCache = Caffeine.newBuilder()
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();
    cacheManager.put(LOGIN_ATTEMPT_BY_IP_CACHE, loginAttemptByIpCache);

    // Generic object cache
    Cache<String, Object> objectCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(24, TimeUnit.HOURS)
        .build();
    cacheManager.put(OBJECT_CACHE, objectCache);
  }

  public static Cache getCache(String cacheName) {
    return cacheManager.get(cacheName);
  }

  public static LoadingCache getLoadingCache(String cacheName) {
    return (LoadingCache) cacheManager.get(cacheName);
  }

  public static void invalidateKey(String cacheName, Object key) {
    Cache cache = cacheManager.get(cacheName);
    if (cache != null) {
      cache.invalidate(key);
    }
  }

  public static void addToObjectCache(String key, Object value) {
    if (value == null) {
      return;
    }
    Cache cache = cacheManager.get(OBJECT_CACHE);
    cache.put(key, value);
  }

  public static Object getFromObjectCache(String key) {
    Cache cache = cacheManager.get(OBJECT_CACHE);
    return cache.getIfPresent(key);
  }

  public static void invalidateObjectCacheKey(String key) {
    invalidateKey(OBJECT_CACHE, key);
  }
}
