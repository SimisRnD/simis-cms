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

package com.simisinc.platform.application.gis.portsmouth;

import com.simisinc.platform.application.gis.GISCommand;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geojson.Feature;

import java.text.DecimalFormat;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/15/2019 12:00 PM
 */
public class PopulatePortsmouthDataCommand {

  // These are values in Portsmouth, Virginia
  private static final double[] airportCoordinates = new double[]{36.897656, -76.215172};
  private static final double[] hospitalCoordinates = new double[]{36.836894, -76.347268};
  private static final double[] navalHospitalCoordinates = new double[]{36.842501, -76.309739};
  private static final double[][] interstateData = {
      {36.830451, -76.305639},
      {36.826875, -76.325243},
      {36.822067, -76.334387},
      {36.815439, -76.347768},
      {36.807985, -76.356708},
      {36.799452, -76.377894},
      {36.771982, -76.365007},
      {36.758592, -76.344598},
      {36.8676, -76.432218},
      {36.867038, -76.432448},
      {36.848435, -76.433199},
      {36.819271, -76.432467}
  };
  private static final double[][] portData = {
      {36.851947, -76.327403},
      {36.86993, -76.360809}
  };

  private static Log LOG = LogFactory.getLog(PopulatePortsmouthDataCommand.class);

  public static void populateFeatureData(Feature feature) {

    // Determine closest interstate, port, airport, hospital, naval hospital
    if (feature.getProperty("latitude") == null && feature.getProperty("longitude") == null) {
      return;
    }
    double latitude = Double.parseDouble(feature.getProperty("latitude"));
    double longitude = Double.parseDouble(feature.getProperty("longitude"));
    if (latitude == 0 || longitude == 0) {
      return;
    }

    // Update the distances
    feature.setProperty("airportDistance", round(GISCommand.distanceBetweenPoints(latitude, longitude, airportCoordinates[0], airportCoordinates[1])) + " mi");
    feature.setProperty("hospitalDistance", round(GISCommand.distanceBetweenPoints(latitude, longitude, hospitalCoordinates[0], hospitalCoordinates[1])) + " mi");
    feature.setProperty("navalHospitalDistance", round(GISCommand.distanceBetweenPoints(latitude, longitude, navalHospitalCoordinates[0], navalHospitalCoordinates[1])) + " mi");

    double interstateDistance = GISCommand.shortestDistance(latitude, longitude, interstateData);
    if (interstateDistance != -1) {
      feature.setProperty("interstateDistance", round(interstateDistance) + " mi");
    }

    double portDistance = GISCommand.shortestDistance(latitude, longitude, portData);
    if (portDistance != -1) {
      feature.setProperty("portDistance", round(portDistance) + " mi");
    }
  }

  private static String round(double number) {
    DecimalFormat decimalFormat = new DecimalFormat("0.#");
    return decimalFormat.format(number);
  }

}
