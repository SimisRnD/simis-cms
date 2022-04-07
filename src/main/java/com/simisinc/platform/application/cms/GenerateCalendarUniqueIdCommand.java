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
 * Description
 *
 * @author matt rajkowski
 * @created 10/29/18 2:19 PM
 */
public class GenerateCalendarUniqueIdCommand {

  public static final String allowedChars = "1234567890abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(GenerateCalendarUniqueIdCommand.class);

  public static String generateUniqueId(Calendar previousRecord, Calendar record) {

    // Use an existing uniqueId
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      // See if the name changed
      if (previousRecord.getName().equals(record.getName())) {
        return previousRecord.getUniqueId();
      }
    }

    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = record.getName().toLowerCase();
    final int len = name.length();
    char lastChar = '-';
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
        lastChar = c;
      } else if (c == '&') {
        sb.append("and");
        lastChar = '&';
      } else if (c == ' ' || c == '-' || c == '/') {
        if (lastChar != '-') {
          sb.append("-");
        }
        lastChar = '-';
      }
    }
    String value = sb.toString();
    while (value.endsWith("-")) {
      value = value.substring(0, value.length() - 1);
    }

    // Find the next available unique instance
    int count = 1;
    String originalUniqueId = value;
    String uniqueId = value;
    while (CalendarRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = originalUniqueId + "-" + count;
    }
    return uniqueId;
  }

}
