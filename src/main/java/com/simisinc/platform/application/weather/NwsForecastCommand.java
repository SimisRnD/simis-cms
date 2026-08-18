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

import static org.jobrunr.utils.resilience.RateLimiter.SECOND;
import static org.jobrunr.utils.resilience.RateLimiter.Builder.rateLimit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jobrunr.utils.resilience.RateLimiter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.simisinc.platform.ApplicationInfo;
import com.simisinc.platform.application.http.HttpGetCommand;
import com.simisinc.platform.infrastructure.cache.CacheManager;

/**
 * Fetches a forecast from the National Weather Service's public API (api.weather.gov) for a
 * given latitude/longitude. Unlike scraping NWS's consumer-facing forecast page (the approach
 * this replaces, see issue #1279), this uses their documented, structured JSON API -- no HTML
 * markup to break when they redesign a page.
 *
 * <p>NWS's coverage is limited to US territory; a location outside that returns no forecast
 * URL from the /points lookup, which is treated the same as any other failure -- log and
 * return null, since a broken widget instance should never take down the page around it.
 *
 * @author matt rajkowski
 */
public class NwsForecastCommand {

  private static Log LOG = LogFactory.getLog(NwsForecastCommand.class);

  private static final String API_BASE = "https://api.weather.gov";
  private static RateLimiter rateLimit = rateLimit().at1Request().per(SECOND);

  private NwsForecastCommand() {
  }

  /**
   * Returns the cached forecast for this location if present, otherwise fetches it from NWS and
   * caches the result (see {@link CacheManager#WEATHER_FORECAST_CACHE}). Returns null if the
   * coordinates are out of range, NWS has no coverage there, or the request/parse fails for any
   * reason.
   */
  public static NwsForecast getForecast(double latitude, double longitude) {
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      LOG.warn("Invalid latitude/longitude: " + latitude + ", " + longitude);
      return null;
    }

    String cacheKey = formatCacheKey(latitude, longitude);
    Cache<String, NwsForecast> cache = CacheManager.getCache(CacheManager.WEATHER_FORECAST_CACHE);
    NwsForecast cached = cache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }

    NwsForecast forecast = fetchForecast(latitude, longitude);
    if (forecast != null) {
      cache.put(cacheKey, forecast);
    }
    return forecast;
  }

  static String formatCacheKey(double latitude, double longitude) {
    return String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
  }

  private static NwsForecast fetchForecast(double latitude, double longitude) {
    Map<String, String> headers = new HashMap<>();
    headers.put("User-Agent", ApplicationInfo.PRODUCT_NAME + " (" + ApplicationInfo.PRODUCT_URL + ")");
    headers.put("Accept", "application/geo+json");

    try {
      while (!rateLimit.isAllowed()) {
        TimeUnit.MILLISECONDS.sleep(100);
      }

      // Step 1: resolve the lat/lon to NWS's gridpoint-based forecast URL and a display name
      String pointsUrl = API_BASE + "/points/" + formatCacheKey(latitude, longitude);
      String pointsResponse = HttpGetCommand.execute(pointsUrl, headers);
      if (StringUtils.isBlank(pointsResponse)) {
        LOG.warn("No response from NWS points lookup for: " + latitude + ", " + longitude);
        return null;
      }

      ObjectMapper mapper = new ObjectMapper();
      JsonNode pointsProperties = mapper.readTree(pointsResponse).path("properties");
      String forecastUrl = pointsProperties.path("forecast").asText(null);
      if (StringUtils.isBlank(forecastUrl)) {
        LOG.warn("NWS has no forecast coverage for: " + latitude + ", " + longitude);
        return null;
      }
      JsonNode relativeLocation = pointsProperties.path("relativeLocation").path("properties");
      String city = relativeLocation.path("city").asText(null);
      String state = relativeLocation.path("state").asText(null);
      String locationName = StringUtils.isNotBlank(city) && StringUtils.isNotBlank(state)
          ? city + ", " + state
          : null;

      // Step 2: fetch the actual forecast periods
      String forecastResponse = HttpGetCommand.execute(forecastUrl, headers);
      if (StringUtils.isBlank(forecastResponse)) {
        LOG.warn("No response from NWS forecast url: " + forecastUrl);
        return null;
      }
      JsonNode periodsNode = mapper.readTree(forecastResponse).path("properties").path("periods");

      List<NwsForecastPeriod> periods = new ArrayList<>();
      for (JsonNode periodNode : periodsNode) {
        NwsForecastPeriod period = new NwsForecastPeriod();
        period.setName(periodNode.path("name").asText(null));
        period.setTemperature(periodNode.path("temperature").asInt(0));
        period.setTemperatureUnit(periodNode.path("temperatureUnit").asText(null));
        period.setShortForecast(periodNode.path("shortForecast").asText(null));
        period.setIconUrl(periodNode.path("icon").asText(null));
        periods.add(period);
      }
      if (periods.isEmpty()) {
        LOG.warn("NWS forecast had no periods for: " + forecastUrl);
        return null;
      }

      NwsForecast forecast = new NwsForecast();
      forecast.setLocationName(locationName);
      forecast.setPeriods(periods);
      return forecast;
    } catch (Exception e) {
      // Anything could have gone wrong -- network issue, malformed response, NWS outage
      LOG.warn("Could not fetch NWS forecast for " + latitude + ", " + longitude + ": " + e.getMessage());
      return null;
    }
  }
}
