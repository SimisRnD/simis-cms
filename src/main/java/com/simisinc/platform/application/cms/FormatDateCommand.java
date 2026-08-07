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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.TimeZone;

/**
 * Formats dates
 *
 * @author matt rajkowski
 * @created 5/25/18 10:00 AM
 */
public class FormatDateCommand {

  private static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter ISO_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter ISO_OFFSET_FORMAT = DateTimeFormatter.ofPattern("xxx");

  private static String[] suffixes =
      {  "0th",  "1st",  "2nd",  "3rd",  "4th",  "5th",  "6th",  "7th",  "8th",  "9th",
          "10th", "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th",
          "20th", "21st", "22nd", "23rd", "24th", "25th", "26th", "27th", "28th", "29th",
          "30th", "31st" };

  private static Log LOG = LogFactory.getLog(FormatDateCommand.class);

  public static String formatMonthDayYear(Timestamp timestamp) {
    // May 25th 2018
    TimeZone siteTimeZone = TimeZone.getTimeZone(getSiteZoneId());
    SimpleDateFormat formatDayOfMonth = new SimpleDateFormat("d");
    formatDayOfMonth.setTimeZone(siteTimeZone);
    int day = Integer.parseInt(formatDayOfMonth.format(timestamp));
    DateFormat dateFormat = new SimpleDateFormat("MMMM '" + suffixes[day] + ",' yyyy");
    dateFormat.setTimeZone(siteTimeZone);
    return dateFormat.format(timestamp);
  }

  public static String formatTime(Timestamp timestamp) {
    // 3:45 pm
    DateFormat timeFormat = new SimpleDateFormat("h:mm a");
    timeFormat.setTimeZone(TimeZone.getTimeZone(getSiteZoneId()));
    return timeFormat.format(timestamp);
  }

  /**
   * The site's configured display timezone (site.timezone), which is what calendar/event dates
   * are meant to be shown in -- falls back to the JVM's own default only if the property is
   * unset, since the server's runtime zone is not guaranteed to match the configured site zone.
   */
  public static ZoneId getSiteZoneId() {
    return ZoneId.of(LoadSitePropertyCommand.loadByName("site.timezone", ZoneId.systemDefault().getId()));
  }

  /**
   * Formats a date as yyyy-MM-dd in the given zone, so the calendar day shown reflects the
   * zone the date is meant to be interpreted in rather than whatever zone the JVM defaults to.
   */
  public static String formatIsoDate(Date date, ZoneId zoneId) {
    return ISO_DATE_FORMAT.format(date.toInstant().atZone(zoneId));
  }

  public static String formatIsoTime(Date date, ZoneId zoneId) {
    return ISO_TIME_FORMAT.format(date.toInstant().atZone(zoneId));
  }

  /**
   * The zone's UTC offset at the given instant (e.g. "-04:00", "+00:00"), accounting for DST.
   */
  public static String formatIsoOffset(Date date, ZoneId zoneId) {
    return ISO_OFFSET_FORMAT.format(date.toInstant().atZone(zoneId));
  }
}
