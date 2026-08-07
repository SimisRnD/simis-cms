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

import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarEventRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 10/29/18 1:50 PM
 */
public class GenerateCalendarEventUniqueIdCommand {

  public static String generateUniqueId(CalendarEvent previousRecord, CalendarEvent record) {

    // An existing event's URL must stay stable across edits -- including a title change or a move
    // to a different calendar -- so a rename can never silently break inbound/external links to
    // the event. This previously regenerated the slug from the new title whenever the title (or
    // calendar) changed, which is exactly the case a rename hits. Note that CalendarEventDetailsWidget
    // resolves the public event page by uniqueId alone (CalendarEventRepository.findByUniqueId(String)),
    // not scoped by calendarId, so freezing here is safe even across a calendar move.
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      return previousRecord.getUniqueId();
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getTitle());

    // Find the next available unique instance (within the calendar)
    int count = 1;
    String uniqueId = value;
    while (CalendarEventRepository.findByUniqueId(record.getCalendarId(), uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
