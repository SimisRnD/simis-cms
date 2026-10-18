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

package com.simisinc.platform.presentation.widgets.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;

/**
 * The calendar page's quick editor (full-calendar.jsp) does not show the organizer, speaker or
 * ticket fields, and SaveCalendarEventCommand writes every field -- so saving an event there used
 * to blank whatever the admin event form had set.
 */
class CalendarWidgetCarryOverTest extends WidgetBase {

  private static CalendarEvent storedEvent() {
    CalendarEvent stored = new CalendarEvent();
    stored.setId(42L);
    stored.setOrganizerName("NTSA");
    stored.setOrganizerUrl("https://www.ntsa.org/");
    stored.setPerformerName("Dr. Johnny Garcia");
    stored.setPerformerUrl("https://www.simisinc.com/about-us");
    stored.setTicketPrice(new BigDecimal("1295"));
    stored.setTicketCurrency("USD");
    stored.setTicketAvailability("InStock");
    stored.setTicketSalesStart(Timestamp.valueOf("2026-10-01 09:00:00"));
    return stored;
  }

  @Test
  void fieldsTheQuickEditorNeverSubmittedKeepTheirStoredValues() {
    CalendarEvent bean = new CalendarEvent();
    bean.setId(42L);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(storedEvent());

      CalendarWidget.carryOverFieldsTheFormDoesNotSubmit(widgetContext, bean);
    }

    assertEquals("NTSA", bean.getOrganizerName());
    assertEquals("https://www.ntsa.org/", bean.getOrganizerUrl());
    assertEquals("Dr. Johnny Garcia", bean.getPerformerName());
    assertEquals("https://www.simisinc.com/about-us", bean.getPerformerUrl());
    assertEquals(0, new BigDecimal("1295").compareTo(bean.getTicketPrice()));
    assertEquals("USD", bean.getTicketCurrency());
    assertEquals("InStock", bean.getTicketAvailability());
    assertEquals(Timestamp.valueOf("2026-10-01 09:00:00"), bean.getTicketSalesStart());
  }

  @Test
  void aFieldSubmittedEmptyIsTheEditorClearingItAndStaysCleared() {
    // Present-but-empty is a deliberate clear; only an absent parameter means "not on this form"
    addQueryParameter(widgetContext, "organizerName", "");
    addQueryParameter(widgetContext, "ticketPrice", "");
    CalendarEvent bean = new CalendarEvent();
    bean.setId(42L);

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      repository.when(() -> CalendarEventRepository.findById(42L)).thenReturn(storedEvent());

      CalendarWidget.carryOverFieldsTheFormDoesNotSubmit(widgetContext, bean);
    }

    assertNull(bean.getOrganizerName());
    assertNull(bean.getTicketPrice());
    // the fields that were not submitted still carry over
    assertEquals("Dr. Johnny Garcia", bean.getPerformerName());
    assertEquals("USD", bean.getTicketCurrency());
  }

  @Test
  void aNewEventHasNothingStoredToKeep() {
    CalendarEvent bean = new CalendarEvent();

    try (MockedStatic<CalendarEventRepository> repository = mockStatic(CalendarEventRepository.class)) {
      CalendarWidget.carryOverFieldsTheFormDoesNotSubmit(widgetContext, bean);

      repository.verify(() -> CalendarEventRepository.findById(anyLong()), never());
    }
    assertNull(bean.getOrganizerName());
    assertNull(bean.getTicketPrice());
  }
}
