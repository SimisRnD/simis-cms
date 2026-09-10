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

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Builds the Content-Security-Policy-Report-Only header used to test a stricter policy against real
 * traffic before enforcing it.
 *
 * <p>
 * The remaining directives in #1430 cannot be written by reading the source. Every third-party
 * integration arrives as a &lt;script src&gt; and then calls endpoints of its own: Stripe's script
 * talks to api.stripe.com, gtag builds google-analytics.com at runtime, Square reaches
 * pci-connect.squareup.com. None of those hosts appear anywhere in this repository, and they change
 * when a vendor updates their SDK, with no commit here. Guessing at connect-src means guessing at
 * whether checkout still works.
 * </p>
 *
 * <p>
 * Report-only is how that guess is replaced with evidence. The browser evaluates the candidate
 * policy, refuses nothing, and posts a report for anything that would have been blocked.
 * </p>
 *
 * <p>
 * The candidate is a site property rather than a constant, which is the opposite of how the enforced
 * policy is handled, and deliberately so: a report-only policy cannot break a page, so the usual
 * reason to keep CSP out of an administrator's hands does not apply -- while the ability to adjust
 * the candidate and watch what happens, without a release, is the entire point of an inventory.
 * Leaving it blank turns the whole mechanism off, which is how it ships.
 * </p>
 *
 * @author SimIS Inc.
 */
public class CspPolicyCommand {

  /** The candidate policy to test. Blank disables report-only entirely. */
  public static final String REPORT_ONLY_PROPERTY = "security.csp.reportOnly";

  /** Where violation reports are posted. Matches CspReportController's servlet mapping. */
  public static final String REPORT_PATH = "/csp-report";

  /** The name tying the Reporting-Endpoints header to the report-to directive. */
  public static final String REPORT_ENDPOINT_NAME = "csp-endpoint";

  /** Replaced with the request's script nonce, so a candidate can carry script-src. */
  public static final String NONCE_PLACEHOLDER = "{nonce}";

  private CspPolicyCommand() {
    // Static utility, not instantiated
  }

  /**
   * The report-only policy for this request, or null when none is configured.
   *
   * @param cspNonce the per-request script nonce
   * @return a complete policy including its reporting directives, or null to send no header
   */
  public static String reportOnlyPolicy(String cspNonce) {
    String configured = LoadSitePropertyCommand.loadByName(REPORT_ONLY_PROPERTY);
    if (StringUtils.isBlank(configured)) {
      return null;
    }
    String policy = configured.trim();
    // A candidate that carries script-src needs this request's nonce, which the administrator
    // cannot know when typing the policy
    if (cspNonce != null) {
      policy = policy.replace(NONCE_PLACEHOLDER, cspNonce);
    }
    // Strip trailing semicolons so appending below never produces an empty directive
    while (policy.endsWith(";")) {
      policy = policy.substring(0, policy.length() - 1).trim();
    }
    if (policy.isEmpty()) {
      return null;
    }
    // Add the reporting directives when they are absent. Without one of these the browser evaluates
    // the policy and reports to nobody, which looks exactly like a policy that passes -- the single
    // most misleading way this feature could fail. Both are added because browsers disagree about
    // which they honor: report-uri is deprecated and still the one several of them use.
    if (!policy.contains("report-uri")) {
      policy = policy + "; report-uri " + REPORT_PATH;
    }
    if (!policy.contains("report-to")) {
      policy = policy + "; report-to " + REPORT_ENDPOINT_NAME;
    }
    return policy;
  }

  /** The Reporting-Endpoints header value that gives report-to its destination. */
  public static String reportingEndpointsHeader() {
    return REPORT_ENDPOINT_NAME + "=\"" + REPORT_PATH + "\"";
  }

  /** True when violation reports should be accepted, i.e. a candidate policy is configured. */
  public static boolean isReportingEnabled() {
    return StringUtils.isNotBlank(LoadSitePropertyCommand.loadByName(REPORT_ONLY_PROPERTY));
  }
}
