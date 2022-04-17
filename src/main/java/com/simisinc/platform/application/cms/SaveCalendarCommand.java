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
import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateCalendarUniqueIdCommand.generateUniqueId;


/**
 * Validates and saves calendar objects
 *
 * @author matt rajkowski
 * @created 10/29/18 2:17 PM
 */
public class SaveCalendarCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveCalendarCommand.class);

  public static Calendar saveCalendar(Calendar calendarBean) throws DataException {

    // Required dependencies
    if (calendarBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(calendarBean.getName())) {
      errorMessages.append("A name is required");
    }
    if (!StringUtils.isBlank(calendarBean.getColor())) {

      String color = calendarBean.getColor();
      if (color.length() != 7 || !color.startsWith("#") || !StringUtils.isAlphanumeric(color.substring(1))) {
        errorMessages.append("Color must be a hex value like #a1a1a1");
      }

    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Calendar calendar;
    if (calendarBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      calendar = CalendarRepository.findById(calendarBean.getId());
      if (calendar == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      calendar = new Calendar();
    }
    // @note set the uniqueId before setting the name
    calendar.setUniqueId(generateUniqueId(calendar, calendarBean));
    calendar.setName(calendarBean.getName());
    calendar.setDescription(calendarBean.getDescription());
    calendar.setColor(calendarBean.getColor());
    calendar.setCreatedBy(calendarBean.getCreatedBy());
    calendar.setModifiedBy(calendarBean.getModifiedBy());
    calendar.setEnabled(calendarBean.getEnabled());
    return CalendarRepository.save(calendar);
  }
}
