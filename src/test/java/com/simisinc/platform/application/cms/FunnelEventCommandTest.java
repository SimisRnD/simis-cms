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

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.persistence.cms.FunnelEventRepository;

/**
 * Verifies the contact-form funnel's admin-configured page/form matching (issue #565, phase 1) --
 * the part of {@link FunnelEventCommand} with actual branching logic. The three call sites
 * (PageServlet, FormWidget, FormDataListWidget) are each covered separately for wiring; this class
 * covers the matching/gating decision itself in isolation.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
class FunnelEventCommandTest {

  @Test
  void recordContactFormPageViewRecordsWhenThePagePathMatchesConfiguration() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.PAGE_PATH_PROPERTY)).thenReturn("/contact-us");

      FunnelEventCommand.recordContactFormPageView("/contact-us", "session-1");

      repository.verify(() -> FunnelEventRepository.record(
          FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY, FunnelEventCommand.STAGE_VIEW, "session-1"));
    }
  }

  @Test
  void recordContactFormPageViewSkipsWhenThePagePathDoesNotMatch() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.PAGE_PATH_PROPERTY)).thenReturn("/contact-us");

      FunnelEventCommand.recordContactFormPageView("/careers", "session-1");

      repository.verifyNoInteractions();
    }
  }

  @Test
  void recordContactFormPageViewSkipsWhenNotConfigured() {
    // Blank by default (see the funnel_events migration) -- must stay inert until an admin opts in
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.PAGE_PATH_PROPERTY)).thenReturn(null);

      FunnelEventCommand.recordContactFormPageView("/contact-us", "session-1");

      repository.verifyNoInteractions();
    }
  }

  @Test
  void recordContactFormSubmittedRecordsWhenTheFormUniqueIdMatchesConfiguration() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.FORM_UNIQUE_ID_PROPERTY)).thenReturn("contact-us");

      FunnelEventCommand.recordContactFormSubmitted("contact-us", "session-2");

      repository.verify(() -> FunnelEventRepository.record(
          FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY, FunnelEventCommand.STAGE_SUBMITTED, "session-2"));
    }
  }

  @Test
  void recordContactFormSubmittedSkipsWhenTheFormUniqueIdDoesNotMatch() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.FORM_UNIQUE_ID_PROPERTY)).thenReturn("contact-us");

      FunnelEventCommand.recordContactFormSubmitted("newsletter", "session-2");

      repository.verifyNoInteractions();
    }
  }

  @Test
  void recordContactFormProcessedRecordsWhenTheFormUniqueIdMatchesConfiguration() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.FORM_UNIQUE_ID_PROPERTY)).thenReturn("contact-us");

      FunnelEventCommand.recordContactFormProcessed("contact-us", "original-submitter-session");

      repository.verify(() -> FunnelEventRepository.record(
          FunnelEventCommand.CONTACT_FORM_FUNNEL_KEY, FunnelEventCommand.STAGE_PROCESSED, "original-submitter-session"));
    }
  }

  @Test
  void recordContactFormProcessedSkipsWhenTheFormUniqueIdDoesNotMatch() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<FunnelEventRepository> repository = mockStatic(FunnelEventRepository.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName(FunnelEventCommand.FORM_UNIQUE_ID_PROPERTY)).thenReturn("contact-us");

      FunnelEventCommand.recordContactFormProcessed("newsletter", "original-submitter-session");

      repository.verifyNoInteractions();
    }
  }

  @Test
  void matchesConfiguredValueRejectsBlankCandidatesAndBlankConfiguration() {
    Assertions.assertFalse(FunnelEventCommand.matchesConfiguredValue(null, "contact-us"));
    Assertions.assertFalse(FunnelEventCommand.matchesConfiguredValue("contact-us", null));
    Assertions.assertFalse(FunnelEventCommand.matchesConfiguredValue("", ""));
    Assertions.assertTrue(FunnelEventCommand.matchesConfiguredValue("contact-us", "contact-us"));
  }
}
