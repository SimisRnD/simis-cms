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

import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;

/**
 * Bridges a widget request to the security audit log (see {@link SaveAuditEventCommand}). It pulls the
 * acting administrator's identity -- user id, username, source IP and session id -- from the
 * {@link WidgetContext} and records an administrative or data-change event.
 *
 * <p>Presentation code (widgets, and the small number of application commands that already receive a
 * WidgetContext) should call this rather than building an {@code AuditLog} by hand, so the actor is
 * resolved consistently. Auditing is a side effect: extracting the actor and writing the record are both
 * guarded so a failure here can never break the admin action being observed.
 *
 * @author SimIS Inc.
 */
public class AuditEventCommand {

  // Event categories (Phase 2 -- authentication is Phase 1 and lives in SaveAuditEventCommand)
  public static final String USER_MANAGEMENT = "user_management";
  public static final String AUTHORIZATION = "authorization";
  public static final String CONFIGURATION = "configuration";
  public static final String CONTENT = "content";
  public static final String DATA_ACCESS = "data_access";

  // Outcomes
  public static final String SUCCESS = "success";
  public static final String FAILURE = "failure";

  private AuditEventCommand() {
    // Static utility
  }

  /**
   * Records an admin/data-change audit event, resolving the acting admin from the widget context. Never
   * throws. Callers that emit many events for one request (e.g. a bulk import loop) should resolve the
   * actor once and call {@link SaveAuditEventCommand#recordAdminEvent} directly to avoid re-loading the
   * acting user on every iteration.
   */
  public static void record(WidgetContext context, String eventCategory, String eventType, String outcome,
      String targetType, String targetId, String targetLabel, String details) {
    long actorUserId = -1L;
    String actorUsername = null;
    String sourceIp = null;
    String sessionId = null;
    try {
      actorUserId = context.getUserId();
      UserSession userSession = context.getUserSession();
      if (userSession != null) {
        sessionId = userSession.getSessionId();
        sourceIp = userSession.getIpAddress();
        // getUser() lazily loads the acting user; skip it for an unauthenticated/unknown actor
        if (userSession.getUserId() > -1L) {
          User actor = userSession.getUser();
          if (actor != null) {
            actorUsername = actor.getEmail();
          }
        }
      }
      // Prefer the live request address, matching the Phase 1 authentication events
      if (context.getRequest() != null && context.getRequest().getRemoteAddr() != null) {
        sourceIp = context.getRequest().getRemoteAddr();
      }
    } catch (Exception e) {
      // Never let actor extraction break the caller; record with whatever was resolved
    }
    SaveAuditEventCommand.recordAdminEvent(eventCategory, eventType, outcome, actorUserId, actorUsername,
        sourceIp, sessionId, targetType, targetId, targetLabel, details);
  }

  /**
   * Summarizes a user's effective roles and groups (codes and names only) as a detail string for a
   * user-management or authorization event, so a reviewer can see the resulting access without a second
   * lookup. Null-safe.
   */
  public static String describeRolesAndGroups(User user) {
    StringBuilder sb = new StringBuilder("roles=[");
    if (user != null && user.getRoleList() != null) {
      boolean first = true;
      for (Role role : user.getRoleList()) {
        if (!first) {
          sb.append(",");
        }
        sb.append(role.getCode());
        first = false;
      }
    }
    sb.append("]; groups=[");
    if (user != null && user.getGroupList() != null) {
      boolean first = true;
      for (Group group : user.getGroupList()) {
        if (!first) {
          sb.append(",");
        }
        sb.append(group.getName());
        first = false;
      }
    }
    sb.append("]");
    return sb.toString();
  }
}
