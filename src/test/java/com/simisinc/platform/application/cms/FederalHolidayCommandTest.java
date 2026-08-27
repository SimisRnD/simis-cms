/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.FederalHoliday;

/**
 * Pins the holiday rules against dates that can be checked against a real calendar, rather than
 * against the implementation's own arithmetic.
 *
 * @author SimIS Inc.
 */
class FederalHolidayCommandTest {

  private static LocalDate observed(int year, String name) {
    return FederalHolidayCommand.forYear(year).stream()
        .filter(h -> h.getName().equals(name))
        .findFirst().orElseThrow().getObservedDate();
  }

  private static LocalDate actual(int year, String name) {
    return FederalHolidayCommand.forYear(year).stream()
        .filter(h -> h.getName().equals(name))
        .findFirst().orElseThrow().getDate();
  }

  @Test
  void thereAreElevenOfThem() {
    assertEquals(11, FederalHolidayCommand.forYear(2026).size());
  }

  @Test
  void weekdayDefinedHolidaysLandOnTheRightRealDates() {
    // Checked against a 2026 calendar
    assertEquals(LocalDate.of(2026, 1, 19), observed(2026, "Birthday of Martin Luther King, Jr."));
    assertEquals(LocalDate.of(2026, 2, 16), observed(2026, "Washington's Birthday"));
    assertEquals(LocalDate.of(2026, 5, 25), observed(2026, "Memorial Day"));
    assertEquals(LocalDate.of(2026, 9, 7), observed(2026, "Labor Day"));
    assertEquals(LocalDate.of(2026, 10, 12), observed(2026, "Columbus Day"));
    assertEquals(LocalDate.of(2026, 11, 26), observed(2026, "Thanksgiving Day"));
  }

  @Test
  void memorialDayIsTheLastMondayNotTheFourth() {
    // 2027 May has five Mondays, which is where "fourth Monday" quietly gets it wrong
    assertEquals(LocalDate.of(2027, 5, 31), observed(2027, "Memorial Day"));
  }

  @Test
  void aSaturdayHolidayIsObservedTheFridayBefore() {
    // Independence Day 2026 falls on a Saturday
    assertEquals(LocalDate.of(2026, 7, 4), actual(2026, "Independence Day"));
    assertEquals(LocalDate.of(2026, 7, 3), observed(2026, "Independence Day"));
  }

  @Test
  void aSundayHolidayIsObservedTheMondayAfter() {
    // Christmas Day 2033 falls on a Sunday
    assertEquals(LocalDate.of(2033, 12, 25), actual(2033, "Christmas Day"));
    assertEquals(LocalDate.of(2033, 12, 26), observed(2033, "Christmas Day"));
  }

  @Test
  void aWeekdayHolidayIsNeverMoved() {
    List<FederalHoliday> list = FederalHolidayCommand.forYear(2026);
    for (FederalHoliday holiday : list) {
      if (holiday.getName().endsWith("Day") && holiday.getDate().getDayOfWeek().getValue() <= 5) {
        assertFalse(holiday.getObservedOnDifferentDay(),
            holiday.getName() + " already falls on a weekday and must not be shifted");
      }
    }
  }

  @Test
  void newYearsDayOnASaturdayIsObservedInDecemberOfThePreviousYear() {
    // 1 January 2028 is a Saturday, so it is taken on Friday 31 December 2027. A list that assumed
    // a year's holidays all fall inside that year would lose this one entirely.
    assertEquals(LocalDate.of(2027, 12, 31), observed(2028, "New Year's Day"));

    List<LocalDate> upcoming = FederalHolidayCommand.upcoming(LocalDate.of(2027, 12, 27), 1)
        .stream().map(FederalHoliday::getObservedDate).collect(Collectors.toList());
    assertEquals(List.of(LocalDate.of(2027, 12, 31)), upcoming,
        "a reader in late December must be shown the New Year's Day observed before the year ends");
  }

  @Test
  void upcomingCountsTodayItself() {
    // Somebody reading this on Thanksgiving morning should see Thanksgiving, not Christmas
    List<FederalHoliday> list = FederalHolidayCommand.upcoming(LocalDate.of(2026, 11, 26), 1);
    assertEquals("Thanksgiving Day", list.get(0).getName());
  }

  @Test
  void upcomingCrossesIntoTheFollowingYear() {
    List<FederalHoliday> list = FederalHolidayCommand.upcoming(LocalDate.of(2026, 12, 26), 2);
    assertEquals(2, list.size());
    assertEquals(2027, list.get(0).getObservedDate().getYear());
  }

  @Test
  void upcomingIsOrderedByObservedDate() {
    List<FederalHoliday> list = FederalHolidayCommand.upcoming(LocalDate.of(2026, 1, 1), 11);
    for (int i = 1; i < list.size(); i++) {
      assertTrue(!list.get(i).getObservedDate().isBefore(list.get(i - 1).getObservedDate()),
          "the list must read soonest first");
    }
  }

  @Test
  void badInputReturnsAnEmptyListRatherThanThrowing() {
    assertTrue(FederalHolidayCommand.upcoming(null, 4).isEmpty());
    assertTrue(FederalHolidayCommand.upcoming(LocalDate.of(2026, 1, 1), 0).isEmpty());
  }
}
