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

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.CalendarEvent;

/**
 * The ticket details a calendar event can carry -- the lowest price to attend, its currency,
 * whether tickets are on sale, and when sales open -- and the one place that decides what they
 * mean. The admin form, the save, the event page and the schema.org Offer all read them through
 * here, so the page a visitor sees and the markup Google reads cannot describe different tickets.
 *
 * @author SimIS
 * @created 10/18/2026
 */
public class CalendarEventTicketCommand {

  // The schema.org ItemAvailability values Google accepts on an Event's Offer. Stored as the bare
  // name; StructuredDataCommand adds the https://schema.org/ prefix.
  public static final String ON_SALE = "InStock";
  public static final String SOLD_OUT = "SoldOut";
  public static final String PRE_ORDER = "PreOrder";

  public static final String DEFAULT_CURRENCY = "USD";

  // The largest value calendar_events.ticket_price, a NUMERIC(10,2), can hold
  static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

  private static final String SEPARATOR = " · ";

  public static boolean isKnownAvailability(String availability) {
    return ON_SALE.equals(availability) || SOLD_OUT.equals(availability) || PRE_ORDER.equals(availability);
  }

  /**
   * The currency a priced event is stated in: the code the editor entered, or USD when it was left
   * blank. Returns null for an event with no price, which has no currency to state.
   */
  public static String currencyFor(CalendarEvent calendarEvent) {
    if (calendarEvent.getTicketPrice() == null) {
      return null;
    }
    String currency = StringUtils.trimToNull(calendarEvent.getTicketCurrency());
    return currency != null ? currency.toUpperCase(Locale.ROOT) : DEFAULT_CURRENCY;
  }

  /**
   * Reads a price the way an editor types one: "1295", "1,295.00", "$1,295" or " 0 ". The Bean
   * converter PageServlet registers accepts only a bare number and turns anything else into null,
   * which would read as "left blank"; this gives the form a second, more forgiving pass before it
   * reports the field as unreadable. Returns null when there is still no number in it.
   */
  public static BigDecimal parsePrice(String text) {
    if (StringUtils.isBlank(text)) {
      return null;
    }
    String cleaned = text.trim().replace(",", "").replace("$", "").trim();
    try {
      return new BigDecimal(cleaned);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * Checks the ticket fields and appends a message for each problem, in the same form
   * SaveCalendarEventCommand uses for every other field. All four are optional.
   */
  public static void validate(CalendarEvent calendarEvent, StringBuilder errorMessages) {
    BigDecimal price = calendarEvent.getTicketPrice();
    if (price != null) {
      if (price.signum() < 0) {
        append(errorMessages, "The ticket price can't be negative");
      } else if (price.compareTo(MAX_PRICE) > 0) {
        append(errorMessages, "The ticket price is too large");
      } else if (price.stripTrailingZeros().scale() > 2) {
        append(errorMessages, "The ticket price can have at most two decimal places");
      }
    }
    String currency = StringUtils.trimToNull(calendarEvent.getTicketCurrency());
    if (currency != null && !currency.toUpperCase(Locale.ROOT).matches("[A-Z]{3}")) {
      append(errorMessages, "The ticket currency needs a three-letter code, such as USD");
    }
    String availability = StringUtils.trimToNull(calendarEvent.getTicketAvailability());
    if (availability != null && !isKnownAvailability(availability)) {
      append(errorMessages, "The ticket status is not one of the listed options");
    }
  }

  /**
   * The line the event page shows about tickets, or null when there is nothing to say: "Free" or
   * "From $1,295", followed by "Sold out", "Pre-order" or the date sales open while that is still
   * ahead. "On sale" itself adds nothing, so it is not spelled out.
   * Google requires an Offer to describe what the page visibly says, which is why the price shown
   * here is the same value StructuredDataCommand emits.
   */
  public static String describe(CalendarEvent calendarEvent, Instant now) {
    List<String> parts = new ArrayList<>();
    BigDecimal price = calendarEvent.getTicketPrice();
    if (price != null) {
      parts.add(price.signum() == 0 ? "Free" : "From " + formatPrice(price, currencyFor(calendarEvent)));
    }
    String availability = StringUtils.trimToNull(calendarEvent.getTicketAvailability());
    if (SOLD_OUT.equals(availability)) {
      parts.add("Sold out");
    } else if (PRE_ORDER.equals(availability)) {
      parts.add("Pre-order");
    } else if (calendarEvent.getTicketSalesStart() != null
        && calendarEvent.getTicketSalesStart().toInstant().isAfter(now)) {
      parts.add("On sale " + FormatDateCommand.format(calendarEvent.getTicketSalesStart(), "MMMM d, yyyy"));
    }
    return parts.isEmpty() ? null : String.join(SEPARATOR, parts);
  }

  /**
   * Formats a price in its currency, dropping ".00" from a whole amount ("$1,295", "$49.50",
   * "€300"). An unrecognized currency code falls back to the number followed by the code.
   */
  static String formatPrice(BigDecimal price, String currencyCode) {
    boolean whole = price.stripTrailingZeros().scale() <= 0;
    try {
      NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
      format.setCurrency(Currency.getInstance(currencyCode));
      if (whole) {
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
      }
      return format.format(price);
    } catch (IllegalArgumentException | NullPointerException e) {
      NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
      format.setMinimumFractionDigits(whole ? 0 : 2);
      format.setMaximumFractionDigits(whole ? 0 : 2);
      return format.format(price) + (currencyCode != null ? " " + currencyCode : "");
    }
  }

  private static void append(StringBuilder errorMessages, String message) {
    if (errorMessages.length() > 0) {
      errorMessages.append("; ");
    }
    errorMessages.append(message);
  }
}
