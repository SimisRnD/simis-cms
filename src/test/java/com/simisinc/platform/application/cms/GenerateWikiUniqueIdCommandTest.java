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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;

/**
 * Covers {@link GenerateWikiUniqueIdCommand#generateUniqueId}.
 *
 * <p>
 * An existing wiki's uniqueId must never change on a rename -- a site page built from the "Wiki"
 * web-template has that uniqueId baked into its own layout config (as wikiUniqueId) at creation
 * time, so regenerating it on a name change silently orphans the entire public wiki section
 * (every page under it starts rendering wiki-not-setup.jsp even though the content is intact in
 * the database). The previous implementation only kept the old uniqueId when the name was
 * byte-for-byte unchanged, which is exactly backwards -- a rename is precisely the case that hit
 * the bug. This mirrors the equivalent, already-correct fix in
 * {@link GenerateWikiPageUniqueIdCommandTest}.
 * </p>
 */
class GenerateWikiUniqueIdCommandTest {

  @Test
  void anExistingWikiKeepsItsUniqueIdWhenTheNameIsUnchanged() {
    Wiki previousRecord = existingWiki("docs", "Documentation");
    Wiki edited = new Wiki();
    edited.setName("Documentation");

    String uniqueId = GenerateWikiUniqueIdCommand.generateUniqueId(previousRecord, edited);

    assertEquals("docs", uniqueId);
  }

  @Test
  void anExistingWikiKeepsItsUniqueIdWhenItIsRenamed() {
    // This is the bug: renaming the name used to regenerate the uniqueId from the new name,
    // silently changing the wiki's public URL prefix and orphaning every page under it (a site
    // page built from the "Wiki" web-template has the old uniqueId baked into its layout config).
    Wiki previousRecord = existingWiki("docs", "Documentation");
    Wiki renamed = new Wiki();
    renamed.setName("Product Documentation");

    String uniqueId = GenerateWikiUniqueIdCommand.generateUniqueId(previousRecord, renamed);

    assertEquals("docs", uniqueId, "an existing wiki's URL prefix must not change when only its name changes");
  }

  @Test
  void aGenuinelyNewWikiGetsAFreshlyGeneratedUniqueIdFromItsName() {
    Wiki newRecord = new Wiki();
    newRecord.setName("Sales Docs");

    try (MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class)) {
      wikiRepository.when(() -> WikiRepository.findByUniqueId("sales-docs")).thenReturn(null);

      String uniqueId = GenerateWikiUniqueIdCommand.generateUniqueId(null, newRecord);

      assertEquals("sales-docs", uniqueId);
    }
  }

  @Test
  void aGenuinelyNewWikiDedupesItsUniqueIdAgainstAnExistingCollision() {
    Wiki newRecord = new Wiki();
    newRecord.setName("Sales Docs");

    try (MockedStatic<WikiRepository> wikiRepository = mockStatic(WikiRepository.class)) {
      wikiRepository.when(() -> WikiRepository.findByUniqueId("sales-docs")).thenReturn(existingWiki("sales-docs", "Sales Docs"));
      wikiRepository.when(() -> WikiRepository.findByUniqueId("sales-docs-2")).thenReturn(null);

      String uniqueId = GenerateWikiUniqueIdCommand.generateUniqueId(null, newRecord);

      assertEquals("sales-docs-2", uniqueId);
    }
  }

  private static Wiki existingWiki(String uniqueId, String name) {
    Wiki wiki = new Wiki();
    wiki.setUniqueId(uniqueId);
    wiki.setName(name);
    return wiki;
  }
}
