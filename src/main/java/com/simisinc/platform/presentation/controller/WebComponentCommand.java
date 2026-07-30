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

package com.simisinc.platform.presentation.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * Verifies a user's access to the specified web component
 *
 * @author matt rajkowski
 * @created 4/10/2022 8:51 AM
 */
public class WebComponentCommand implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(WebComponentCommand.class);

  // Open-by-default: CMS pages/sections/columns/widgets are public unless roles/groups are declared.
  // Pages (only) additionally accept a capability= list (issue #701's hasPermission() system,
  // issue #733) -- a user satisfies the role/capability side of the check by holding EITHER a
  // listed role OR a listed capability, so existing role="..." pages are unaffected until a
  // capability="..." attribute is deliberately added alongside them.
  public static boolean allowsUser(Page page, UserSession userSession) {
    return allowsUser(page.getRoles(), page.getGroups(), page.getCapabilities(), userSession, false);
  }

  public static boolean allowsUser(Section section, UserSession userSession) {
    return allowsUser(section.getRoles(), section.getGroups(), userSession, false);
  }

  public static boolean allowsUser(Column column, UserSession userSession) {
    return allowsUser(column.getRoles(), column.getGroups(), userSession, false);
  }

  public static boolean allowsUser(Widget widget, UserSession userSession) {
    return allowsUser(widget.getRoles(), widget.getGroups(), userSession, false);
  }

  // Open-by-default convenience overload — use allowsUser(roles, groups, session, true) for deny-by-default.
  public static boolean allowsUser(List<String> roles, List<String> groups, UserSession userSession) {
    return allowsUser(roles, groups, userSession, false);
  }

  /**
   * Evaluates whether the current user satisfies the declared role and group constraints.
   *
   * @param denyWhenEmpty when {@code true}, an empty roles+groups list denies access (deny-by-default);
   *                      when {@code false}, an empty list allows everyone (open-by-default, legacy CMS behaviour).
   *                      New resources that require explicit authorisation should pass {@code true}.
   */
  public static boolean allowsUser(List<String> roles, List<String> groups, UserSession userSession, boolean denyWhenEmpty) {
    return allowsUser(roles, groups, Collections.emptyList(), userSession, denyWhenEmpty);
  }

  /**
   * As above, plus an optional capability= list (issue #733): the role side of the check is
   * satisfied by holding EITHER a listed role OR a listed capability (hasPermission()), not both.
   */
  public static boolean allowsUser(List<String> roles, List<String> groups, List<String> capabilities,
      UserSession userSession, boolean denyWhenEmpty) {
    if (roles.isEmpty() && groups.isEmpty() && capabilities.isEmpty()) {
      return !denyWhenEmpty;
    }

    // Roles can be for a user that is either logged in/out
    boolean roleAllowed = roles.isEmpty() && capabilities.isEmpty();
    for (String role : roles) {
      if ("guest".equals(role) && !userSession.isLoggedIn()) {
        roleAllowed = true;
      }
      if ("users".equals(role) && userSession.isLoggedIn()) {
        roleAllowed = true;
      }
      if (userSession.hasRole(role)) {
        roleAllowed = true;
      }
    }
    for (String capability : capabilities) {
      if (userSession.hasPermission(capability)) {
        roleAllowed = true;
      }
    }

    // Groups are for logged-in users
    boolean groupAllowed = groups.isEmpty();
    for (String group : groups) {
      if (userSession.hasGroup(group)) {
        groupAllowed = true;
      }
    }
    return roleAllowed && groupAllowed;
  }
}
