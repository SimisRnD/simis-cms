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

package com.simisinc.platform.presentation.widgets.calendar;

import com.simisinc.platform.application.calendar.HolidaysCommand;
import com.simisinc.platform.application.cms.LocalDateCommand;
import com.simisinc.platform.application.json.JsonCommand;
import com.simisinc.platform.domain.model.cms.Holiday;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Retrieves a US Holiday calendar and provides a JSON response
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class CalendarAjaxHolidays {

  private static Log LOG = LogFactory.getLog(CalendarAjaxHolidays.class);

  protected static void addHolidays(Date startDate, Date endDate, StringBuilder sb) {
    // Add holidays
    LocalDate startLocalDate = LocalDateCommand.convertToLocalDate(startDate);
    LocalDate endLocalDate = LocalDateCommand.convertToLocalDate(endDate);

    List<Holiday> holidayList = HolidaysCommand.usHolidays(startLocalDate, endLocalDate);
    int holidayId = 0;
    for (Holiday holiday : holidayList) {
      --holidayId;
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("{");
      sb.append("\"id\":").append(holidayId).append(",");
      sb.append("\"uniqueId\":\"").append(JsonCommand.toJson("holiday" + holidayId)).append("\",");
      sb.append("\"allDay\":").append("true").append(",");
      sb.append("\"start\":\"").append(holiday.getDate().toString()).append("\",");
      sb.append("\"end\":\"").append(holiday.getDate().toString()).append("T24:00").append("\",");
      sb.append("\"title\":\"").append(JsonCommand.toJson(holiday.getName())).append("\"");
      sb.append("}");
    }
  }
}
