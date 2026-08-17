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

package com.simisinc.platform.application.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.infrastructure.cache.CacheManager;

/**
 * Tests for NwsForecastCommand's National Weather Service integration.
 *
 * @author matt rajkowski
 */
class NwsForecastCommandTest {

  private static final String POINTS_URL = "https://api.weather.gov/points/35.7796,-78.6382";
  private static final String FORECAST_URL = "https://api.weather.gov/gridpoints/RAH/75,57/forecast";

  private static final String POINTS_RESPONSE = "{"
      + "\"properties\":{"
      + "\"forecast\":\"" + FORECAST_URL + "\","
      + "\"relativeLocation\":{\"properties\":{\"city\":\"Raleigh\",\"state\":\"NC\"}}"
      + "}}";

  private static final String FORECAST_RESPONSE = "{"
      + "\"properties\":{\"periods\":["
      + "{\"name\":\"This Afternoon\",\"temperature\":85,\"temperatureUnit\":\"F\","
      + "\"shortForecast\":\"Sunny\",\"icon\":\"https://api.weather.gov/icons/land/day/skc?size=medium\"},"
      + "{\"name\":\"Tonight\",\"temperature\":65,\"temperatureUnit\":\"F\","
      + "\"shortForecast\":\"Clear\",\"icon\":\"https://api.weather.gov/icons/land/night/skc?size=medium\"}"
      + "]}}";

  @Test
  void fetchesAndCachesAForecastOnACacheMiss() {
    Cache<String, NwsForecast> cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE)).thenReturn(cache);
      httpGet.when(() -> HttpGetCommand.execute(eq(POINTS_URL), anyMap())).thenReturn(POINTS_RESPONSE);
      httpGet.when(() -> HttpGetCommand.execute(eq(FORECAST_URL), anyMap())).thenReturn(FORECAST_RESPONSE);

      NwsForecast forecast = NwsForecastCommand.getForecast(35.7796, -78.6382);

      assertNotNull(forecast);
      assertEquals("Raleigh, NC", forecast.getLocationName());
      assertEquals(2, forecast.getPeriods().size());
      assertEquals("This Afternoon", forecast.getPeriods().get(0).getName());
      assertEquals(85, forecast.getPeriods().get(0).getTemperature());
      assertEquals("F", forecast.getPeriods().get(0).getTemperatureUnit());
      assertEquals("Sunny", forecast.getPeriods().get(0).getShortForecast());
      assertEquals("https://api.weather.gov/icons/land/day/skc?size=medium", forecast.getPeriods().get(0).getIconUrl());

      verify(cache).put(eq("35.7796,-78.6382"), any());
    }
  }

  @Test
  void returnsTheCachedForecastWithoutFetchingWhenAlreadyCached() {
    NwsForecast cachedForecast = new NwsForecast();
    cachedForecast.setLocationName("Raleigh, NC");

    Cache<String, NwsForecast> cache = mock(Cache.class);
    when(cache.getIfPresent("35.7796,-78.6382")).thenReturn(cachedForecast);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE)).thenReturn(cache);

      NwsForecast forecast = NwsForecastCommand.getForecast(35.7796, -78.6382);

      assertEquals(cachedForecast, forecast);
      httpGet.verify(() -> HttpGetCommand.execute(any(), anyMap()), never());
    }
  }

  @Test
  void returnsNullForOutOfRangeCoordinatesWithoutMakingARequest() {
    try (MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      assertNull(NwsForecastCommand.getForecast(200, -78.6382));
      assertNull(NwsForecastCommand.getForecast(35.7796, -200));
      httpGet.verify(() -> HttpGetCommand.execute(any(), anyMap()), never());
    }
  }

  @Test
  void returnsNullWhenThePointsLookupFails() {
    Cache<String, NwsForecast> cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE)).thenReturn(cache);
      httpGet.when(() -> HttpGetCommand.execute(eq(POINTS_URL), anyMap())).thenReturn(null);

      assertNull(NwsForecastCommand.getForecast(35.7796, -78.6382));
      verify(cache, never()).put(any(), any());
    }
  }

  @Test
  void returnsNullWhenNwsHasNoCoverageForTheLocation() {
    // e.g. a location outside the US -- NWS's points response has no "forecast" property
    String noCoverageResponse = "{\"properties\":{}}";

    Cache<String, NwsForecast> cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE)).thenReturn(cache);
      httpGet.when(() -> HttpGetCommand.execute(eq(POINTS_URL), anyMap())).thenReturn(noCoverageResponse);

      assertNull(NwsForecastCommand.getForecast(35.7796, -78.6382));
      verify(cache, never()).put(any(), any());
    }
  }

  @Test
  void returnsNullWhenTheForecastFetchFailsAfterAGoodPointsLookup() {
    Cache<String, NwsForecast> cache = mock(Cache.class);
    when(cache.getIfPresent(any())).thenReturn(null);

    try (MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class);
        MockedStatic<HttpGetCommand> httpGet = mockStatic(HttpGetCommand.class)) {
      cacheManager.when(() -> CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE)).thenReturn(cache);
      httpGet.when(() -> HttpGetCommand.execute(eq(POINTS_URL), anyMap())).thenReturn(POINTS_RESPONSE);
      httpGet.when(() -> HttpGetCommand.execute(eq(FORECAST_URL), anyMap())).thenReturn(null);

      assertNull(NwsForecastCommand.getForecast(35.7796, -78.6382));
      verify(cache, never()).put(any(), any());
    }
  }

  @Test
  void formatCacheKeyIsConsistentlyFormattedRegardlessOfInputPrecision() {
    assertEquals("35.7796,-78.6382", NwsForecastCommand.formatCacheKey(35.7796, -78.6382));
    assertEquals("35.0000,-78.0000", NwsForecastCommand.formatCacheKey(35.0, -78.0));
  }
}
