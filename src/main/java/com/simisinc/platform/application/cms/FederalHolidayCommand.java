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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.simisinc.platform.domain.model.FederalHoliday;

/**
 * Computes United States federal holidays from the rules in 5 U.S.C. 6103, rather than storing
 * them.
 *
 * <p>Every one of the eleven is either a fixed calendar date or an "nth weekday of a month", so a
 * stored list would buy nothing and cost the one thing that matters: somebody has to remember to
 * extend it, every year, forever. A list that silently runs out is worse than no list, because it
 * looks maintained right up until the moment it is wrong. Computing them cannot go stale.
 *
 * <p>The weekend rule is from 5 U.S.C. 6103(a) and Executive Order 11582: a fixed-date holiday
 * falling on a Saturday is observed the Friday before, and one falling on a Sunday the Monday
 * after. Holidays already defined as a Monday or Thursday never move.
 *
 * <p>Note the year-boundary case this creates: when January 1st is a Saturday, that New Year's Day
 * is observed on December 31st of the <em>previous</em> year. {@link #upcoming} therefore looks
 * into next year and sorts by observed date rather than assuming a year's holidays all fall inside
 * it.
 *
 * @author SimIS Inc.
 */
public class FederalHolidayCommand {

  private FederalHolidayCommand() {
  }

  /** The eleven federal holidays of a given year, ordered by the date they are observed */
  public static List<FederalHoliday> forYear(int year) {
    List<FederalHoliday> list = new ArrayList<>();
    // Fixed dates -- these are the ones the weekend rule can move
    list.add(fixed("New Year's Day", LocalDate.of(year, Month.JANUARY, 1)));
    list.add(fixed("Juneteenth National Independence Day", LocalDate.of(year, Month.JUNE, 19)));
    list.add(fixed("Independence Day", LocalDate.of(year, Month.JULY, 4)));
    list.add(fixed("Veterans Day", LocalDate.of(year, Month.NOVEMBER, 11)));
    list.add(fixed("Christmas Day", LocalDate.of(year, Month.DECEMBER, 25)));
    // Weekday-defined -- always fall on a working day, so observed == actual
    list.add(weekday("Birthday of Martin Luther King, Jr.", nth(year, Month.JANUARY, DayOfWeek.MONDAY, 3)));
    list.add(weekday("Washington's Birthday", nth(year, Month.FEBRUARY, DayOfWeek.MONDAY, 3)));
    list.add(weekday("Memorial Day", last(year, Month.MAY, DayOfWeek.MONDAY)));
    list.add(weekday("Labor Day", nth(year, Month.SEPTEMBER, DayOfWeek.MONDAY, 1)));
    list.add(weekday("Columbus Day", nth(year, Month.OCTOBER, DayOfWeek.MONDAY, 2)));
    list.add(weekday("Thanksgiving Day", nth(year, Month.NOVEMBER, DayOfWeek.THURSDAY, 4)));
    Collections.sort(list);
    return list;
  }

  /**
   * The next {@code limit} holidays whose observed date is on or after {@code from}.
   *
   * <p>A holiday is still "upcoming" on the day itself -- somebody reading this on the morning of
   * Thanksgiving should see Thanksgiving, not be told the next one is Christmas.
   *
   * @param from the day to count from, in the site's own timezone
   * @param limit how many to return
   * @return the upcoming holidays, soonest first, never null
   */
  public static List<FederalHoliday> upcoming(LocalDate from, int limit) {
    List<FederalHoliday> upcoming = new ArrayList<>();
    if (from == null || limit < 1) {
      return upcoming;
    }
    // Two years is always enough for any limit up to eleven, and covers a December reader whose
    // next holidays are in January -- including a New Year's Day observed on December 31st
    List<FederalHoliday> candidates = new ArrayList<>(forYear(from.getYear()));
    candidates.addAll(forYear(from.getYear() + 1));
    Collections.sort(candidates);
    for (FederalHoliday holiday : candidates) {
      if (!holiday.getObservedDate().isBefore(from)) {
        upcoming.add(holiday);
        if (upcoming.size() == limit) {
          break;
        }
      }
    }
    return upcoming;
  }

  /** Applies the weekend rule: Saturday moves back to Friday, Sunday forward to Monday */
  private static FederalHoliday fixed(String name, LocalDate date) {
    LocalDate observed = date;
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY) {
      observed = date.minusDays(1);
    } else if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      observed = date.plusDays(1);
    }
    return new FederalHoliday(name, date, observed);
  }

  private static FederalHoliday weekday(String name, LocalDate date) {
    return new FederalHoliday(name, date, date);
  }

  private static LocalDate nth(int year, Month month, DayOfWeek dayOfWeek, int occurrence) {
    return LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(occurrence, dayOfWeek));
  }

  private static LocalDate last(int year, Month month, DayOfWeek dayOfWeek) {
    return LocalDate.of(year, month, 1).with(TemporalAdjusters.lastInMonth(dayOfWeek));
  }
}
