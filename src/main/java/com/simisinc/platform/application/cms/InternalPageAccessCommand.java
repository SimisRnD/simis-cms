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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.UserSession;

/**
 * Decides whether a request for a web page marked "internal" must be refused (issue #1688).
 *
 * <p>
 * Before this command, {@link WebPage#isInternal()} was a label: it drove the "Hide Internal Pages"
 * filter on /admin/web-pages and a badge, and nothing else. Ticking it restricted nobody, while the
 * admin UI's own help text said so in wording most operators read as reassurance rather than as a
 * warning. This turns the flag into a real page-route gate.
 * </p>
 *
 * <p>
 * <b>This is a page-route gate, not a content-object gate.</b> It governs whether the page is
 * served, listed and indexed. It does not protect the underlying content records: {@code GET
 * /api/content/{uniqueId}} returns rendered content with no access check at all, and no page gate
 * can cover it because {@code content_unique_id} has no foreign key to {@code web_pages}. Say this
 * out loud wherever the flag is described to an operator.
 * </p>
 *
 * <p>
 * <b>Composition is AND.</b> This gate is conjunctive with whatever {@code role=}/{@code group=}/
 * {@code capability=} the page XML already carries, so the effective audience is the intersection.
 * It can only narrow access, never widen it. Deliberately not implemented by injecting the staff
 * group into {@code Page.getGroups()} -- {@code WebComponentCommand} ORs across that list
 * ({@code WebComponentCommand.java:104-110}), so injection would <i>widen</i> access on any page
 * already carrying a {@code group=}.
 * </p>
 *
 * <p>
 * <b>Blank property means off.</b> {@code security.internalPages.group} ships empty, so an upgrade
 * is a strict no-op and {@code internal} keeps behaving exactly as it does today until an
 * administrator opts in.
 * </p>
 *
 * @author matt rajkowski
 * @created 8/31/26 9:00 AM
 */
public class InternalPageAccessCommand {

  /** Empty means the gate is inert; see the class notes. */
  public static final String PROPERTY_INTERNAL_PAGE_GROUP = "security.internalPages.group";

  private static Log LOG = LogFactory.getLog(InternalPageAccessCommand.class);

  private InternalPageAccessCommand() {
    // Static command
  }

  /**
   * Returns true when this request for this page must be refused.
   *
   * <p>
   * The order of the checks below is a hard constraint, not a style choice:
   * </p>
   * <ol>
   * <li>The not-internal exit comes <b>before any property read</b>, which keeps the command usable
   * from POJO tests that have no {@code DataSource} and off the hot path for the 99% of pages that
   * are not internal.</li>
   * <li>The content-editor bypass comes before the lockout exemptions so the tier that maintains
   * internal pages can always reach them. It also means <b>internal is not confidential from
   * content editors</b> -- {@code PageServlet?action=getWidgetContent} is gated on exactly this
   * predicate, so any narrower bypass here would leave that as an escalation path.</li>
   * <li>The two runtime exemptions keep an administrator from locking the site out of the MFA
   * enrollment page or of a shipped file-backed layout.</li>
   * <li>The property read comes last, so an unset property costs nothing.</li>
   * </ol>
   *
   * @param webPage the page being requested; null is not internal and is never blocked
   * @param userSession the requesting session; null is treated as anonymous
   * @return true when the request must be refused
   */
  public static boolean isBlocked(WebPage webPage, UserSession userSession) {
    if (webPage == null || !webPage.isInternal()) {
      return false;
    }

    // The content-editor tier (admin, content-manager, content-editor) always gets through, which is
    // what keeps a misconfigured group recoverable from the UI. canEditContent(null) is false, so an
    // anonymous session falls through rather than bypassing.
    if (EditorPermissionCommand.canEditContent(userSession)) {
      return false;
    }

    // Never gate the MFA enrollment page: a user who must enrol before going anywhere else would
    // otherwise be trapped. Read the configured value rather than hard-coding /my-page, because the
    // URL is a site property.
    if (StringUtils.equals(webPage.getLink(), MfaEnforcementCommand.getEnrollmentUrl())) {
      return false;
    }

    // A web_pages row can shadow a shipped file layout (/login, /register, /forgot-password). Such a
    // row does not supply the markup, so it must not supply the gate either -- otherwise ticking
    // "internal" on a shadow row would lock everyone out of signing in.
    if (WebPageXmlLayoutCommand.containsPage(webPage.getLink())) {
      return false;
    }

    String uniqueId = StringUtils.trimToNull(LoadSitePropertyCommand.loadByName(PROPERTY_INTERNAL_PAGE_GROUP));
    if (uniqueId == null) {
      // Feature off. This is the shipped default and must stay reachable from a broken state.
      return false;
    }

    if (userSession == null) {
      return true;
    }

    // hasGroup matches groups.unique_id and is blank- and null-list-safe (UserSession.java:219-232).
    // A uniqueId that resolves to no group therefore denies everyone outside the editor tier, which
    // is deliberate: fail closed, but stay recoverable via the bypass above.
    return !userSession.hasGroup(uniqueId);
  }
}
