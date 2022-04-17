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

import com.simisinc.platform.domain.model.cms.Calendar;
import com.simisinc.platform.infrastructure.persistence.cms.CalendarRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads a calendar object from cache or storage
 *
 * @author matt rajkowski
 * @created 10/29/18 2:20 PM
 */
public class LoadCalendarCommand {

  private static Log LOG = LogFactory.getLog(LoadCalendarCommand.class);

  public static Calendar loadCalendarByUniqueId(String calendarUniqueId) {
//    return (Calendar) CacheManager.getLoadingCache(CacheManager.COLLECTION_UNIQUE_ID_CACHE).get(uniqueId);
    return CalendarRepository.findByUniqueId(calendarUniqueId);
  }

  public static Calendar loadCalendarById(long calendarId) {
    return CalendarRepository.findById(calendarId);
  }

}
