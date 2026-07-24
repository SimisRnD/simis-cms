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

import com.simisinc.platform.presentation.controller.UserSession;

/**
 * The builder-vs-editor permission split for the visual editor (Project #6), expressed as
 * closed-by-default capability checks.
 *
 * <p>Two tiers, deliberately distinct:
 * <ul>
 * <li><b>Editors</b> may <b>edit content</b> within guardrails (text in blocks) -- the roles
 * {@code content-editor}, {@code content-manager}, {@code admin}.</li>
 * <li><b>Builders</b> may additionally <b>change page structure</b> (layout, widget arrangement,
 * CSS) -- {@code content-manager} and {@code admin}. {@code content-editor} is intentionally excluded:
 * authors get content guardrails, designers get the canvas.</li>
 * </ul>
 *
 * <p><b>Closed-by-default.</b> Both checks return {@code false} unless the user explicitly holds a
 * granting role -- a null session, an empty role list, or an unrelated role all deny. This is the
 * inverse of v1's allow-unless-declared posture, and it is the model the later editor phases gate on.
 * As the phases land, the ad-hoc {@code hasRole("admin") || hasRole("content-manager")} checks
 * scattered across the content pipeline migrate to these two methods.
 *
 * @author elizabeth houser
 */
public class EditorPermissionCommand {

  // The content-authoring tier and the builder tier above it.
  private static final String[] CONTENT_EDITOR_ROLES = { "admin", "content-manager", "content-editor" };
  // The layout/structure tier only. content-editor is deliberately absent.
  private static final String[] LAYOUT_BUILDER_ROLES = { "admin", "content-manager" };

  private EditorPermissionCommand() {
    // Static command
  }

  /** @return true only if the user may edit content (editor tier or above). */
  public static boolean canEditContent(UserSession userSession) {
    return hasAnyRole(userSession, CONTENT_EDITOR_ROLES);
  }

  /** @return true only if the user may change page layout/structure (builder tier). */
  public static boolean canBuildLayout(UserSession userSession) {
    return hasAnyRole(userSession, LAYOUT_BUILDER_ROLES);
  }

  private static boolean hasAnyRole(UserSession userSession, String[] roles) {
    if (userSession == null) {
      // Closed-by-default: no session means no capability.
      return false;
    }
    for (String role : roles) {
      if (userSession.hasRole(role)) {
        return true;
      }
    }
    return false;
  }
}
