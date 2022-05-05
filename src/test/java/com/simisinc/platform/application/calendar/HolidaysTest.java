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
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for holidays
 *
 * @author matt rajkowski
 * @created 12/8/2020 1:46 PM
 */
public class HolidaysTest {

  @Test
  void dateBased() {
    LocalDate groundhogDay2021 = Holidays.groundhogDay(2021);
    assertEquals(Month.FEBRUARY, groundhogDay2021.getMonth());
    assertEquals(2, groundhogDay2021.getDayOfMonth());
    assertEquals(2021, groundhogDay2021.getYear());
    assertEquals(DayOfWeek.TUESDAY, groundhogDay2021.getDayOfWeek());

    LocalDate abrahamLincolnsBirthday2021 = Holidays.abrahamLincolnsBirthday(2021);
    assertEquals(Month.FEBRUARY, abrahamLincolnsBirthday2021.getMonth());
    assertEquals(12, abrahamLincolnsBirthday2021.getDayOfMonth());
    assertEquals(2021, abrahamLincolnsBirthday2021.getYear());
    assertEquals(DayOfWeek.FRIDAY, abrahamLincolnsBirthday2021.getDayOfWeek());

    LocalDate valentinesDay2021 = Holidays.valentinesDay(2021);
    assertEquals(Month.FEBRUARY, valentinesDay2021.getMonth());
    assertEquals(14, valentinesDay2021.getDayOfMonth());
    assertEquals(2021, valentinesDay2021.getYear());
    assertEquals(DayOfWeek.SUNDAY, valentinesDay2021.getDayOfWeek());

    LocalDate susanBAnthonyDay2021 = Holidays.susanBAnthonyDay(2021);
    assertEquals(Month.FEBRUARY, susanBAnthonyDay2021.getMonth());
    assertEquals(15, susanBAnthonyDay2021.getDayOfMonth());
    assertEquals(2021, susanBAnthonyDay2021.getYear());
    assertEquals(DayOfWeek.MONDAY, susanBAnthonyDay2021.getDayOfWeek());

    LocalDate saintPatricksDay2021 = Holidays.saintPatricksDay(2021);
    assertEquals(Month.MARCH, saintPatricksDay2021.getMonth());
    assertEquals(17, saintPatricksDay2021.getDayOfMonth());
    assertEquals(2021, saintPatricksDay2021.getYear());
    assertEquals(DayOfWeek.WEDNESDAY, saintPatricksDay2021.getDayOfWeek());

    LocalDate cincoDeMayo2021 = Holidays.cincoDeMayo(2021);
    assertEquals(Month.MAY, cincoDeMayo2021.getMonth());
    assertEquals(5, cincoDeMayo2021.getDayOfMonth());
    assertEquals(2021, cincoDeMayo2021.getYear());
    assertEquals(DayOfWeek.WEDNESDAY, cincoDeMayo2021.getDayOfWeek());

    LocalDate independenceDay2021 = Holidays.independenceDay(2021);
    assertEquals(Month.JULY, independenceDay2021.getMonth());
    assertEquals(4, independenceDay2021.getDayOfMonth());
    assertEquals(2021, independenceDay2021.getYear());
    assertEquals(DayOfWeek.SUNDAY, independenceDay2021.getDayOfWeek());

    LocalDate halloween2021 = Holidays.halloween(2021);
    assertEquals(Month.OCTOBER, halloween2021.getMonth());
    assertEquals(31, halloween2021.getDayOfMonth());
    assertEquals(2021, halloween2021.getYear());
    assertEquals(DayOfWeek.SUNDAY, halloween2021.getDayOfWeek());

    LocalDate veteransDay2021 = Holidays.veteransDay(2021);
    assertEquals(Month.NOVEMBER, veteransDay2021.getMonth());
    assertEquals(11, veteransDay2021.getDayOfMonth());
    assertEquals(2021, veteransDay2021.getYear());
    assertEquals(DayOfWeek.THURSDAY, veteransDay2021.getDayOfWeek());

    LocalDate christmasDay2021 = Holidays.christmasDay(2021);
    assertEquals(Month.DECEMBER, christmasDay2021.getMonth());
    assertEquals(25, christmasDay2021.getDayOfMonth());
    assertEquals(2021, christmasDay2021.getYear());
    assertEquals(DayOfWeek.SATURDAY, christmasDay2021.getDayOfWeek());
  }

  @Test
  void newYears() {
    LocalDate newYears2020 = Holidays.newYearsDay(2020);
    assertEquals(Month.JANUARY, newYears2020.getMonth());
    assertEquals(1, newYears2020.getDayOfMonth());
    assertEquals(2020, newYears2020.getYear());
    assertEquals(DayOfWeek.WEDNESDAY, newYears2020.getDayOfWeek());

    LocalDate newYears2021 = Holidays.newYearsDay(2021);
    assertEquals(Month.JANUARY, newYears2021.getMonth());
    assertEquals(1, newYears2021.getDayOfMonth());
    assertEquals(2021, newYears2021.getYear());
    assertEquals(DayOfWeek.FRIDAY, newYears2021.getDayOfWeek());

    LocalDate newYears2022 = Holidays.newYearsDay(2022);
    assertEquals(Month.JANUARY, newYears2022.getMonth());
    assertEquals(1, newYears2022.getDayOfMonth());
    assertEquals(2022, newYears2022.getYear());
    assertEquals(DayOfWeek.SATURDAY, newYears2022.getDayOfWeek());
  }

  @Test
  void newYearsDayObserved() {
    LocalDate newYears2020 = Holidays.newYearsDayObserved(2020);
    assertEquals(Month.JANUARY, newYears2020.getMonth());
    assertEquals(1, newYears2020.getDayOfMonth());
    assertEquals(2020, newYears2020.getYear());
    assertEquals(DayOfWeek.WEDNESDAY, newYears2020.getDayOfWeek());

    LocalDate newYears2021 = Holidays.newYearsDayObserved(2021);
    assertEquals(Month.JANUARY, newYears2021.getMonth());
    assertEquals(1, newYears2021.getDayOfMonth());
    assertEquals(2021, newYears2021.getYear());
    assertEquals(DayOfWeek.FRIDAY, newYears2021.getDayOfWeek());

    LocalDate newYears2022 = Holidays.newYearsDayObserved(2022);
    assertEquals(Month.DECEMBER, newYears2022.getMonth());
    assertEquals(31, newYears2022.getDayOfMonth());
    assertEquals(2021, newYears2022.getYear());
    assertEquals(DayOfWeek.FRIDAY, newYears2022.getDayOfWeek());
  }

  @Test
  void presidentsDayObserved() {
    LocalDate presidentsDayObserved2021 = Holidays.presidentsDayObserved(2021);
    assertEquals(Month.FEBRUARY, presidentsDayObserved2021.getMonth());
    assertEquals(15, presidentsDayObserved2021.getDayOfMonth());
    assertEquals(2021, presidentsDayObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, presidentsDayObserved2021.getDayOfWeek());
  }

  @Test
  void martinLutherKingObserved() {
    LocalDate martinLutherKingJrObserved2021 = Holidays.martinLutherKingJrObserved(2021);
    assertEquals(Month.JANUARY, martinLutherKingJrObserved2021.getMonth());
    assertEquals(18, martinLutherKingJrObserved2021.getDayOfMonth());
    assertEquals(2021, martinLutherKingJrObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, martinLutherKingJrObserved2021.getDayOfWeek());
  }

  @Test
  void presidentialInaugurationDay() {
    LocalDate presidentialInaugurationDay2020 = Holidays.presidentialInaugurationDay(2020);
    assertNull(presidentialInaugurationDay2020);
    LocalDate presidentialInaugurationDay2021 = Holidays.presidentialInaugurationDay(2021);
    assertNotNull(presidentialInaugurationDay2021);
    assertEquals(Month.JANUARY, presidentialInaugurationDay2021.getMonth());
    assertEquals(20, presidentialInaugurationDay2021.getDayOfMonth());
    assertEquals(2021, presidentialInaugurationDay2021.getYear());
    assertEquals(DayOfWeek.WEDNESDAY, presidentialInaugurationDay2021.getDayOfWeek());
    LocalDate presidentialInaugurationDay2022 = Holidays.presidentialInaugurationDay(2022);
    assertNull(presidentialInaugurationDay2022);
    LocalDate presidentialInaugurationDay2023 = Holidays.presidentialInaugurationDay(2023);
    assertNull(presidentialInaugurationDay2023);
    LocalDate presidentialInaugurationDay2024 = Holidays.presidentialInaugurationDay(2024);
    assertNull(presidentialInaugurationDay2024);
    LocalDate presidentialInaugurationDay2025 = Holidays.presidentialInaugurationDay(2025);
    assertNotNull(presidentialInaugurationDay2025);
    assertEquals(Month.JANUARY, presidentialInaugurationDay2025.getMonth());
    assertEquals(20, presidentialInaugurationDay2025.getDayOfMonth());
    assertEquals(2025, presidentialInaugurationDay2025.getYear());
    assertEquals(DayOfWeek.MONDAY, presidentialInaugurationDay2025.getDayOfWeek());
    LocalDate presidentialInaugurationDay2026 = Holidays.presidentialInaugurationDay(2026);
    assertNull(presidentialInaugurationDay2026);
    LocalDate presidentialInaugurationDay2027 = Holidays.presidentialInaugurationDay(2027);
    assertNull(presidentialInaugurationDay2027);
    LocalDate presidentialInaugurationDay2028 = Holidays.presidentialInaugurationDay(2028);
    assertNull(presidentialInaugurationDay2028);
    LocalDate presidentialInaugurationDay2029 = Holidays.presidentialInaugurationDay(2029);
    assertNotNull(presidentialInaugurationDay2029);
    LocalDate presidentialInaugurationDay2030 = Holidays.presidentialInaugurationDay(2030);
    assertNull(presidentialInaugurationDay2030);
  }

  @Test
  void goodFriday() {
    LocalDate goodFriday2020 = Holidays.goodFriday(2020);
    assertEquals(Month.APRIL, goodFriday2020.getMonth());
    assertEquals(10, goodFriday2020.getDayOfMonth());
    assertEquals(2020, goodFriday2020.getYear());
    assertEquals(DayOfWeek.FRIDAY, goodFriday2020.getDayOfWeek());

    LocalDate goodFriday2021 = Holidays.goodFriday(2021);
    assertEquals(Month.APRIL, goodFriday2021.getMonth());
    assertEquals(2, goodFriday2021.getDayOfMonth());
    assertEquals(2021, goodFriday2021.getYear());
    assertEquals(DayOfWeek.FRIDAY, goodFriday2021.getDayOfWeek());

    LocalDate goodFriday2022 = Holidays.goodFriday(2022);
    assertEquals(Month.APRIL, goodFriday2022.getMonth());
    assertEquals(15, goodFriday2022.getDayOfMonth());
    assertEquals(2022, goodFriday2022.getYear());
    assertEquals(DayOfWeek.FRIDAY, goodFriday2022.getDayOfWeek());

    LocalDate goodFriday2023 = Holidays.goodFriday(2023);
    assertEquals(Month.APRIL, goodFriday2023.getMonth());
    assertEquals(7, goodFriday2023.getDayOfMonth());
    assertEquals(2023, goodFriday2023.getYear());
    assertEquals(DayOfWeek.FRIDAY, goodFriday2023.getDayOfWeek());

    LocalDate goodFriday2024 = Holidays.goodFriday(2024);
    assertEquals(Month.MARCH, goodFriday2024.getMonth());
    assertEquals(29, goodFriday2024.getDayOfMonth());
    assertEquals(2024, goodFriday2024.getYear());
    assertEquals(DayOfWeek.FRIDAY, goodFriday2024.getDayOfWeek());
  }

  @Test
  void easterSunday() {
    LocalDate easterSunday2020 = Holidays.easterSunday(2020);
    assertEquals(Month.APRIL, easterSunday2020.getMonth());
    assertEquals(12, easterSunday2020.getDayOfMonth());
    assertEquals(2020, easterSunday2020.getYear());
    assertEquals(DayOfWeek.SUNDAY, easterSunday2020.getDayOfWeek());

    LocalDate easterSunday2021 = Holidays.easterSunday(2021);
    assertEquals(Month.APRIL, easterSunday2021.getMonth());
    assertEquals(4, easterSunday2021.getDayOfMonth());
    assertEquals(2021, easterSunday2021.getYear());
    assertEquals(DayOfWeek.SUNDAY, easterSunday2021.getDayOfWeek());

    LocalDate easterSunday2022 = Holidays.easterSunday(2022);
    assertEquals(Month.APRIL, easterSunday2022.getMonth());
    assertEquals(17, easterSunday2022.getDayOfMonth());
    assertEquals(2022, easterSunday2022.getYear());
    assertEquals(DayOfWeek.SUNDAY, easterSunday2022.getDayOfWeek());

    LocalDate easterSunday2023 = Holidays.easterSunday(2023);
    assertEquals(Month.APRIL, easterSunday2023.getMonth());
    assertEquals(9, easterSunday2023.getDayOfMonth());
    assertEquals(2023, easterSunday2023.getYear());
    assertEquals(DayOfWeek.SUNDAY, easterSunday2023.getDayOfWeek());

    LocalDate easterSunday2024 = Holidays.easterSunday(2024);
    assertEquals(Month.MARCH, easterSunday2024.getMonth());
    assertEquals(31, easterSunday2024.getDayOfMonth());
    assertEquals(2024, easterSunday2024.getYear());
    assertEquals(DayOfWeek.SUNDAY, easterSunday2024.getDayOfWeek());
  }

  @Test
  void easterMonday() {
    LocalDate easterMonday2020 = Holidays.easterMonday(2020);
    assertEquals(Month.APRIL, easterMonday2020.getMonth());
    assertEquals(13, easterMonday2020.getDayOfMonth());
    assertEquals(2020, easterMonday2020.getYear());
    assertEquals(DayOfWeek.MONDAY, easterMonday2020.getDayOfWeek());

    LocalDate easterMonday2021 = Holidays.easterMonday(2021);
    assertEquals(Month.APRIL, easterMonday2021.getMonth());
    assertEquals(5, easterMonday2021.getDayOfMonth());
    assertEquals(2021, easterMonday2021.getYear());
    assertEquals(DayOfWeek.MONDAY, easterMonday2021.getDayOfWeek());

    LocalDate easterMonday2022 = Holidays.easterMonday(2022);
    assertEquals(Month.APRIL, easterMonday2022.getMonth());
    assertEquals(18, easterMonday2022.getDayOfMonth());
    assertEquals(2022, easterMonday2022.getYear());
    assertEquals(DayOfWeek.MONDAY, easterMonday2022.getDayOfWeek());

    LocalDate easterMonday2023 = Holidays.easterMonday(2023);
    assertEquals(Month.APRIL, easterMonday2023.getMonth());
    assertEquals(10, easterMonday2023.getDayOfMonth());
    assertEquals(2023, easterMonday2023.getYear());
    assertEquals(DayOfWeek.MONDAY, easterMonday2023.getDayOfWeek());

    LocalDate easterMonday2024 = Holidays.easterMonday(2024);
    assertEquals(Month.APRIL, easterMonday2024.getMonth());
    assertEquals(1, easterMonday2024.getDayOfMonth());
    assertEquals(2024, easterMonday2024.getYear());
    assertEquals(DayOfWeek.MONDAY, easterMonday2024.getDayOfWeek());
  }

  @Test
  void memorialDayObserved() {
    LocalDate memorialDayObserved2021 = Holidays.memorialDayObserved(2021);
    assertEquals(Month.MAY, memorialDayObserved2021.getMonth());
    assertEquals(31, memorialDayObserved2021.getDayOfMonth());
    assertEquals(2021, memorialDayObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, memorialDayObserved2021.getDayOfWeek());
  }

  @Test
  void independenceDayObserved() {
    LocalDate independenceDayObserved2021 = Holidays.independenceDayObserved(2021);
    assertEquals(Month.JULY, independenceDayObserved2021.getMonth());
    assertEquals(5, independenceDayObserved2021.getDayOfMonth());
    assertEquals(2021, independenceDayObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, independenceDayObserved2021.getDayOfWeek());
  }

  @Test
  void canadianCivicHoliday() {
    LocalDate canadianCivicHoliday2021 = Holidays.canadianCivicHoliday(2021);
    assertEquals(Month.AUGUST, canadianCivicHoliday2021.getMonth());
    assertEquals(2, canadianCivicHoliday2021.getDayOfMonth());
    assertEquals(2021, canadianCivicHoliday2021.getYear());
    assertEquals(DayOfWeek.MONDAY, canadianCivicHoliday2021.getDayOfWeek());
  }

  @Test
  void laborDayObserved() {
    LocalDate laborDayObserved2021 = Holidays.laborDayObserved(2021);
    assertEquals(Month.SEPTEMBER, laborDayObserved2021.getMonth());
    assertEquals(6, laborDayObserved2021.getDayOfMonth());
    assertEquals(2021, laborDayObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, laborDayObserved2021.getDayOfWeek());
  }

  @Test
  void columbusDayObserved() {
    LocalDate columbusDayObserved2021 = Holidays.columbusDayObserved(2021);
    assertEquals(Month.OCTOBER, columbusDayObserved2021.getMonth());
    assertEquals(11, columbusDayObserved2021.getDayOfMonth());
    assertEquals(2021, columbusDayObserved2021.getYear());
    assertEquals(DayOfWeek.MONDAY, columbusDayObserved2021.getDayOfWeek());
  }

  @Test
  void usElectionDay() {
    LocalDate usElectionDay2021 = Holidays.usElectionDay(2021);
    assertEquals(Month.NOVEMBER, usElectionDay2021.getMonth());
    assertEquals(2, usElectionDay2021.getDayOfMonth());
    assertEquals(2021, usElectionDay2021.getYear());
    assertEquals(DayOfWeek.TUESDAY, usElectionDay2021.getDayOfWeek());
  }

  @Test
  void veteransDayObserved() {
    LocalDate veteransDayObserved2021 = Holidays.veteransDayObserved(2021);
    assertEquals(Month.NOVEMBER, veteransDayObserved2021.getMonth());
    assertEquals(11, veteransDayObserved2021.getDayOfMonth());
    assertEquals(2021, veteransDayObserved2021.getYear());
    assertEquals(DayOfWeek.THURSDAY, veteransDayObserved2021.getDayOfWeek());
  }

  @Test
  void thanksgivingObserved() {
    LocalDate thanksgiving2021 = Holidays.thanksgiving(2021);
    assertEquals(Month.NOVEMBER, thanksgiving2021.getMonth());
    assertEquals(25, thanksgiving2021.getDayOfMonth());
    assertEquals(2021, thanksgiving2021.getYear());
    assertEquals(DayOfWeek.THURSDAY, thanksgiving2021.getDayOfWeek());
  }

  @Test
  void christmasHolidayObserved() {
    LocalDate christmasHolidayObserved2021 = Holidays.christmasHolidayObserved(2021);
    assertEquals(Month.DECEMBER, christmasHolidayObserved2021.getMonth());
    assertEquals(24, christmasHolidayObserved2021.getDayOfMonth());
    assertEquals(2021, christmasHolidayObserved2021.getYear());
    assertEquals(DayOfWeek.FRIDAY, christmasHolidayObserved2021.getDayOfWeek());
  }

  @Test
  void dateRangeTest() {
    LocalDate startDate = LocalDate.of(2021, Holidays.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2021, Holidays.JANUARY, 31);
    List<Holiday> holidayList = HolidaysCommand.usHolidays(startDate, endDate);
    assertEquals(3, holidayList.size());
  }

  @Test
  void dateRangeTest2021and2022() {
    LocalDate startDate = LocalDate.of(2021, Holidays.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2022, Holidays.DECEMBER, 31);
    List<Holiday> holidayList = HolidaysCommand.usHolidays(startDate, endDate);
    assertEquals(33, holidayList.size());
  }

  @Test
  void dateRangeTest2022() {
    LocalDate startDate = LocalDate.of(2022, Holidays.JANUARY, 1);
    LocalDate endDate = LocalDate.of(2022, Holidays.DECEMBER, 31);
    List<Holiday> holidayList = HolidaysCommand.usHolidays(startDate, endDate);
    assertEquals(15, holidayList.size());
  }

  @Test
  void dateRangeTest2021Dec() {
    LocalDate startDate = LocalDate.of(2021, Holidays.DECEMBER, 1);
    LocalDate endDate = LocalDate.of(2021, Holidays.DECEMBER, 31);
    List<Holiday> holidayList = HolidaysCommand.usHolidays(startDate, endDate);
    assertEquals(3, holidayList.size());
  }

  @Test
  void dateRangeTest2022Dec() {
    LocalDate startDate = LocalDate.of(2022, Holidays.DECEMBER, 1);
    LocalDate endDate = LocalDate.of(2022, Holidays.DECEMBER, 31);
    List<Holiday> holidayList = HolidaysCommand.usHolidays(startDate, endDate);
    assertEquals(2, holidayList.size());
  }
}
