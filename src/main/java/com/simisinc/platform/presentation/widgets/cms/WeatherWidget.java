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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.weather.NwsForecast;
import com.simisinc.platform.application.weather.NwsForecastCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Displays a National Weather Service forecast for an admin-configured latitude/longitude.
 *
 * @author matt rajkowski
 */
public class WeatherWidget extends GenericWidget {

  static final long serialVersionUID = 2026081700001L;
  private static Log LOG = LogFactory.getLog(WeatherWidget.class);

  static String JSP = "/cms/weather.jsp";

  static final int DEFAULT_PERIOD_COUNT = 4;

  public WidgetContext execute(WidgetContext context) {

    String latitudeValue = context.getPreferences().get("latitude");
    String longitudeValue = context.getPreferences().get("longitude");
    if (StringUtils.isBlank(latitudeValue) || StringUtils.isBlank(longitudeValue)) {
      LOG.debug("Skipping... latitude/longitude preference not set");
      return null;
    }

    double latitude;
    double longitude;
    try {
      latitude = Double.parseDouble(latitudeValue);
      longitude = Double.parseDouble(longitudeValue);
    } catch (NumberFormatException e) {
      LOG.warn("Invalid latitude/longitude preference: " + latitudeValue + ", " + longitudeValue);
      return null;
    }

    NwsForecast forecast = NwsForecastCommand.getForecast(latitude, longitude);
    if (forecast == null) {
      return null;
    }

    int periodCount = DEFAULT_PERIOD_COUNT;
    String periodsValue = context.getPreferences().get("periods");
    if (StringUtils.isNotBlank(periodsValue)) {
      try {
        periodCount = Math.max(1, Integer.parseInt(periodsValue));
      } catch (NumberFormatException e) {
        LOG.debug("Invalid periods preference, using default: " + periodsValue);
      }
    }
    List<?> periods = forecast.getPeriods().size() > periodCount
        ? forecast.getPeriods().subList(0, periodCount)
        : forecast.getPeriods();

    String title = context.getPreferences().get("title");
    if (StringUtils.isBlank(title)) {
      title = StringUtils.isNotBlank(forecast.getLocationName())
          ? "Weather in " + forecast.getLocationName()
          : "Weather";
    }

    context.getRequest().setAttribute("title", title);
    context.getRequest().setAttribute("periods", periods);
    context.setJsp(JSP);
    return context;
  }
}
