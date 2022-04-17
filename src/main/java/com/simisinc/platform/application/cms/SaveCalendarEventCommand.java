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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.events.cms.CalendarEventRescheduledEvent;
import com.simisinc.platform.domain.events.cms.CalendarEventScheduledEvent;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateCalendarEventUniqueIdCommand.generateUniqueId;

/**
 * Validates and saves calendar event objects
 *
 * @author matt rajkowski
 * @created 10/29/18 1:21 PM
 */
public class SaveCalendarEventCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveCalendarEventCommand.class);

  public static CalendarEvent saveCalendarEvent(CalendarEvent calendarEventBean) throws DataException {

    // Required dependencies
    if (calendarEventBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this calendar event was not set");
    }
    if (calendarEventBean.getCalendarId() == -1) {
      throw new DataException("A calendar must be set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(calendarEventBean.getTitle())) {
      errorMessages.append("A title is required");
    }
    if (calendarEventBean.getStartDate() != null && calendarEventBean.getEndDate() != null && calendarEventBean.getEndDate().before(calendarEventBean.getStartDate())) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("The end date needs to come after the start date");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Clean the content
    String cleanedContent = HtmlCommand.cleanContent(calendarEventBean.getBody());

    // Transform the fields and store...
    CalendarEvent calendarEvent;
    if (calendarEventBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      calendarEvent = CalendarEventRepository.findById(calendarEventBean.getId());
      if (calendarEvent == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      calendarEvent = new CalendarEvent();
    }

    // Check for events
    boolean justScheduled = false;
    boolean justRescheduled = false;
    if (calendarEvent.getId() > -1) {
      // If it's existing, and the date has changed
      if (!calendarEvent.getStartDate().equals(calendarEventBean.getStartDate()) ||
          !calendarEvent.getEndDate().equals(calendarEventBean.getEndDate()))
        justRescheduled = true;
    } else {
      // It's new
      justScheduled = true;
    }

    // @note set the uniqueId before setting the name and calendar
    calendarEvent.setUniqueId(generateUniqueId(calendarEvent, calendarEventBean));
    calendarEvent.setCalendarId(calendarEventBean.getCalendarId());
    calendarEvent.setTitle(calendarEventBean.getTitle());
    calendarEvent.setBody(cleanedContent);
    calendarEvent.setSummary(calendarEventBean.getSummary());
    calendarEvent.setAllDay(calendarEventBean.getAllDay());
    calendarEvent.setDetailsUrl(calendarEventBean.getDetailsUrl());
    calendarEvent.setSignUpUrl(calendarEventBean.getSignUpUrl());
    calendarEvent.setLocation(calendarEventBean.getLocation());
    calendarEvent.setImageUrl(calendarEventBean.getImageUrl());
    calendarEvent.setCreatedBy(calendarEventBean.getCreatedBy());
    calendarEvent.setModifiedBy(calendarEventBean.getModifiedBy());
    calendarEvent.setPublished(calendarEventBean.getPublished());
    calendarEvent.setStartDate(calendarEventBean.getStartDate());
    calendarEvent.setEndDate(calendarEventBean.getEndDate());
    if (calendarEvent.getStartDate() == null && calendarEvent.getPublished() != null) {
      calendarEvent.setStartDate(calendarEvent.getPublished());
    }

    CalendarEvent result = CalendarEventRepository.save(calendarEvent);
    if (result != null) {
      // Trigger events
      if (justScheduled) {
        WorkflowManager.triggerWorkflowForEvent(new CalendarEventScheduledEvent(calendarEvent));
      } else if (justRescheduled) {
        WorkflowManager.triggerWorkflowForEvent(new CalendarEventRescheduledEvent(calendarEvent));
      }
    }
    return result;
  }
}
