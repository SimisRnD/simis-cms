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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.CalendarEvent;

/**
 * The ticket rules shared by the event form, the save, the event page and the Event JSON-LD.
 */
class CalendarEventTicketCommandTest {

  private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

  private static CalendarEvent priced(String price) {
    CalendarEvent calendarEvent = new CalendarEvent();
    calendarEvent.setTicketPrice(price == null ? null : new BigDecimal(price));
    return calendarEvent;
  }

  // ---- describe: the line the event page shows ----

  @Test
  void describeSaysNothingForAnEventWithNoTicketDetails() {
    assertNull(CalendarEventTicketCommand.describe(new CalendarEvent(), NOW));
  }

  @Test
  void describeShowsTheLowestPriceWithoutCentsWhenThereAreNone() {
    assertEquals("From $1,295", CalendarEventTicketCommand.describe(priced("1295.00"), NOW));
    assertEquals("From $49.50", CalendarEventTicketCommand.describe(priced("49.5"), NOW));
  }

  @Test
  void describeCallsAZeroPriceFree() {
    assertEquals("Free", CalendarEventTicketCommand.describe(priced("0.00"), NOW));
  }

  @Test
  void describeUsesTheEventsOwnCurrency() {
    CalendarEvent calendarEvent = priced("300");
    calendarEvent.setTicketCurrency("EUR");
    assertEquals("From €300", CalendarEventTicketCommand.describe(calendarEvent, NOW));
  }

  @Test
  void describeAddsSoldOutAndPreOrderButNotTheImpliedOnSale() {
    CalendarEvent calendarEvent = priced("1295");
    calendarEvent.setTicketAvailability("SoldOut");
    assertEquals("From $1,295 · Sold out", CalendarEventTicketCommand.describe(calendarEvent, NOW));
    calendarEvent.setTicketAvailability("PreOrder");
    assertEquals("From $1,295 · Pre-order", CalendarEventTicketCommand.describe(calendarEvent, NOW));
    calendarEvent.setTicketAvailability("InStock");
    assertEquals("From $1,295", CalendarEventTicketCommand.describe(calendarEvent, NOW));
  }

  @Test
  void describeShowsTheSaleDateOnlyWhileItIsStillAhead() {
    CalendarEvent calendarEvent = priced("1295");
    try (MockedStatic<FormatDateCommand> dates = mockStatic(FormatDateCommand.class, CALLS_REAL_METHODS)) {
      dates.when(FormatDateCommand::getSiteZoneId).thenReturn(ZoneId.of("America/New_York"));

      calendarEvent.setTicketSalesStart(Timestamp.from(Instant.parse("2026-11-02T14:00:00Z")));
      assertEquals("From $1,295 · On sale November 2, 2026", CalendarEventTicketCommand.describe(calendarEvent, NOW));

      // Once sales have opened, the date is history and says nothing a visitor needs
      calendarEvent.setTicketSalesStart(Timestamp.from(Instant.parse("2026-10-01T14:00:00Z")));
      assertEquals("From $1,295", CalendarEventTicketCommand.describe(calendarEvent, NOW));
    }
  }

  @Test
  void describeShowsAStatusEvenWithoutAPrice() {
    CalendarEvent calendarEvent = new CalendarEvent();
    calendarEvent.setTicketAvailability("SoldOut");
    assertEquals("Sold out", CalendarEventTicketCommand.describe(calendarEvent, NOW));
  }

  // ---- parsePrice: what an editor types ----

  @Test
  void parsePriceAcceptsTheWaysAPriceIsUsuallyTyped() {
    assertEquals(0, new BigDecimal("1295").compareTo(CalendarEventTicketCommand.parsePrice("$1,295")));
    assertEquals(0, new BigDecimal("1295.50").compareTo(CalendarEventTicketCommand.parsePrice(" 1,295.50 ")));
    assertEquals(0, BigDecimal.ZERO.compareTo(CalendarEventTicketCommand.parsePrice("0")));
  }

  @Test
  void parsePriceReturnsNullWhenThereIsNoNumber() {
    assertNull(CalendarEventTicketCommand.parsePrice("free"));
    assertNull(CalendarEventTicketCommand.parsePrice("about $1,000"));
    assertNull(CalendarEventTicketCommand.parsePrice("   "));
  }

  // ---- validate ----

  private static String errorsFor(CalendarEvent calendarEvent) {
    StringBuilder errors = new StringBuilder();
    CalendarEventTicketCommand.validate(calendarEvent, errors);
    return errors.toString();
  }

  @Test
  void validateAcceptsAnEventWithNoTicketDetailsAndACompleteOne() {
    assertEquals("", errorsFor(new CalendarEvent()));
    CalendarEvent calendarEvent = priced("1295.50");
    calendarEvent.setTicketCurrency("usd");
    calendarEvent.setTicketAvailability("PreOrder");
    assertEquals("", errorsFor(calendarEvent));
  }

  @Test
  void validateRejectsANegativeOrOversizedPriceOrFractionsOfACent() {
    assertTrue(errorsFor(priced("-5")).contains("can't be negative"));
    assertTrue(errorsFor(priced("100000000")).contains("too large"));
    assertTrue(errorsFor(priced("12.345")).contains("two decimal places"));
    // trailing zeros are not extra precision
    assertEquals("", errorsFor(priced("12.3400")));
  }

  @Test
  void validateRejectsACurrencyThatIsNotAThreeLetterCode() {
    CalendarEvent calendarEvent = priced("10");
    calendarEvent.setTicketCurrency("$");
    assertTrue(errorsFor(calendarEvent).contains("three-letter code"));
    calendarEvent.setTicketCurrency("dollars");
    assertTrue(errorsFor(calendarEvent).contains("three-letter code"));
  }

  @Test
  void validateRejectsAStatusOutsideTheListedOptions() {
    CalendarEvent calendarEvent = new CalendarEvent();
    calendarEvent.setTicketAvailability("LimitedAvailability");
    assertTrue(errorsFor(calendarEvent).contains("not one of the listed options"));
    assertFalse(CalendarEventTicketCommand.isKnownAvailability("LimitedAvailability"));
  }

  @Test
  void validateSeparatesSeveralProblemsTheWayTheSaveCommandDoes() {
    CalendarEvent calendarEvent = priced("-1");
    calendarEvent.setTicketCurrency("$$");
    assertEquals("The ticket price can't be negative; The ticket currency needs a three-letter code, such as USD",
        errorsFor(calendarEvent));
  }

  // ---- currencyFor ----

  @Test
  void currencyForIsNullWithoutAPriceAndUsdWhenLeftBlank() {
    assertNull(CalendarEventTicketCommand.currencyFor(new CalendarEvent()));
    assertEquals("USD", CalendarEventTicketCommand.currencyFor(priced("10")));
    CalendarEvent calendarEvent = priced("10");
    calendarEvent.setTicketCurrency(" gbp ");
    assertEquals("GBP", CalendarEventTicketCommand.currencyFor(calendarEvent));
  }

  @Test
  void formatPriceFallsBackToTheNumberAndCodeForAnUnknownCurrency() {
    assertEquals("1,295 XQZ", CalendarEventTicketCommand.formatPrice(new BigDecimal("1295"), "XQZ"));
  }
}
