/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Determines whether a REST API caller may see a given web page (issue #412), mirroring
 * {@code PageServlet.service()}'s combined gate for a live, non-editor site visitor:
 * {@link ValidateUserAccessToWebPageCommand#hasAccess} (draft/blank-pageXml + widget/section/
 * column role, group, and capability gating) plus the {@code publishAt}/{@code expiresAt}
 * scheduling window PageServlet enforces separately for non-admin/content-manager users.
 * <p>
 * {@code WebPage.enabled} is deliberately NOT checked here -- {@code PageServlet} never checks it
 * either; it is not part of the live-serving gate.
 * </p>
 * <p>
 * Bridges the REST layer's plain {@link User} (set by {@code RestRequestFilter}, either a real
 * authenticated user loaded via {@code LoadUserCommand.loadUser} or a bare guest user) into the
 * {@link UserSession} that the shared access-check commands require, via
 * {@code UserSession.login(User)} -- the same role/group/capability lists a JSP-session login
 * would populate.
 * </p>
 *
 * @author SimIS Inc.
 */
public class ValidateApiAccessToWebPageCommand {

  private ValidateApiAccessToWebPageCommand() {
    // Static utility, not instantiated
  }

  public static boolean hasAccess(WebPage webPage, User user) {
    if (webPage == null) {
      return false;
    }
    UserSession userSession = new UserSession();
    if (user != null) {
      userSession.login(user);
    }
    if (!ValidateUserAccessToWebPageCommand.hasAccess(webPage.getLink(), userSession)) {
      return false;
    }
    if (!userSession.hasRole("admin") && !userSession.hasRole("content-manager")) {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      if (webPage.getPublishAt() != null && webPage.getPublishAt().after(now)) {
        return false;
      }
      if (webPage.getExpiresAt() != null && webPage.getExpiresAt().before(now)) {
        return false;
      }
    }
    return true;
  }
}
