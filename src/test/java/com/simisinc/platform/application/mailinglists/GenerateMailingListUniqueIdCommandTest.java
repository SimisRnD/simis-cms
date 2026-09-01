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

package com.simisinc.platform.application.mailinglists;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;

/**
 * Issue #1724: the whole point of mailing_lists.unique_id is that it is generated once and never
 * regenerated. If a rename could change it, page configuration pointing at it would break on a
 * rename exactly the way the mailingList *name* preference already does, and the column would buy
 * nothing.
 *
 * @author elizabeth houser
 */
class GenerateMailingListUniqueIdCommandTest {

  private static MailingList list(String uniqueId, String name) {
    MailingList mailingList = new MailingList();
    mailingList.setUniqueId(uniqueId);
    mailingList.setName(name);
    return mailingList;
  }

  @Test
  void aNewListGetsAnIdSlugifiedFromItsName() {
    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findByUniqueId("simis-news-and-updates")).thenReturn(null);

      assertEquals("simis-news-and-updates",
          GenerateMailingListUniqueIdCommand.generateUniqueId(null, list(null, "SimIS News & Updates")));
    }
  }

  @Test
  void renamingAnExistingListKeepsItsOriginalId() {
    MailingList existing = list("newsletter", "Newsletter");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      assertEquals("newsletter",
          GenerateMailingListUniqueIdCommand.generateUniqueId(existing, list(null, "Company Announcements")));

      // Not even looked up: an existing id is returned as-is, never re-derived and never
      // re-numbered against what else exists today
      repository.verifyNoInteractions();
    }
  }

  @Test
  void anExistingListWithNoIdYetGetsOneGenerated() {
    // Only reachable before UPGRADE_20260831.1800's backfill has run; unique_id is NOT NULL after
    // that. Generating rather than propagating null keeps a save from writing an unusable record.
    MailingList existing = list(null, "Newsletter");

    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findByUniqueId("newsletter")).thenReturn(null);

      assertEquals("newsletter",
          GenerateMailingListUniqueIdCommand.generateUniqueId(existing, list(null, "Newsletter")));
    }
  }

  @Test
  void aNameThatSlugifiesOntoAnExistingIdTakesTheNextFreeSuffix() {
    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findByUniqueId("newsletter")).thenReturn(list("newsletter", "Newsletter"));
      repository.when(() -> MailingListRepository.findByUniqueId("newsletter-2")).thenReturn(list("newsletter-2", "News Letter"));
      repository.when(() -> MailingListRepository.findByUniqueId("newsletter-3")).thenReturn(null);

      assertEquals("newsletter-3",
          GenerateMailingListUniqueIdCommand.generateUniqueId(null, list(null, "NEWSLETTER")));
    }
  }

  @Test
  void aNameWithNoIdSafeCharactersFallsBackRatherThanBecomingAnEmptyId() {
    // "!!!" slugifies to "", which would make every such list collide on the empty string and
    // leave unique_id holding nothing readable
    try (MockedStatic<MailingListRepository> repository = mockStatic(MailingListRepository.class)) {
      repository.when(() -> MailingListRepository.findByUniqueId("list")).thenReturn(null);

      assertEquals("list", GenerateMailingListUniqueIdCommand.generateUniqueId(null, list(null, "!!!")));
    }
  }
}
