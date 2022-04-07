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

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Formats dates
 *
 * @author matt rajkowski
 * @created 5/25/18 10:00 AM
 */
public class FormatDateCommand {

  private static String[] suffixes =
      {  "0th",  "1st",  "2nd",  "3rd",  "4th",  "5th",  "6th",  "7th",  "8th",  "9th",
          "10th", "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th",
          "20th", "21st", "22nd", "23rd", "24th", "25th", "26th", "27th", "28th", "29th",
          "30th", "31st" };

  private static Log LOG = LogFactory.getLog(FormatDateCommand.class);

  public static String formatMonthDayYear(Timestamp timestamp) {
    // May 25th 2018
    SimpleDateFormat formatDayOfMonth  = new SimpleDateFormat("d");
    int day = Integer.parseInt(formatDayOfMonth.format(timestamp));
    DateFormat dateFormat = new SimpleDateFormat("MMMM '" + suffixes[day] + ",' yyyy");
    return dateFormat.format(timestamp);
  }

  public static String formatTime(Timestamp timestamp) {
    // 3:45 pm
    DateFormat timeFormat = new SimpleDateFormat("h:mm a");
    return timeFormat.format(timestamp);
  }
}
