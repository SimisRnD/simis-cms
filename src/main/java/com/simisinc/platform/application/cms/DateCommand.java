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

import com.github.marlonlom.utilities.timeago.TimeAgo;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;

/**
 * Relative date comparison functions
 *
 * @author matt rajkowski
 * @created 4/30/18 8:56 AM
 */
public class DateCommand {

  private static Log LOG = LogFactory.getLog(DateCommand.class);

  public static boolean isAfterNow(Timestamp timestamp) {
    if (timestamp == null) {
      return false;
    }
    return (timestamp.getTime() > System.currentTimeMillis());
  }

  public static boolean isBeforeNow(Timestamp timestamp) {
    if (timestamp == null) {
      return false;
    }
    return (timestamp.getTime() < System.currentTimeMillis());
  }

  public static boolean isMinutesOld(Timestamp timestamp, int minutesToCheck) {
    if (timestamp == null) {
      return false;
    }
    return (timestamp.getTime() < (System.currentTimeMillis() - (minutesToCheck * 60 * 1000)));
  }

  public static boolean isHoursOld(Timestamp timestamp, int hoursToCheck) {
    if (timestamp == null) {
      return false;
    }
    return (timestamp.getTime() < (System.currentTimeMillis() - (hoursToCheck * 60 * 60 * 1000)));
  }

  public static String relative(Timestamp timestamp) {
    if (timestamp == null) {
      return "";
    }
    return TimeAgo.using(timestamp.getTime());
  }

  public static Timestamp addDays(Timestamp timestamp, int days) {
    if (timestamp == null || days == 0) {
      return timestamp;
    }
    return new Timestamp(timestamp.getTime() + (days * 86_500_000L));
  }

}
