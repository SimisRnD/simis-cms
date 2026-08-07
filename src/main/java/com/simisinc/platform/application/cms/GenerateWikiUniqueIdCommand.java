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

import com.simisinc.platform.domain.model.cms.Wiki;
import com.simisinc.platform.infrastructure.persistence.cms.WikiRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 2/10/19 11:39 AM
 */
public class GenerateWikiUniqueIdCommand {

  public static String generateUniqueId(Wiki previousRecord, Wiki record) {

    // An existing wiki's URL prefix must stay stable across a rename -- a site page built from
    // the "Wiki" web-template has this uniqueId baked into its own layout config (as
    // wikiUniqueId) at creation time, so regenerating it here on a name change orphans the
    // entire public wiki section (every page under it starts rendering wiki-not-setup.jsp even
    // though the content is intact in the database). See GenerateWikiPageUniqueIdCommand for the
    // identical fix already applied to individual wiki pages.
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      return previousRecord.getUniqueId();
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getName());

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (WikiRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
