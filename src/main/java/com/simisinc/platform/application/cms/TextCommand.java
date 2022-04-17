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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Functions for text strings
 *
 * @author matt rajkowski
 * @created 4/26/18 10:16 AM
 */
public class TextCommand {

  private static Log LOG = LogFactory.getLog(TextCommand.class);

  public static String trim(String text, int maxChars, boolean ellipsis) {
    // Validate the input
    if (StringUtils.isBlank(text)) {
      return "";
    }
    if (text.trim().length() <= maxChars) {
      return text.trim();
    }

    // Return the text without ellipsis
    String shortText = text.substring(0, maxChars);
    if (!ellipsis) {
      return shortText.trim();
    }

    // Use whole words when returning ellipsis...
    if (shortText.charAt(maxChars - 1) == ' ' || text.charAt(maxChars) == ' ') {
      // Got it right on the space
    } else {
      // Find the last space
      int lastSpace = shortText.lastIndexOf(' ');
      if (lastSpace > 0) {
        shortText = shortText.substring(0, lastSpace);
      }
    }
    return shortText.trim() + "...";
  }

}
