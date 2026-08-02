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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.persistence.cms.FunnelEventRepository;

/**
 * Recording entry point for the contact-form conversion funnel (issue #565, phase 1): page view -&gt;
 * form submitted -&gt; processed. Each call site (PageServlet, FormWidget, FormDataListWidget) calls
 * one of the three methods below immediately after its own success condition, mirroring how
 * SearchAnalyticsCommand.record() and SaveWebPageHitCommand.saveHit() wrap their own repository
 * inserts (issue #424 / pre-existing).
 * <p>
 * Which page path and which formUniqueId count as "the contact form" is admin configuration (the
 * {@code funnel.contactForm.pagePath} / {@code funnel.contactForm.formUniqueId} site properties),
 * not a hardcoded assumption -- every site names its contact page/form differently (see the shipped
 * "Contact Us Form" web-template, whose formUniqueId is derived from the page name at creation time).
 * Both are blank by default, so recording stays off until an admin opts in, the same way the
 * pre-existing #563 conversion-rate tile ships commented out until configured.
 * <p>
 * Only the contact-form funnel exists in phase 1. {@code funnel_key} is still a fixed, curated string
 * ("contact-form") rather than the raw pagePath/formUniqueId themselves, so a later phase's newsletter
 * or solution-page funnel (which may span several pages/forms) can share this table without a schema
 * change -- see funnel_events' own migration comment.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class FunnelEventCommand {

  public static final String CONTACT_FORM_FUNNEL_KEY = "contact-form";

  public static final String STAGE_VIEW = "view";
  public static final String STAGE_SUBMITTED = "submitted";
  public static final String STAGE_PROCESSED = "processed";

  static final String PAGE_PATH_PROPERTY = "funnel.contactForm.pagePath";
  static final String FORM_UNIQUE_ID_PROPERTY = "funnel.contactForm.formUniqueId";

  /** Called alongside SaveWebPageHitCommand.saveHit() in PageServlet, for every rendered page. */
  public static void recordContactFormPageView(String pagePath, String sessionId) {
    if (!matchesConfiguredValue(pagePath, LoadSitePropertyCommand.loadByName(PAGE_PATH_PROPERTY))) {
      return;
    }
    FunnelEventRepository.record(CONTACT_FORM_FUNNEL_KEY, STAGE_VIEW, sessionId);
  }

  /** Called from FormWidget.post() immediately after FormDataRepository.save() succeeds. */
  public static void recordContactFormSubmitted(String formUniqueId, String sessionId) {
    if (!matchesConfiguredValue(formUniqueId, LoadSitePropertyCommand.loadByName(FORM_UNIQUE_ID_PROPERTY))) {
      return;
    }
    FunnelEventRepository.record(CONTACT_FORM_FUNNEL_KEY, STAGE_SUBMITTED, sessionId);
  }

  /**
   * Called from FormDataListWidget's "Mark as Processed" handler, after FormDataRepository.markAsProcessed()
   * succeeds. The event fires days later from an admin's own session, so {@code sessionId} must be the
   * original submitter's session id (the form_data row's own stored value), not the admin's current one.
   */
  public static void recordContactFormProcessed(String formUniqueId, String sessionId) {
    if (!matchesConfiguredValue(formUniqueId, LoadSitePropertyCommand.loadByName(FORM_UNIQUE_ID_PROPERTY))) {
      return;
    }
    FunnelEventRepository.record(CONTACT_FORM_FUNNEL_KEY, STAGE_PROCESSED, sessionId);
  }

  /** Never matches a blank candidate against a blank/unset configuration -- both must be real values. */
  static boolean matchesConfiguredValue(String candidate, String configuredValue) {
    return StringUtils.isNotBlank(candidate) && StringUtils.isNotBlank(configuredValue) && configuredValue.equals(candidate);
  }
}
