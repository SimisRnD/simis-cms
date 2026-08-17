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

/**
 * A single forecast period (e.g. "This Afternoon", "Tonight") from the National Weather Service.
 *
 * @author matt rajkowski
 */
public class NwsForecastPeriod {

  private String name = null;
  private int temperature = 0;
  private String temperatureUnit = null;
  private String shortForecast = null;
  private String iconUrl = null;

  public NwsForecastPeriod() {
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public int getTemperature() {
    return temperature;
  }

  public void setTemperature(int temperature) {
    this.temperature = temperature;
  }

  public String getTemperatureUnit() {
    return temperatureUnit;
  }

  public void setTemperatureUnit(String temperatureUnit) {
    this.temperatureUnit = temperatureUnit;
  }

  public String getShortForecast() {
    return shortForecast;
  }

  public void setShortForecast(String shortForecast) {
    this.shortForecast = shortForecast;
  }

  public String getIconUrl() {
    return iconUrl;
  }

  public void setIconUrl(String iconUrl) {
    this.iconUrl = iconUrl;
  }
}
