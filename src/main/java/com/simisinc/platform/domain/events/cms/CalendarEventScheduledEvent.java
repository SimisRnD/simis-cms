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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import lombok.NoArgsConstructor;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/6/21 11:53 PM
 */
@NoArgsConstructor
public class CalendarEventScheduledEvent extends Event {

  public static final String ID = "calendar-event-scheduled";

  private CalendarEvent calendarEvent = null;

  public CalendarEventScheduledEvent(CalendarEvent calendarEvent) {
    this.calendarEvent = calendarEvent;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public User getUser() {
    return UserRepository.findByUserId(calendarEvent.getModifiedBy());
  }

  public void setCalendarEvent(CalendarEvent calendarEvent) {
    this.calendarEvent = calendarEvent;
  }

  public CalendarEvent getCalendarEvent() {
    return calendarEvent;
  }

}
