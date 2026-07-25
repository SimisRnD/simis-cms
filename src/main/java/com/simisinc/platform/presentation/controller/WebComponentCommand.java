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
import java.util.List;

/**
 * Verifies a user's access to the specified web component.
 *
 * <p>Access decisions are made against an explicit {@link AccessPolicy}:
 * <ul>
 *   <li>{@link AccessPolicy#PUBLIC} — a resource with no declared roles or groups is
 *       reachable by any visitor (the correct posture for public CMS content).</li>
 *   <li>{@link AccessPolicy#RESTRICTED} — a resource with no declared roles or groups
 *       is denied; an explicit role or group match is required (the correct posture for
 *       any newly-declared protected resource).</li>
 * </ul>
 *
 * <p>The {@link AccessPolicy} parameter is required on every call so that the access
 * posture is visible at the call site. This aligns with the deny-by-default posture of
 * {@code EditorPermissionCommand} and closes SSP AC-3 / AC-6 (issue #299).
 *
 * @author matt rajkowski
 * @created 4/10/2022 8:51 AM
 */
public class WebComponentCommand implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(WebComponentCommand.class);

  /**
   * Declares the access posture of a resource when no roles or groups are configured.
   */
  public enum AccessPolicy {
    /** No role/group restriction — any visitor is permitted (public CMS content). */
    PUBLIC,
    /** Explicit role or group required — no declaration means no access. */
    RESTRICTED
  }

  public static boolean allowsUser(Page page, UserSession userSession, AccessPolicy policy) {
    return allowsUser(page.getRoles(), page.getGroups(), userSession, policy);
  }

  public static boolean allowsUser(Section section, UserSession userSession, AccessPolicy policy) {
    return allowsUser(section.getRoles(), section.getGroups(), userSession, policy);
  }

  public static boolean allowsUser(Column column, UserSession userSession, AccessPolicy policy) {
    return allowsUser(column.getRoles(), column.getGroups(), userSession, policy);
  }

  public static boolean allowsUser(Widget widget, UserSession userSession, AccessPolicy policy) {
    return allowsUser(widget.getRoles(), widget.getGroups(), userSession, policy);
  }

  public static boolean allowsUser(List<String> roles, List<String> groups, UserSession userSession, AccessPolicy policy) {
    if (roles.isEmpty() && groups.isEmpty()) {
      return policy == AccessPolicy.PUBLIC;
    }

    // Roles can be for a user that is either logged in/out
    boolean roleAllowed = roles.isEmpty();
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
