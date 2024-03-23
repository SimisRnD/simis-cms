/*
 * Copyright 2024 Matt Rajkowski (https://www.github.com/rajkowski)
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
package com.simisinc.platform.application.filesystem;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;

public class FileSystemCommandTest {

  private static List<SiteProperty> findByPrefix(String uniqueId) {
    List<SiteProperty> systemPropertyList = new ArrayList<>();
    {
      SiteProperty siteProperty = new SiteProperty();
      siteProperty.setLabel("Config Path");
      siteProperty.setName("system.configpath");
      siteProperty.setValue(".");
      siteProperty.setId(1);
      systemPropertyList.add(siteProperty);
    }
    {
      SiteProperty siteProperty = new SiteProperty();
      siteProperty.setLabel("File Path");
      siteProperty.setName("system.filepath");
      siteProperty.setValue(".");
      siteProperty.setId(1);
      systemPropertyList.add(siteProperty);
    }
    return systemPropertyList;
  }

  private LoadingCache<String, List<SiteProperty>> sitePropertyListCache;

  @BeforeEach
  public void init() {
    sitePropertyListCache = Caffeine.newBuilder().build(FileSystemCommandTest::findByPrefix);
  }

  @Test
  void testIsModified() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);
      File testResourcePath = FileSystemCommand.getFileServerConfigPath("src", "test", "resources");
      File file = new File(testResourcePath, "simplelogger.properties");
      Assertions.assertFalse(FileSystemCommand.isModified(file, file.lastModified()));
      Assertions.assertTrue(FileSystemCommand.isModified(file, file.lastModified() - 100));
    }
  }

  @Test
  void testGenerateUniqueFilename() {
    long now = System.currentTimeMillis();
    long id = 321;
    String uniqueId = FileSystemCommand.generateUniqueFilename(id);
    Assertions.assertTrue(uniqueId.endsWith("-" + id));
    long timestamp = Long.parseLong(uniqueId.substring(0, uniqueId.indexOf("-")));
    Assertions.assertTrue(timestamp >= now);
  }

  @Test
  void testGetFileServerConfigPath() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      File testResourcePath = FileSystemCommand.getFileServerConfigPath("src", "test", "resources");
      Assertions.assertTrue(testResourcePath.isDirectory());
      Assertions.assertTrue(testResourcePath.exists());

      File properties = new File(testResourcePath, "simplelogger.properties");
      Assertions.assertTrue(properties.isFile());
      Assertions.assertTrue(properties.exists());
    }
  }

  @Test
  void testGetFileServerConfigPathValue() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      String pathValue = FileSystemCommand.getFileServerConfigPathValue();
      Assertions.assertEquals("." + File.separator, pathValue);

      File path = new File(pathValue);
      Assertions.assertTrue(path.isDirectory());
      Assertions.assertTrue(path.exists());
    }
  }

  @Test
  void testGetFileServerRootPath() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      File testResourcePath = FileSystemCommand.getFileServerRootPath("src", "test", "resources");
      Assertions.assertTrue(testResourcePath.isDirectory());
      Assertions.assertTrue(testResourcePath.exists());

      File properties = new File(testResourcePath, "simplelogger.properties");
      Assertions.assertTrue(properties.isFile());
      Assertions.assertTrue(properties.exists());
    }
  }

  @Test
  void testGetFileServerRootPathValue() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      String pathValue = FileSystemCommand.getFileServerRootPathValue();
      Assertions.assertEquals("." + File.separator, pathValue);

      File path = new File(pathValue);
      Assertions.assertTrue(path.isDirectory());
      Assertions.assertTrue(path.exists());
    }
  }

  @Test
  void testLoadFileToList() {
    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      cacheManager.when(() -> CacheManager.getLoadingCache(anyString())).thenReturn(sitePropertyListCache);

      File testResourcePath = FileSystemCommand.getFileServerConfigPath("src", "test", "resources");
      Assertions.assertTrue(testResourcePath.isDirectory());
      Assertions.assertTrue(testResourcePath.exists());

      File properties = new File(testResourcePath, "simple-list.csv");
      Assertions.assertTrue(properties.isFile());
      Assertions.assertTrue(properties.exists());

      List<String> list = FileSystemCommand.loadFileToList(properties);
      Assertions.assertFalse(list.isEmpty());
      Assertions.assertEquals(2, list.size());
    }
  }
}
