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


  /**
   * The Foundation label variant for a role badge, chosen by privilege level.
   *
   * <p>The colour carries the escalation ladder rather than decorating it, so "who holds elevated
   * access" is answerable at a glance on /admin/users -- which is the question that page exists to
   * answer. Grey, green, blue, amber reads as increasing rank; red is deliberately not used, since
   * the break-glass badge alongside these owns it and a role sharing that colour would blunt it.
   *
   * <p>Levels are the seeded lookup_role values: 70 content-editor, 80 content-manager, 90
   * community-manager, 93 data-manager, 95 ecommerce-manager, 100 admin. Banded rather than
   * enumerated so a site that adds a role at, say, 85 still gets a sensible colour instead of none.
   *
   * <p>Every variant returned clears WCAG AA (4.5:1) for small text as shipped: secondary 4.50:1,
   * primary 4.65:1, success 6.93:1 (the platform.css override, not Foundation's default), warning
   * 10.66:1.
   */
  public static String roleTierClass(int level) {
    if (level >= 100) {
      return "warning";
    }
    if (level >= 90) {
      return "primary";
    }
    if (level >= 80) {
      return "success";
    }
    return "secondary";
  }
}
