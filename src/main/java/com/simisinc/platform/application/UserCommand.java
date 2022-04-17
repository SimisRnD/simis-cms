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

import com.simisinc.platform.domain.model.User;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Displays information about a user
 *
 * @author matt rajkowski
 * @created 8/8/18 2:27 PM
 */
public class UserCommand {

  private static Log LOG = LogFactory.getLog(UserCommand.class);

  public static String name(Long userId) {
    User user = LoadUserCommand.loadUser(userId);
    if (user == null) {
      return "Unknown";
    }
    return name(user);
  }

  public static String name(User user) {
    if (user == null) {
      return "Unknown";
    }
    if (StringUtils.isNotBlank(user.getNickname())) {
      return user.getNickname();
    }
    if (StringUtils.isNotBlank(user.getFullName())) {
      return user.getFullName();
    }
    return "Unknown";
  }

  public static User findById(Long userId) {
    return LoadUserCommand.loadUser(userId);
  }

}
