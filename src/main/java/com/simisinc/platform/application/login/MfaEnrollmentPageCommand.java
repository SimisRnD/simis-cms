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

package com.simisinc.platform.application.login;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Verifies that a configured MFA enrollment page can actually be used to enroll.
 *
 * <p>MFA enforcement redirects every non-exempt request to the enrollment URL, and the enrollment
 * URL is the only page it exempts. If that page does not exist, or exists but does not carry the
 * {@code myMfaSettings} widget, there is no way to enroll and no way to reach the admin screen that
 * would turn enforcement back off -- the only remaining recovery is a direct database update. This
 * is not hypothetical: the shipped default named a page no installer ever created, so on a fresh
 * install the default itself was the broken case. The default is now {@code /my-page}, which is
 * seeded, and this check is what keeps a hand-edited value from recreating the problem.
 *
 * <p>Callers use this to refuse the enforcement setting rather than to soften enforcement at request
 * time. Failing the save keeps the door open; failing open at request time would defeat the control.
 *
 * @author SimIS Inc.
 */
public class MfaEnrollmentPageCommand {

  private static Log LOG = LogFactory.getLog(MfaEnrollmentPageCommand.class);

  /** The widget that renders the enrollment UI (see MyMfaSettingsWidget / widget-library.xml) */
  public static final String ENROLLMENT_WIDGET_NAME = "myMfaSettings";

  private MfaEnrollmentPageCommand() {
  }

  /**
   * Returns {@code true} when the given link resolves to a web page that renders the MFA enrollment
   * widget, so a user redirected there can actually complete enrollment.
   *
   * <p>A blank link is not usable. A lookup failure returns {@code false}: the caller's response is
   * to refuse a settings change, so an unverifiable page is treated as unusable rather than assumed
   * good.
   */
  public static boolean isUsableEnrollmentPage(String link) {
    if (StringUtils.isBlank(link)) {
      return false;
    }
    WebPage webPage;
    try {
      webPage = WebPageRepository.findByLink(link.trim());
    } catch (Exception e) {
      LOG.warn("Could not verify MFA enrollment page '" + link + "'", e);
      return false;
    }
    if (webPage == null) {
      return false;
    }
    // A page row can exist with no layout yet -- the CMS creates a stub the first time an admin
    // visits an unknown link, which renders only the "this is a new page" placeholder
    String pageXml = webPage.getPageXml();
    if (StringUtils.isBlank(pageXml)) {
      return false;
    }
    return containsEnrollmentWidget(pageXml);
  }

  /**
   * Returns {@code true} if the page XML declares the enrollment widget. Matches on the widget name
   * attribute so that a page mentioning the name in unrelated text does not count.
   */
  public static boolean containsEnrollmentWidget(String pageXml) {
    if (StringUtils.isBlank(pageXml)) {
      return false;
    }
    return pageXml.contains("name=\"" + ENROLLMENT_WIDGET_NAME + "\"")
        || pageXml.contains("name='" + ENROLLMENT_WIDGET_NAME + "'");
  }
}
