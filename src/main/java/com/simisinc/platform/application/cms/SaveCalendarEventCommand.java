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
import com.simisinc.platform.application.FieldLengthCommand;
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

  // @column calendar_events.title
  private static final int MAX_TITLE_LENGTH = 255;


  public static final String allowedChars = "abcdefghijklmnopqrstuvwxyz";
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
    } else {
      FieldLengthCommand.appendIfTooLong(errorMessages, "; ", "A title",
          calendarEventBean.getTitle(), MAX_TITLE_LENGTH);
    }
    // Both dates are required, and neither was checked. calendar_events.end_date is NOT NULL, so a
    // missing end date reached PostgreSQL and the insert died on the constraint -- the save is lost
    // and the author sees a system error rather than a field they can fix (issue #1938). A missing
    // start date was worse because it did not fail: it was backfilled with the publish time further
    // down, so the event silently moved to today. Both are caught here, in the one place both the
    // admin form and the full calendar editor go through.
    if (calendarEventBean.getStartDate() == null) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("A start date is required");
    }
    if (calendarEventBean.getEndDate() == null) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("An end date is required");
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
      // createdBy is set once, below, only for a genuinely new record -- an edit must not
      // reassign the original creator to whoever happens to be editing it today
    } else {
      LOG.debug("Saving a new record... ");
      calendarEvent = new CalendarEvent();
      calendarEvent.setCreatedBy(calendarEventBean.getCreatedBy());
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
    calendarEvent.setOrganizerName(calendarEventBean.getOrganizerName());
    calendarEvent.setOrganizerUrl(calendarEventBean.getOrganizerUrl());
    calendarEvent.setPerformerName(calendarEventBean.getPerformerName());
    calendarEvent.setPerformerUrl(calendarEventBean.getPerformerUrl());
    calendarEvent.setLocation(calendarEventBean.getLocation());
    // The structured address behind that free-text venue name. StructuredDataCommand turns these
    // into the schema.org PostalAddress an Event's location needs -- Search Console reports
    // "Missing field address (in location)" for every event without them, and until now nothing
    // could set them: no form field, no mapping here, and no column in the repository's insert.
    calendarEvent.setStreet(calendarEventBean.getStreet());
    calendarEvent.setCity(calendarEventBean.getCity());
    calendarEvent.setState(calendarEventBean.getState());
    calendarEvent.setPostalCode(calendarEventBean.getPostalCode());
    calendarEvent.setCountry(calendarEventBean.getCountry());
    calendarEvent.setImageUrl(calendarEventBean.getImageUrl());
    calendarEvent.setVideoUrl(calendarEventBean.getVideoUrl());
    calendarEvent.setTagsList(calendarEventBean.getTagsList());
    calendarEvent.setModifiedBy(calendarEventBean.getModifiedBy());
    calendarEvent.setPublished(calendarEventBean.getPublished());
    calendarEvent.setStartDate(calendarEventBean.getStartDate());
    calendarEvent.setEndDate(calendarEventBean.getEndDate());
    // The backfill that used to sit here -- a null startDate replaced with the publish time -- is
    // gone. It is unreachable now that a start date is required above, and it was the mechanism
    // that turned a date the converter could not read into a silent "today" rather than an error.
    // Same shape as the blog-post defect in issue #1351, fixed by PR #1353.

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
