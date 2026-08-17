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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.weather.NwsForecast;
import com.simisinc.platform.application.weather.NwsForecastCommand;
import com.simisinc.platform.application.weather.NwsForecastPeriod;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Tests for WeatherWidget.
 *
 * @author matt rajkowski
 */
class WeatherWidgetTest extends WidgetBase {

  private static NwsForecast forecastWithPeriods(String locationName, int periodCount) {
    NwsForecast forecast = new NwsForecast();
    forecast.setLocationName(locationName);
    for (int i = 0; i < periodCount; i++) {
      NwsForecastPeriod period = new NwsForecastPeriod();
      period.setName("Period " + i);
      forecast.getPeriods().add(period);
    }
    return forecast;
  }

  @Test
  void executeReturnsNullWhenLatitudeOrLongitudeIsMissing() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "35.7796");
    // longitude intentionally not set
    widgetContext.setPreferences(preferences);

    WidgetContext result = new WeatherWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeReturnsNullWhenLatitudeIsNotANumber() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "not-a-number");
    preferences.put("longitude", "-78.6382");
    widgetContext.setPreferences(preferences);

    try (MockedStatic<NwsForecastCommand> forecastCommand = mockStatic(NwsForecastCommand.class)) {
      WidgetContext result = new WeatherWidget().execute(widgetContext);

      assertNull(result);
      forecastCommand.verifyNoInteractions();
    }
  }

  @Test
  void executeReturnsNullWhenNoForecastIsAvailable() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "35.7796");
    preferences.put("longitude", "-78.6382");
    widgetContext.setPreferences(preferences);

    try (MockedStatic<NwsForecastCommand> forecastCommand = mockStatic(NwsForecastCommand.class)) {
      forecastCommand.when(() -> NwsForecastCommand.getForecast(anyDouble(), anyDouble())).thenReturn(null);

      WidgetContext result = new WeatherWidget().execute(widgetContext);

      assertNull(result);
    }
  }

  @Test
  void executeSetsTheJspAndDefaultsTheTitleToTheLocationName() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "35.7796");
    preferences.put("longitude", "-78.6382");
    widgetContext.setPreferences(preferences);

    try (MockedStatic<NwsForecastCommand> forecastCommand = mockStatic(NwsForecastCommand.class)) {
      forecastCommand.when(() -> NwsForecastCommand.getForecast(35.7796, -78.6382))
          .thenReturn(forecastWithPeriods("Raleigh, NC", 6));

      WidgetContext result = new WeatherWidget().execute(widgetContext);

      assertEquals(WeatherWidget.JSP, result.getJsp());
      assertEquals("Weather in Raleigh, NC", request.getAttribute("title"));
      // Defaults to showing 4 periods even though the forecast has 6
      assertEquals(4, ((List<?>) request.getAttribute("periods")).size());
    }
  }

  @Test
  void executeRespectsAConfiguredPeriodsCount() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "35.7796");
    preferences.put("longitude", "-78.6382");
    preferences.put("periods", "2");
    widgetContext.setPreferences(preferences);

    try (MockedStatic<NwsForecastCommand> forecastCommand = mockStatic(NwsForecastCommand.class)) {
      forecastCommand.when(() -> NwsForecastCommand.getForecast(35.7796, -78.6382))
          .thenReturn(forecastWithPeriods("Raleigh, NC", 6));

      new WeatherWidget().execute(widgetContext);

      assertEquals(2, ((List<?>) request.getAttribute("periods")).size());
    }
  }

  @Test
  void executeUsesACustomTitleWhenProvidedInsteadOfTheLocationName() {
    Map<String, String> preferences = new HashMap<>();
    preferences.put("latitude", "35.7796");
    preferences.put("longitude", "-78.6382");
    preferences.put("title", "Local Forecast");
    widgetContext.setPreferences(preferences);

    try (MockedStatic<NwsForecastCommand> forecastCommand = mockStatic(NwsForecastCommand.class)) {
      forecastCommand.when(() -> NwsForecastCommand.getForecast(35.7796, -78.6382))
          .thenReturn(forecastWithPeriods("Raleigh, NC", 3));

      new WeatherWidget().execute(widgetContext);

      assertEquals("Local Forecast", request.getAttribute("title"));
    }
  }
}
