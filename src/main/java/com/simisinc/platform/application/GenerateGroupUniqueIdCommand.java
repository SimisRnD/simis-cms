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

package com.simisinc.platform.application;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/22 8:58 AM
 */
public class GenerateGroupUniqueIdCommand {

  private static Log LOG = LogFactory.getLog(GenerateGroupUniqueIdCommand.class);

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz0123456789";
  private static final String allowedFinalChars = "abcdefghijklmnopqrstuvwyxz0123456789-";

  public static String generateUniqueId(Group previousGroup, Group group) {

    // Use an existing uniqueId
    if (previousGroup != null && previousGroup.getUniqueId() != null) {
      // See if the uniqueId changed
      if (previousGroup.getUniqueId().equals(group.getUniqueId())) {
        return previousGroup.getUniqueId();
      }
    }

    // See if the specified one is unique
    if (StringUtils.isNotBlank(group.getUniqueId()) &&
        StringUtils.containsOnly(group.getUniqueId(), allowedFinalChars) &&
        GroupRepository.findByUniqueId(group.getUniqueId()) == null) {
      return group.getUniqueId();
    }

    // Create a new uniqueId
    String name = group.getName();
    String value = parseToValidValue(name);

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (GroupRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

  public static String parseToValidValue(String nameValue) {
    String name = nameValue.toLowerCase();
    StringBuilder sb = new StringBuilder();
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
    return value;
  }

  public static boolean isValid(String uniqueId) {
    if (StringUtils.isBlank(uniqueId) || !StringUtils.containsOnly(uniqueId, allowedFinalChars)) {
      return false;
    }
    return true;
  }
}
