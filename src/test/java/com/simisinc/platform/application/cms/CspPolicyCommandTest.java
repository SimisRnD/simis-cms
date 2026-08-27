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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Tests the report-only policy header.
 *
 * @author elizabeth houser
 */
class CspPolicyCommandTest {

  private MockedStatic<LoadSitePropertyCommand> configured(String policy) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByName(CspPolicyCommand.REPORT_ONLY_PROPERTY)).thenReturn(policy);
    return m;
  }

  @Test
  void noPolicyMeansNoHeaderAtAll() {
    // How it ships. Nothing about the enforced policy changes, and the endpoint accepts nothing.
    try (MockedStatic<LoadSitePropertyCommand> m = configured("")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
      assertFalse(CspPolicyCommand.isReportingEnabled());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = configured(null)) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
      assertFalse(CspPolicyCommand.isReportingEnabled());
    }
    try (MockedStatic<LoadSitePropertyCommand> m = configured("   ")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
    }
  }

  @Test
  void aPolicyGetsBothReportingDirectivesAdded() {
    // Without one of these the browser evaluates the policy and reports to nobody, which is
    // indistinguishable from a policy that passes -- the most misleading way this could fail.
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self'")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertTrue(policy.startsWith("default-src 'self'"));
      assertTrue(policy.contains("report-uri /csp-report"));
      assertTrue(policy.contains("report-to csp-endpoint"));
      assertTrue(CspPolicyCommand.isReportingEnabled());
    }
  }

  @Test
  void anAdministratorsOwnReportingDirectivesAreNotDuplicated() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self'; report-uri /elsewhere")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertEquals(1, policy.split("report-uri", -1).length - 1);
      assertTrue(policy.contains("/elsewhere"));
    }
  }

  @Test
  void theNoncePlaceholderIsFilledInPerRequest() {
    // An administrator cannot know the nonce when typing the policy, so a candidate carrying
    // script-src would be untestable without this
    try (MockedStatic<LoadSitePropertyCommand> m = configured("script-src 'self' 'nonce-{nonce}'")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertTrue(policy.contains("'nonce-abc123'"));
      assertFalse(policy.contains("{nonce}"));
    }
  }

  @Test
  void aTrailingSemicolonDoesNotProduceAnEmptyDirective() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured("default-src 'self';  ")) {
      String policy = CspPolicyCommand.reportOnlyPolicy("abc123");
      assertFalse(policy.contains(";;"));
      assertTrue(policy.startsWith("default-src 'self'; report-uri"));
    }
  }

  @Test
  void aPolicyOfOnlySemicolonsIsTreatedAsUnset() {
    try (MockedStatic<LoadSitePropertyCommand> m = configured(";;;")) {
      assertNull(CspPolicyCommand.reportOnlyPolicy("abc123"));
    }
  }

  @Test
  void theReportingEndpointsHeaderNamesTheSamePathTheServletServes() {
    assertEquals("csp-endpoint=\"/csp-report\"", CspPolicyCommand.reportingEndpointsHeader());
    assertEquals("/csp-report", CspPolicyCommand.REPORT_PATH);
  }
}
