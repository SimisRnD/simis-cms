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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;

/**
 * Tests the content API's access check (issue #1701).
 *
 * <p>
 * The headline is {@link #contentOnlyOnAPageTheCallerCannotOpenIsRefused()}: before this command,
 * any holder of an app key could read any content record by uniqueId, and the key is not a
 * credential. The rest guard the ways this could go wrong in the other direction -- refusing reads
 * that should succeed, or failing open.
 * </p>
 */
class ValidateApiAccessToContentCommandTest {

  private static final String UNIQUE_ID = "staff-handbook";

  private WebPage pageRendering(String link, String reference) {
    WebPage webPage = new WebPage();
    webPage.setLink(link);
    webPage.setPageXml("<page><section><column><widget name=\"content\">" + reference
        + "</widget></column></section></page>");
    return webPage;
  }

  private List<WebPage> pages(WebPage... items) {
    List<WebPage> list = new ArrayList<>();
    for (WebPage w : items) {
      list.add(w);
    }
    return list;
  }

  /** Enforcement on, and the given pages are what the repository returns. */
  private MockedStatic<LoadSitePropertyCommand> enforcementOn() {
    MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
    property.when(() -> LoadSitePropertyCommand
        .loadByName(ValidateApiAccessToContentCommand.PROPERTY_ENFORCE)).thenReturn("true");
    return property;
  }

  // --- the defect this exists to fix ---

  @Test
  void contentOnlyOnAPageTheCallerCannotOpenIsRefused() {
    WebPage gated = pageRendering("/employee-handbook", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = enforcementOn();
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(WebPageRepository::findAll).thenReturn(pages(gated));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(gated), any())).thenReturn(false);

      assertFalse(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null),
          "an app key alone must not read content off a page the caller cannot open");
    }
  }

  @Test
  void contentOnAPageTheCallerCanOpenIsAllowed() {
    WebPage open = pageRendering("/about-us", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = enforcementOn();
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(WebPageRepository::findAll).thenReturn(pages(open));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(open), any())).thenReturn(true);

      assertTrue(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, new User()));
    }
  }

  /**
   * Any page, not every page. A block on both a public and a staff-only page is already public --
   * requiring every page to pass would refuse a read the caller could get by opening the public
   * page, protecting nothing and breaking a legitimate call.
   */
  @Test
  void oneOpenPageIsEnoughEvenWhenAnotherPageIsGated() {
    WebPage gated = pageRendering("/employee-handbook", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    WebPage open = pageRendering("/about-us", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = enforcementOn();
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(WebPageRepository::findAll).thenReturn(pages(gated, open));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(gated), any())).thenReturn(false);
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(open), any())).thenReturn(true);

      assertTrue(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null));
    }
  }

  /** The inline-embed reference form, not just the widget-preference one. */
  @Test
  void theInlineEmbedReferenceFormIsMatchedToo() {
    WebPage gated = pageRendering("/employee-handbook", "${uniqueId:" + UNIQUE_ID + "}");
    try (MockedStatic<LoadSitePropertyCommand> property = enforcementOn();
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      repo.when(WebPageRepository::findAll).thenReturn(pages(gated));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(gated), any())).thenReturn(false);

      assertFalse(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null),
          "a page embedding content inline gates it the same as a content widget does");
    }
  }

  /**
   * Orphan content: no page renders it, so there is no page gate to inherit. Allowed deliberately
   * -- denying would break content reached outside the page system with a 404 that reads as a
   * missing record rather than a policy decision.
   */
  @Test
  void contentOnNoPageAtAllIsAllowed() {
    WebPage unrelated = pageRendering("/about-us", "<uniqueId>something-else</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = enforcementOn();
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class)) {
      repo.when(WebPageRepository::findAll).thenReturn(pages(unrelated));

      assertTrue(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null));
    }
  }

  // --- failing open is the thing to guard hardest ---

  /**
   * The window between deploying the code and running the migration: the row does not exist yet.
   * loadByNameAsBoolean would read that as false and disable the check for every existing
   * deployment, which is exactly the hole this closes -- so a missing row must enforce.
   */
  @Test
  void aMissingPropertyRowStillEnforces() {
    WebPage gated = pageRendering("/employee-handbook", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand
          .loadByName(ValidateApiAccessToContentCommand.PROPERTY_ENFORCE)).thenReturn(null);
      repo.when(WebPageRepository::findAll).thenReturn(pages(gated));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(gated), any())).thenReturn(false);

      assertFalse(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null),
          "an absent property must not read as 'off' -- that would reopen the hole on every upgrade");
    }
  }

  @Test
  void anEmptyPropertyValueStillEnforces() {
    WebPage gated = pageRendering("/employee-handbook", "<uniqueId>" + UNIQUE_ID + "</uniqueId>");
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> repo = mockStatic(WebPageRepository.class);
        MockedStatic<ValidateApiAccessToWebPageCommand> gate = mockStatic(ValidateApiAccessToWebPageCommand.class)) {
      property.when(() -> LoadSitePropertyCommand
          .loadByName(ValidateApiAccessToContentCommand.PROPERTY_ENFORCE)).thenReturn("   ");
      repo.when(WebPageRepository::findAll).thenReturn(pages(gated));
      gate.when(() -> ValidateApiAccessToWebPageCommand.hasAccess(eq(gated), any())).thenReturn(false);

      assertFalse(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null));
    }
  }

  /** The documented escape hatch: an explicit false restores the old behaviour. */
  @Test
  void anExplicitFalseTurnsTheCheckOff() {
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand
          .loadByName(ValidateApiAccessToContentCommand.PROPERTY_ENFORCE)).thenReturn("false");

      // No repository stub at all: with the check off, nothing should be looked up.
      assertTrue(ValidateApiAccessToContentCommand.hasAccess(UNIQUE_ID, null));
    }
  }

  @Test
  void aBlankUniqueIdIsRefused() {
    assertFalse(ValidateApiAccessToContentCommand.hasAccess("  ", null));
    assertFalse(ValidateApiAccessToContentCommand.hasAccess(null, null));
  }
}
