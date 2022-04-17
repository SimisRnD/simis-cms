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

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Returns a holiday's date for the specified year
 *
 * @author Gregory N. Mirsky; http://images.techhive.com/downloads/idge/imported/article/jvw/1998/01/holidays.java
 * @author John D. Mitchell
 * @author Matt Rajkowski
 * @created 7/29/20 7:59 AM
 */
public class Holidays {

  public static final int JANUARY = 1;
  public static final int FEBRUARY = 2;
  public static final int MARCH = 3;
  public static final int APRIL = 4;
  public static final int MAY = 5;
  public static final int JUNE = 6;
  public static final int JULY = 7;
  public static final int AUGUST = 8;
  public static final int SEPTEMBER = 9;
  public static final int OCTOBER = 10;
  public static final int NOVEMBER = 11;
  public static final int DECEMBER = 12;

  public static LocalDate newYearsDayObserved(int year) {
    LocalDate localDate = LocalDate.of(year, JANUARY, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 1: // Monday
      case 2: // Tuesday
      case 3: // Wednesday
      case 4: // Thursday
      case 5: // Friday
        return localDate;
      case 7: // Sunday
        return localDate.plusDays(1);
      default:
        // Saturday, then observe on friday of previous year
        return LocalDate.of(--year, DECEMBER, 31);
    }
  }

  public static LocalDate newYearsDay(int year) {
    return LocalDate.of(year, JANUARY, 1);
  }

  public static LocalDate martinLutherKingJrObserved(int year) {
    // Third Monday in January (Birthday of Martin Luther King, Jr.)
    LocalDate localDate = LocalDate.of(year, JANUARY, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 1: // Monday
        return LocalDate.of(year, JANUARY, 15);
      case 2: // Tuesday
        return LocalDate.of(year, JANUARY, 21);
      case 3: // Wednesday
        return LocalDate.of(year, JANUARY, 20);
      case 4: // Thursday
        return LocalDate.of(year, JANUARY, 19);
      case 5: // Friday
        return LocalDate.of(year, JANUARY, 18);
      case 7: // Sunday
        return LocalDate.of(year, JANUARY, 16);
      default: // Saturday
        return LocalDate.of(year, JANUARY, 17);
    }
  }

  public static LocalDate presidentialInaugurationDay(int year) {
    // Check the year to see if there is an inauguration
    int remainder = (year - 2017) % 4;
    if (remainder == 0) {
      return LocalDate.of(year, JANUARY, 20);
    } else {
      return null;
    }
  }

  public static LocalDate presidentialInaugurationDayHoliday(int year) {
    // January 20 following an election, moves to January 21 if on Sunday
    LocalDate localDate = presidentialInaugurationDay(year);
    if (localDate != null && localDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return localDate.plusDays(1);
    }
    return localDate;
  }

  public static LocalDate groundhogDay(int year) {
    return LocalDate.of(year, FEBRUARY, 2);
  }

  public static LocalDate abrahamLincolnsBirthday(int year) {
    return LocalDate.of(year, FEBRUARY, 12);
  }

  public static LocalDate valentinesDay(int year) {
    return LocalDate.of(year, FEBRUARY, 14);
  }

  public static LocalDate susanBAnthonyDay(int year) {
    return LocalDate.of(year, FEBRUARY, 15);
  }

  public static LocalDate presidentsDayObserved(int year) {
    // Third Monday in February (Washington's Birthday)
    LocalDate localDate = LocalDate.of(year, FEBRUARY, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 1: // Monday
        return LocalDate.of(year, FEBRUARY, 15);
      case 2: // Tuesday
        return LocalDate.of(year, FEBRUARY, 21);
      case 3: // Wednesday
        return LocalDate.of(year, FEBRUARY, 20);
      case 4: // Thursday
        return LocalDate.of(year, FEBRUARY, 19);
      case 5: // Friday
        return LocalDate.of(year, FEBRUARY, 18);
      case 7: // Sunday
        return LocalDate.of(year, FEBRUARY, 16);
      default: // Saturday
        return LocalDate.of(year, FEBRUARY, 17);
    }
  }

  public static LocalDate saintPatricksDay(int year) {
    return LocalDate.of(year, MARCH, 17);
  }

  public static LocalDate goodFriday(int year) {
    LocalDate easterSunday = easterSunday(year);
    return easterSunday.minusDays(2);
  }

  public static LocalDate easterSunday(int year) {
    /*
     * Calculate Easter Sunday
     *
     * Written by Gregory N. Mirsky
     *
     * Source: 2nd Edition by Peter Duffett-Smith. It was originally from Butcher's
     * Ecclesiastical Calendar, published in 1876. This algorithm has also been
     * published in the 1922 book General Astronomy by Spencer Jones; in The Journal
     * of the British Astronomical Association (Vol.88, page 91, December 1977); and
     * in Astronomical Algorithms (1991) by Jean Meeus.
     *
     * This algorithm holds for any year in the Gregorian Calendar, which (of
     * course) means years including and after 1583.
     *
     * a=year%19 b=year/100 c=year%100 d=b/4 e=b%4 f=(b+8)/25 g=(b-f+1)/3
     * h=(19*a+b-d-g+15)%30 i=c/4 k=c%4 l=(32+2*e+2*i-h-k)%7 m=(a+11*h+22*l)/451
     * Easter Month =(h+l-7*m+114)/31 [3=March, 4=April] p=(h+l-7*m+114)%31 Easter
     * Date=p+1 (date in Easter Month)
     *
     * Note: Integer truncation is already factored into the calculations. Using
     * higher precision variables will cause inaccurate calculations.
     */

    int a = year % 19,
        b = year / 100,
        c = year % 100,
        d = b / 4,
        e = b % 4,
        g = (8 * b + 13) / 25,
        h = (19 * a + b - d - g + 15) % 30,
        j = c / 4,
        k = c % 4,
        m = (a + 11 * h) / 319,
        r = (2 * e + 2 * j - k - h + m + 32) % 7,
        n = (h - m + r + 90) / 25,
        p = (h - m + r + n + 19) % 32;

    return LocalDate.of(year, n, p);
  }

  public static LocalDate easterMonday(int year) {
    LocalDate easterSunday = easterSunday(year);
    return easterSunday.plusDays(1);
  }

  public static LocalDate cincoDeMayo(int year) {
    return LocalDate.of(year, MAY, 5);
  }

  public static LocalDate memorialDayObserved(int year) {
    // Last Monday in May
    LocalDate localDate = LocalDate.of(year, MAY, 31);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 1: // Monday
        return localDate;
      case 2: // Tuesday
        return LocalDate.of(year, MAY, 30);
      case 3: // Wednesday
        return LocalDate.of(year, MAY, 29);
      case 4: // Thursday
        return LocalDate.of(year, MAY, 28);
      case 5: // Friday
        return LocalDate.of(year, MAY, 27);
      case 7: // Sunday
        return LocalDate.of(year, MAY, 25);
      default: // Saturday
        return LocalDate.of(year, MAY, 26);
    }
  }

  public static LocalDate juneteenthNationalIndependenceDay(int year) {
    if (year < 2021) {
      return null;
    }
    return LocalDate.of(year, JUNE, 19);
  }

  public static LocalDate juneteenthNationalIndependenceDayObserved(int year) {
    if (year < 2021) {
      return null;
    }
    LocalDate localDate = LocalDate.of(year, JUNE, 19);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, JUNE, 20);
      case 1: // Monday
      case 2: // Tuesday
      case 3: // Wednesday
      case 4: // Thursday
      case 5: // Friday
        return localDate;
      default:
        // Saturday
        return LocalDate.of(year, JUNE, 18);
    }
  }

  public static LocalDate independenceDay(int year) {
    return LocalDate.of(year, JULY, 4);
  }

  public static LocalDate independenceDayObserved(int year) {
    LocalDate localDate = LocalDate.of(year, JULY, 4);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, JULY, 5);
      case 1: // Monday
      case 2: // Tuesday
      case 3: // Wednesday
      case 4: // Thursday
      case 5: // Friday
        return localDate;
      default:
        // Saturday
        return LocalDate.of(year, JULY, 3);
    }
  }

  public static LocalDate canadianCivicHoliday(int year) {
    // First Monday in August
    LocalDate localDate = LocalDate.of(year, AUGUST, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, AUGUST, 2);
      case 1: // Monday
        return localDate;
      case 2: // Tuesday
        return LocalDate.of(year, AUGUST, 7);
      case 3: // Wednesday
        return LocalDate.of(year, AUGUST, 6);
      case 4: // Thursday
        return LocalDate.of(year, AUGUST, 5);
      case 5: // Friday
        return LocalDate.of(year, AUGUST, 4);
      default: // Saturday
        return LocalDate.of(year, AUGUST, 3);
    }
  }

  public static LocalDate laborDayObserved(int year) {
    // The first Monday in September
    LocalDate localDate = LocalDate.of(year, SEPTEMBER, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, SEPTEMBER, 2);
      case 1: // Monday
        return localDate;
      case 2: // Tuesday
        return LocalDate.of(year, SEPTEMBER, 7);
      case 3: // Wednesday
        return LocalDate.of(year, SEPTEMBER, 6);
      case 4: // Thursday
        return LocalDate.of(year, SEPTEMBER, 5);
      case 5: // Friday
        return LocalDate.of(year, SEPTEMBER, 4);
      default: // Saturday
        return LocalDate.of(year, SEPTEMBER, 3);
    }
  }

  public static LocalDate columbusDayObserved(int year) {
    // Second Monday in October
    LocalDate localDate = LocalDate.of(year, OCTOBER, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, OCTOBER, 9);
      case 1: // Monday
        return LocalDate.of(year, OCTOBER, 8);
      case 2: // Tuesday
        return LocalDate.of(year, OCTOBER, 14);
      case 3: // Wednesday
        return LocalDate.of(year, OCTOBER, 13);
      case 4: // Thursday
        return LocalDate.of(year, OCTOBER, 12);
      case 5: // Friday
        return LocalDate.of(year, OCTOBER, 11);
      default: // Saturday
        return LocalDate.of(year, OCTOBER, 10);
    }
  }

  public static LocalDate halloween(int year) {
    return LocalDate.of(year, OCTOBER, 31);
  }

  public static LocalDate usElectionDay(int year) {
    // First Tuesday in November
    LocalDate localDate = LocalDate.of(year, NOVEMBER, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, NOVEMBER, 3);
      case 1: // Monday
        return LocalDate.of(year, NOVEMBER, 2);
      case 2: // Tuesday
        return localDate;
      case 3: // Wednesday
        return LocalDate.of(year, NOVEMBER, 7);
      case 4: // Thursday
        return LocalDate.of(year, NOVEMBER, 6);
      case 5: // Friday
        return LocalDate.of(year, NOVEMBER, 5);
      default: // Saturday
        return LocalDate.of(year, NOVEMBER, 4);
    }
  }

  public static LocalDate veteransDay(int year) {
    return LocalDate.of(year, NOVEMBER, 11);
  }

  public static LocalDate veteransDayObserved(int year) {
    // November 11
    LocalDate localDate = LocalDate.of(year, NOVEMBER, 11);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, NOVEMBER, 12);
      case 1: // Monday
      case 2: // Tuesday
      case 3: // Wednesday
      case 4: // Thursday
      case 5: // Friday
        return localDate;
      default:
        // Saturday
        return LocalDate.of(year, NOVEMBER, 10);
    }
  }

  public static LocalDate rememberenceDayObserved(int year) {
    // Canadian version of Veterans Day
    return veteransDayObserved(year);
  }

  public static LocalDate thanksgiving(int year) {
    // Fourth Thursday in November
    LocalDate localDate = LocalDate.of(year, NOVEMBER, 1);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, NOVEMBER, 26);
      case 1: // Monday
        return LocalDate.of(year, NOVEMBER, 25);
      case 2: // Tuesday
        return LocalDate.of(year, NOVEMBER, 24);
      case 3: // Wednesday
        return LocalDate.of(year, NOVEMBER, 23);
      case 4: // Thursday
        return LocalDate.of(year, NOVEMBER, 22);
      case 5: // Friday
        return LocalDate.of(year, NOVEMBER, 28);
      default: // Saturday
        return LocalDate.of(year, NOVEMBER, 27);
    }
  }

  public static LocalDate christmasDay(int year) {
    return LocalDate.of(year, DECEMBER, 25);
  }

  public static LocalDate christmasHolidayObserved(int year) {
    LocalDate localDate = LocalDate.of(year, DECEMBER, 25);
    DayOfWeek dayOfWeek = localDate.getDayOfWeek();
    switch (dayOfWeek.getValue()) {
      case 7: // Sunday
        return LocalDate.of(year, DECEMBER, 26);
      case 1: // Monday
      case 2: // Tuesday
      case 3: // Wednesday
      case 4: // Thursday
      case 5: // Friday
        return localDate;
      default:
        // Saturday
        return LocalDate.of(year, DECEMBER, 24);
    }
  }

}
