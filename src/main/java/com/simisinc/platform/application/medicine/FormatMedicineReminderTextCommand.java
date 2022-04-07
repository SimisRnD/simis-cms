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

package com.simisinc.platform.application.medicine;

import com.simisinc.platform.domain.model.medicine.MedicineReminder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/12/18 9:52 AM
 */
public class FormatMedicineReminderTextCommand {

  private static Log LOG = LogFactory.getLog(FormatMedicineReminderTextCommand.class);

  private static String[] suffixes =
      {"0th", "1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th",
          "10th", "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th",
          "20th", "21st", "22nd", "23rd", "24th", "25th", "26th", "27th", "28th", "29th",
          "30th", "31st"};

  public static String formatDayText(MedicineReminder medicineReminder, ZoneId timezone) {

    // Display the result in the client's timezone
    ZonedDateTime zonedCurrentDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault());
    ZonedDateTime currentDateTime = zonedCurrentDateTime.withZoneSameInstant(timezone);

    ZonedDateTime zonedDateTime = medicineReminder.getReminder().toLocalDateTime().atZone(ZoneId.systemDefault());
    ZonedDateTime reminderDateTime = zonedDateTime.withZoneSameInstant(timezone);

    // Monday (Today), Sep 24th 2018
    String displayName = reminderDateTime.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.US);
    DateTimeFormatter formatDayOfMonth = DateTimeFormatter.ofPattern("d");
    int day = Integer.parseInt(formatDayOfMonth.format(reminderDateTime));
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMM '" + suffixes[day] + ",' yyyy");
    if (reminderDateTime.truncatedTo(ChronoUnit.DAYS).equals(currentDateTime.truncatedTo(ChronoUnit.DAYS))) {
      return displayName + " (Today), " + dateFormat.format(reminderDateTime);
    } else if (reminderDateTime.truncatedTo(ChronoUnit.DAYS).equals(currentDateTime.plusDays(1).truncatedTo(ChronoUnit.DAYS))) {
      return displayName + " (Tomorrow), " + dateFormat.format(reminderDateTime);
    } else {
      return displayName + ", " + dateFormat.format(reminderDateTime);
    }
  }

  public static String formatTimeText(MedicineReminder medicineReminder, ZoneId timezone) {

    // Time comparisons using the server's timezone
    long reminder = medicineReminder.getReminder().getTime();
    long now = System.currentTimeMillis();

    // Display the result in the client's timezone
    ZonedDateTime zonedCurrentDateTime = LocalDateTime.now().atZone(ZoneId.systemDefault());
    ZonedDateTime currentDateTime = zonedCurrentDateTime.withZoneSameInstant(timezone);

    ZonedDateTime zonedDateTime = medicineReminder.getReminder().toLocalDateTime().atZone(ZoneId.systemDefault());
    ZonedDateTime reminderDateTime = zonedDateTime.withZoneSameInstant(timezone);

    // 3:45 pm
    DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("h:mm a").withZone(timezone);
    if (reminderDateTime.truncatedTo(ChronoUnit.DAYS).equals(currentDateTime.truncatedTo(ChronoUnit.DAYS))) {
      if (reminder < (now - (15 * 60 * 1000))) {
        // In the past
        return timeFormat.format(reminderDateTime);
      } else if (reminder < (now + (15 * 60 * 1000))) {
        // Sometime soon (give or take 15 minutes from ow)
        return "Now " + timeFormat.format(reminderDateTime);
      } else {
        // In the future
        return "at " + timeFormat.format(reminderDateTime);
      }
    }
    return "at " + timeFormat.format(reminderDateTime);
  }

  public static String formatLoggedText(MedicineReminder medicineReminder, ZoneId timezone) {
    if (medicineReminder.getLogged() == null) {
      return null;
    }
    // Action at 3:45 pm
    ZonedDateTime zonedDateTime = medicineReminder.getLogged().toLocalDateTime().atZone(ZoneId.systemDefault());
    ZonedDateTime loggedDateTime = zonedDateTime.withZoneSameInstant(timezone);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
    if (medicineReminder.getWasTaken()) {
      return "Taken at " + formatter.format(loggedDateTime);
    } else if (medicineReminder.getWasSkipped()) {
      return "Skipped";
    }
    return null;
  }
}
