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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Tests the enrollment-page check that stops MFA enforcement from being enabled against a page that
 * cannot enroll anyone.
 *
 * @author SimIS Inc.
 */
class MfaEnrollmentPageCommandTest {

  @Test
  void pageXmlWithTheEnrollmentWidgetIsAccepted() {
    String pageXml = "<page role=\"users\" title=\"My Profile\">"
        + "<section><column class=\"small-12 cell\">"
        + "<widget name=\"myMfaSettings\"><title>Two-Factor Authentication</title></widget>"
        + "</column></section></page>";
    assertTrue(MfaEnrollmentPageCommand.containsEnrollmentWidget(pageXml));
  }

  @Test
  void singleQuotedWidgetNameIsAccepted() {
    assertTrue(MfaEnrollmentPageCommand.containsEnrollmentWidget(
        "<page><section><column><widget name='myMfaSettings' /></column></section></page>"));
  }

  @Test
  void pageXmlWithoutTheEnrollmentWidgetIsRejected() {
    // The shipped "About Us" style page -- a real page, but nobody can enroll on it
    String pageXml = "<page><section><column class=\"small-12 cell\">"
        + "<widget name=\"content\"><uniqueId>about-us</uniqueId></widget>"
        + "</column></section></page>";
    assertFalse(MfaEnrollmentPageCommand.containsEnrollmentWidget(pageXml));
  }

  @Test
  void widgetNameMentionedOnlyAsTextIsRejected() {
    // Prose naming the widget is not the widget; matching on the name attribute avoids this
    assertFalse(MfaEnrollmentPageCommand.containsEnrollmentWidget(
        "<page><section><column><widget name=\"content\">"
            + "<html>Add the myMfaSettings widget here later</html>"
            + "</widget></column></section></page>"));
  }

  @Test
  void blankPageXmlIsRejected() {
    // The stub row the CMS creates when an admin visits an unknown link -- this was the live case
    assertFalse(MfaEnrollmentPageCommand.containsEnrollmentWidget(null));
    assertFalse(MfaEnrollmentPageCommand.containsEnrollmentWidget(""));
    assertFalse(MfaEnrollmentPageCommand.containsEnrollmentWidget("   "));
  }

  @Test
  void blankLinkIsNotUsable() {
    // No database access needed: a blank link short-circuits before any lookup
    assertFalse(MfaEnrollmentPageCommand.isUsableEnrollmentPage(null));
    assertFalse(MfaEnrollmentPageCommand.isUsableEnrollmentPage(""));
    assertFalse(MfaEnrollmentPageCommand.isUsableEnrollmentPage("  "));
  }
}
