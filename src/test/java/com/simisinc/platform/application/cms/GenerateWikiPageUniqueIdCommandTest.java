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

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.WikiPage;

/**
 * Covers {@link GenerateWikiPageUniqueIdCommand#generateUniqueId}.
 *
 * <p>
 * An existing page's URL must never change on a rename -- otherwise every inbound
 * {@code [[WikiLink]]} and external link to it silently breaks. The previous implementation
 * regenerated the slug from the new title whenever the title changed, which is exactly the case a
 * rename hits; it only kept the old slug when the title was byte-for-byte unchanged.
 * </p>
 *
 * @author SimIS
 * @created 7/28/2026
 */
class GenerateWikiPageUniqueIdCommandTest {

  @Test
  void anExistingPageKeepsItsUniqueIdWhenTheTitleIsUnchanged() {
    WikiPage previousRecord = existingPage("setup-guide", "Setup Guide");
    WikiPage edited = new WikiPage();
    edited.setTitle("Setup Guide");

    String uniqueId = GenerateWikiPageUniqueIdCommand.generateUniqueId(previousRecord, edited);

    assertEquals("setup-guide", uniqueId);
  }

  @Test
  void anExistingPageKeepsItsUniqueIdWhenTheTitleIsRenamed() {
    // This is the bug: renaming the title used to regenerate the slug from the new title,
    // silently changing the page's URL and breaking every link that pointed at the old one.
    WikiPage previousRecord = existingPage("setup-guide", "Setup Guide");
    WikiPage renamed = new WikiPage();
    renamed.setTitle("Complete Setup Guide for New Users");

    String uniqueId = GenerateWikiPageUniqueIdCommand.generateUniqueId(previousRecord, renamed);

    assertEquals("setup-guide", uniqueId, "an existing page's URL must not change when only its title changes");
  }

  @Test
  void anExistingPageKeepsItsUniqueIdEvenWhenRenamedToLookLikeADifferentSlug() {
    // Same guarantee, phrased against the slugified form rather than a human title, since the old
    // bug's second (dead) branch specifically compared a slug value against a title.
    WikiPage previousRecord = existingPage("original-name", "Original Name");
    WikiPage renamed = new WikiPage();
    renamed.setTitle("original-name"); // happens to already look like a slug

    String uniqueId = GenerateWikiPageUniqueIdCommand.generateUniqueId(previousRecord, renamed);

    assertEquals("original-name", uniqueId);
  }

  private static WikiPage existingPage(String uniqueId, String title) {
    WikiPage page = new WikiPage();
    page.setUniqueId(uniqueId);
    page.setTitle(title);
    return page;
  }
}
