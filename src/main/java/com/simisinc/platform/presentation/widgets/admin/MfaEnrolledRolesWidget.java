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

package com.simisinc.platform.presentation.widgets.admin;

import com.simisinc.platform.infrastructure.persistence.RoleRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Shows the roles that already have at least one MFA-enrolled member, on the MFA Enforcement
 * Settings page -- the page's own help text warns that enabling enforcement for a role with no
 * enrolled member locks out every member of it, so this gives the admin that answer before they
 * flip the switch instead of finding out after.
 *
 * @author SimIS
 * @created 8/16/2026
 */
public class MfaEnrolledRolesWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/mfa-enrolled-roles.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      return context;
    }

    context.getRequest().setAttribute("roleList", RoleRepository.findAllWithMfaEnrolledMember());

    context.setJsp(JSP);
    return context;
  }
}
