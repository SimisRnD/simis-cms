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

package com.simisinc.platform.application.gis;

import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Functions for working with geospatial distances
 *
 * @author matt rajkowski
 * @created 3/15/2019 12:00 PM
 */
public class GISCommand {

  private static Log LOG = LogFactory.getLog(GISCommand.class);

  private static double deg2rad(double deg) {
    return (deg * Math.PI / 180.0);
  }

  private static double rad2deg(double rad) {
    return (rad * 180 / Math.PI);
  }

  private static double pi = Math.PI / 180;
  private static double xpi = 180 / Math.PI;

  public static double shortestDistance(double lat1, double lon1, double[][] points) {

    LOG.debug("-----------------------");

    double shortestDistance = -1;
    for (double[] point : points) {
      double thisDistance = distanceBetweenPoints(lat1, lon1, point[0], point[1]);

      LOG.debug("Distance: " + thisDistance);

      if (thisDistance > 0) {
        if (shortestDistance == -1 || thisDistance < shortestDistance) {
          shortestDistance = thisDistance;
        }
      }
    }
    return shortestDistance;
  }

  public static double distanceBetweenPoints(double lat1, double lon1, double lat2, double lon2) {
    return distanceBetweenPoints(lat1, lon1, lat2, lon2, "M");
  }

  public static double distanceBetweenPoints(double lat1, double lon1, double lat2, double lon2, String unit) {
    double theta = lon1 - lon2;
    double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
    dist = Math.acos(dist);
    dist = rad2deg(dist);
    // In miles
    dist = dist * 60 * 1.1515;
    if ("K".equals(unit)) {
      // In Kilometers
      dist = dist * 1.609344;
    } else if ("N".equals(unit)) {
      // In Nautical Miles
      dist = dist * 0.8684;
    }
    return (dist);
  }

  /**
   * Determine the center of geometry defined by coordinates
   * https://stackoverflow.com/questions/6671183/calculate-the-center-point-of-multiple-latitude-longitude-coordinate-pairs
   *
   * @param arr
   * @return
   */
  public static Session center(List<Session> arr) {
    if (arr == null || arr.size() == 0) {
      return null;
    }
    if (arr.size() == 1) {
      return arr.get(0);
    }
    double x = 0, y = 0, z = 0;

    for (Session c : arr) {
      double latitude = c.getLatitude() * pi, longitude = c.getLongitude() * pi;
      double cl = Math.cos(latitude);
      x += cl * Math.cos(longitude);
      y += cl * Math.sin(longitude);
      z += Math.sin(latitude);
    }

    int total = arr.size();

    x = x / total;
    y = y / total;
    z = z / total;

    double centralLongitude = Math.atan2(y, x);
    double centralSquareRoot = Math.sqrt(x * x + y * y);
    double centralLatitude = Math.atan2(z, centralSquareRoot);

    return new Session(centralLatitude * xpi, centralLongitude * xpi);
  }

  public static Session centerFromItems(List<Item> arr) {
    if (arr == null || arr.size() == 0) {
      return null;
    }
    if (arr.size() == 1) {
      Item item = arr.get(0);
      return new Session(item.getLatitude(), item.getLongitude());
    }
    double x = 0, y = 0, z = 0;

    for (Item c : arr) {
      double latitude = c.getLatitude() * pi, longitude = c.getLongitude() * pi;
      double cl = Math.cos(latitude);
      x += cl * Math.cos(longitude);
      y += cl * Math.sin(longitude);
      z += Math.sin(latitude);
    }

    int total = arr.size();

    x = x / total;
    y = y / total;
    z = z / total;

    double centralLongitude = Math.atan2(y, x);
    double centralSquareRoot = Math.sqrt(x * x + y * y);
    double centralLatitude = Math.atan2(z, centralSquareRoot);

    return new Session(centralLatitude * xpi, centralLongitude * xpi);
  }

}
