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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class LoadSitePropertyCommandTest {

  private static List<SiteProperty> findByPrefix(String uniqueId) {
    List<SiteProperty> sitePropertyList = new ArrayList<>();
    {
      SiteProperty siteProperty = new SiteProperty();
      siteProperty.setLabel("Name of the site");
      siteProperty.setName("site.name");
      siteProperty.setValue("New Site");
      siteProperty.setId(5);
      sitePropertyList.add(siteProperty);
    }
    {
      SiteProperty siteProperty = new SiteProperty();
      siteProperty.setLabel("Site Url");
      siteProperty.setName("site.url");
      siteProperty.setValue("https://simiscms.com");
      siteProperty.setId(8);
      sitePropertyList.add(siteProperty);
    }
    {
      SiteProperty siteProperty = new SiteProperty();
      siteProperty.setLabel("Header Link Name");
      siteProperty.setName("site.header.link");
      siteProperty.setValue("");
      siteProperty.setId(31);
      sitePropertyList.add(siteProperty);
    }
    return sitePropertyList;
  }

  private LoadingCache<String, List<SiteProperty>> sitePropertyListCache;

  @BeforeEach
  public void init() {
    sitePropertyListCache = Caffeine.newBuilder().build(LoadSitePropertyCommandTest::findByPrefix);
  }

  @Test
  void loadAsMap() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadAsMap("site");

      Assertions.assertEquals(3, sitePropertyMap.size());
      Assertions.assertEquals("New Site", sitePropertyMap.get("site.name"));
      Assertions.assertEquals("https://simiscms.com", sitePropertyMap.get("site.url"));
      Assertions.assertTrue(sitePropertyMap.containsKey("site.header.link"));
    }
  }

  @Test
  void loadAsMapSkipEmpty() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      Map<String, String> sitePropertyMap = LoadSitePropertyCommand.loadNonEmptyAsMap("site");

      Assertions.assertEquals(2, sitePropertyMap.size());
      Assertions.assertEquals("New Site", sitePropertyMap.get("site.name"));
      Assertions.assertEquals("https://simiscms.com", sitePropertyMap.get("site.url"));
      Assertions.assertFalse(sitePropertyMap.containsKey("site.header.link"));
    }
  }

  @Test
  void loadByName() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      String value = LoadSitePropertyCommand.loadByName("site.url");
      Assertions.assertNotNull(value);
      Assertions.assertEquals("https://simiscms.com", value);
    }
  }

  @Test
  void loadByNameWithDefaultValue() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      String value = LoadSitePropertyCommand.loadByName("site.url", "https://example.com");
      Assertions.assertNotNull(value);
      Assertions.assertEquals("https://simiscms.com", value);

      value = LoadSitePropertyCommand.loadByName("site.example", "https://example.com");
      Assertions.assertNotNull(value);
      Assertions.assertEquals("https://example.com", value);
    }
  }
}