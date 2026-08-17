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

import java.util.ArrayList;
import java.util.List;

/**
 * A National Weather Service forecast for one location: a display name (e.g. "Raleigh, NC") and
 * its upcoming forecast periods, in the order NWS returns them.
 *
 * @author matt rajkowski
 */
public class NwsForecast {

  private String locationName = null;
  private List<NwsForecastPeriod> periods = new ArrayList<>();

  public NwsForecast() {
  }

  public String getLocationName() {
    return locationName;
  }

  public void setLocationName(String locationName) {
    this.locationName = locationName;
  }

  public List<NwsForecastPeriod> getPeriods() {
    return periods;
  }

  public void setPeriods(List<NwsForecastPeriod> periods) {
    this.periods = periods;
  }
}
