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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the link between three things that have to agree, and previously did not: the MFA
 * enrollment default, the page the installer seeds, and the page the site's own navigation sends
 * people to.
 *
 * <p>When they disagreed, the default named a page nothing created and nothing linked to, so the
 * "My Account" link was dead on every install and an MFA enforcement policy stranded everyone.
 * Asserted against the shipped SQL and layout rather than a constant, because a constant agreeing
 * with itself is what let this through.
 *
 * @author SimIS Inc.
 */
class MfaEnrollmentDefaultPageTest {

  private static String read(String path) throws IOException {
    return Files.readString(Path.of(path));
  }

  @Test
  void theDefaultEnrollmentUrlIsThePageTheInstallerSeeds() throws IOException {
    String seed = read("src/main/resources/database/install/NEW_50050__insert_my_page_web_page.sql");
    assertTrue(seed.contains("'" + MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL + "'"),
        "the installer must create the page the enrollment default names");
  }

  @Test
  void theSeededPageCarriesTheEnrollmentWidget() throws IOException {
    String seed = read("src/main/resources/database/install/NEW_50050__insert_my_page_web_page.sql");
    assertTrue(MfaEnrollmentPageCommand.containsEnrollmentWidget(seed),
        "a seeded enrollment page without the widget cannot enroll anyone");
  }

  @Test
  void theSitePropertyDefaultMatchesTheCodeDefault() throws IOException {
    String install = read("src/main/resources/database/install/NEW_10000__new_database.sql");
    assertTrue(install.contains("'mfa.enrollment.url', '" + MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL + "'"),
        "the seeded site property and MfaEnforcementCommand must not drift apart");
  }

  @Test
  void theNavigationLinkPointsAtTheSamePage() throws IOException {
    String header = read("src/main/webapp/WEB-INF/web-layouts/header/header-layout.xml");
    assertTrue(header.contains("link=\"" + MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL + "\""),
        "the header's My Account link and the enrollment page must be the same page -- "
            + "a page nothing links to is one only a redirect can reach");
    assertEquals("/my-page", MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL);
  }
}
