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

package com.simisinc.platform.application.cms;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Methods for working with server and client time differences
 *
 * @author matt rajkowski
 * @created 9/27/18 4:14 PM
 */
public class TimeCommand {

  private static Log LOG = LogFactory.getLog(TimeCommand.class);

  public static int[] adjustHoursMinutesClientToServer(String time, ZoneId clientTimezone) {
    // Determine the time
    int sepIdx = time.indexOf(':');
    boolean foundAMPM = true;
    int amSepIdx = time.indexOf(' ');
    if (amSepIdx == -1) {
      foundAMPM = false;
      amSepIdx = time.length() - 1;
    }
    String hourValue = time.substring(0, sepIdx);
    int hour = Integer.parseInt(hourValue);
    String minuteValue = time.substring(sepIdx + 1, amSepIdx);
    int minute = Integer.parseInt(minuteValue);

    // Convert to 24-hour time, if needed
    if (foundAMPM) {
      String amPMValue = time.substring(amSepIdx + 1);
      if ("AM".equalsIgnoreCase(amPMValue)) {
        if (hour == 12) {
          hour = 0;
        }
      } else if ("PM".equalsIgnoreCase(amPMValue)) {
        if (hour < 12) {
          hour += 12;
        }
      }
    }

    // Perform client-to-server time change
    LocalDateTime now = LocalDateTime.now();
    ZoneId serverZoneId = ZoneId.systemDefault();
    int hoursAdj = (int) ChronoUnit.HOURS.between(now.atZone(serverZoneId), now.atZone(clientTimezone));
    int minutesAdj = (int) ChronoUnit.MINUTES.between(now.atZone(serverZoneId), now.atZone(clientTimezone)) % 60;
    hour += hoursAdj;
    minute += minutesAdj;
    if (minute < 0) {
      minute += 60;
      hour -= 1;
    } else if (minute > 59) {
      minute -= 60;
      hour += 1;
    }
    if (hour < 0) {
      hour += 24;
    } else if (hour > 23) {
      hour -= 24;
    }
    return new int[]{hour, minute};
  }

  public static int[] adjustHoursMinutesServerToClient(int hour, int minute, ZoneId clientTimezone) {
    // Perform server-to-client time change
    LocalDateTime now = LocalDateTime.now();
    ZoneId serverZoneId = ZoneId.systemDefault();
    int hoursAdj = (int) ChronoUnit.HOURS.between(now.atZone(clientTimezone), now.atZone(serverZoneId));
    int minutesAdj = (int) ChronoUnit.MINUTES.between(now.atZone(clientTimezone), now.atZone(serverZoneId)) % 60;
    hour += hoursAdj;
    minute += minutesAdj;
    if (minute < 0) {
      minute += 60;
      hour -= 1;
    } else if (minute > 59) {
      minute -= 60;
      hour += 1;
    }
    if (hour < 0) {
      hour += 24;
    } else if (hour > 23) {
      hour -= 24;
    }
    return new int[]{hour, minute};
  }
}
