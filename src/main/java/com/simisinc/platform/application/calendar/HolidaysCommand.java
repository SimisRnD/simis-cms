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

package com.simisinc.platform.application.calendar;

import com.simisinc.platform.domain.model.cms.Holiday;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/18/21 9:30 AM
 */
public class HolidaysCommand {

  private static Log LOG = LogFactory.getLog(HolidaysCommand.class);

  public static List<Holiday> usHolidays(LocalDate startDate, LocalDate endDate) {

    int startYear = startDate.getYear();
    int endYear = endDate.getYear();

    List<Holiday> holidayList = new ArrayList<>();
    for (int year = startYear; year <= endYear; year++) {

      LocalDate newYearsDay = Holidays.newYearsDay(year);
      LocalDate newYearsDayObserved = Holidays.newYearsDayObserved(year);
      add(holidayList, startDate, endDate, "New Year's Day", newYearsDay);
      if (!newYearsDay.isEqual(newYearsDayObserved) && newYearsDayObserved.getYear() == year) {
        add(holidayList, startDate, endDate, "New Year's Day (observed)", newYearsDayObserved);
      }
      // It's possible that next year's New Year is observed at the end of this year
      LocalDate nextNewYearsDayObserved = Holidays.newYearsDayObserved(year + 1);
      if (nextNewYearsDayObserved.getYear() == year) {
        add(holidayList, startDate, endDate, "New Year's Day (observed)", nextNewYearsDayObserved);
      }

      add(holidayList, startDate, endDate, "Martin Luther King, Jr.'s Birthday", Holidays.martinLutherKingJrObserved(year));

      LocalDate presidentialInaugurationDay = Holidays.presidentialInaugurationDay(year);
      if (presidentialInaugurationDay != null) {
        add(holidayList, startDate, endDate, "Presidential Inauguration Day", presidentialInaugurationDay);
        LocalDate presidentialInaugurationDayHoliday = Holidays.presidentialInaugurationDayHoliday(year);
        if (presidentialInaugurationDayHoliday != null && !presidentialInaugurationDay.isEqual(presidentialInaugurationDayHoliday)) {
          add(holidayList, startDate, endDate, "Presidential Inauguration Day (holiday)", presidentialInaugurationDayHoliday);
        }
      }

//      add(holidayList, startDate, endDate, "Ground Hog Day", Holidays.groundhogDay(year));
//      add(holidayList, startDate, endDate, "Abraham Lincoln's Birthday", Holidays.abrahamLincolnsBirthday(year));
//      add(holidayList, startDate, endDate, "Susan B. Anthony Day", Holidays.susanBAnthonyDay(year));
      add(holidayList, startDate, endDate, "President's Day", Holidays.presidentsDayObserved(year));
//      add(holidayList, startDate, endDate, "St. Patrick's Day", Holidays.saintPatricksDay(year));
//      add(holidayList, startDate, endDate, "Good Friday", Holidays.goodFriday(year));
      add(holidayList, startDate, endDate, "Easter Sunday", Holidays.easterSunday(year));
//      add(holidayList, startDate, endDate, "Cinco de Mayo", Holidays.cincoDeMayo(year));
      add(holidayList, startDate, endDate, "Memorial Day", Holidays.memorialDayObserved(year));

      LocalDate juneteenthNationalIndependenceDay = Holidays.juneteenthNationalIndependenceDay(year);
      if (juneteenthNationalIndependenceDay != null) {
        add(holidayList, startDate, endDate, "Juneteenth", juneteenthNationalIndependenceDay);
        LocalDate juneteenthNationalIndependenceDayObserved = Holidays.juneteenthNationalIndependenceDayObserved(year);
        if (juneteenthNationalIndependenceDayObserved != null && !juneteenthNationalIndependenceDay.isEqual(juneteenthNationalIndependenceDayObserved)) {
          add(holidayList, startDate, endDate, "Juneteenth (observed)", juneteenthNationalIndependenceDayObserved);
        }
      }

      LocalDate independenceDay = Holidays.independenceDay(year);
      add(holidayList, startDate, endDate, "Independence Day", independenceDay);
      LocalDate independenceDayObserved = Holidays.independenceDayObserved(year);
      if (!independenceDay.isEqual(independenceDayObserved)) {
        add(holidayList, startDate, endDate, "Independence Holiday (observed)", independenceDayObserved);
      }

      add(holidayList, startDate, endDate, "Labor Day", Holidays.laborDayObserved(year));
      add(holidayList, startDate, endDate, "Columbus Day", Holidays.columbusDayObserved(year));
//      add(holidayList, startDate, endDate, "Halloween", Holidays.halloween(year));
      add(holidayList, startDate, endDate, "US Election Day", Holidays.usElectionDay(year));

      LocalDate veteransDay = Holidays.veteransDay(year);
      add(holidayList, startDate, endDate, "Veterans Day", veteransDay);
      LocalDate veteransDayObserved = Holidays.veteransDayObserved(year);
      if (!veteransDay.isEqual(veteransDayObserved)) {
        add(holidayList, startDate, endDate, "Veterans Day (observed)", veteransDayObserved);
      }

      add(holidayList, startDate, endDate, "Thanksgiving Day", Holidays.thanksgiving(year));

      LocalDate christmasDay = Holidays.christmasDay(year);
      add(holidayList, startDate, endDate, "Christmas Day", christmasDay);
      LocalDate christmasHolidayObserved = Holidays.christmasHolidayObserved(year);
      if (!christmasDay.isEqual(christmasHolidayObserved)) {
        add(holidayList, startDate, endDate, "Christmas Holiday (observed)", christmasHolidayObserved);
      }
    }
    return holidayList;
  }

  private static void add(List<Holiday> holidayList, LocalDate startDate, LocalDate endDate, String name, LocalDate date) {
    if (date == null) {
      return;
    }
    if ((date.isEqual(startDate) || date.isAfter(startDate)) && (date.isBefore(endDate) || date.isEqual(endDate))) {
      // Add the holiday
      Holiday holiday = new Holiday(name, date);
      holidayList.add(holiday);
    }
  }
}
