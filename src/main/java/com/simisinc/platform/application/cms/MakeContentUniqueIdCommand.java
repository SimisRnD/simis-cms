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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Generates a URL compatible id
 *
 * @author matt rajkowski
 * @created 5/6/18 7:00 PM
 */
public class MakeContentUniqueIdCommand {

  private static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static Log LOG = LogFactory.getLog(MakeContentUniqueIdCommand.class);

  public static String getId(String originalName) {

    // Create a new one
    StringBuilder sb = new StringBuilder();
    String name = originalName.toLowerCase();
    final int len = name.length();
    char lastChar = '_';
    for (int i = 0; i < len; i++) {
      char c = name.charAt(i);
      if (allowedChars.indexOf(name.charAt(i)) > -1) {
        sb.append(c);
        lastChar = c;
      } else if (c == '&') {
        sb.append("and");
        lastChar = '&';
      } else if (c == ' ' || c == '-') {
        if (lastChar != '-') {
          sb.append("-");
        }
        lastChar = '-';
      }
    }
    return sb.toString();
  }

}
