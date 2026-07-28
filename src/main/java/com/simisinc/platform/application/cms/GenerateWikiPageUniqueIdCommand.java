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

import com.simisinc.platform.domain.model.cms.WikiPage;
import com.simisinc.platform.infrastructure.persistence.cms.WikiPageRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 2/10/19 12:01 PM
 */
public class GenerateWikiPageUniqueIdCommand {

  public static String generateUniqueId(WikiPage previousRecord, WikiPage record) {

    // An existing page's URL must stay stable across edits -- including a title change -- so a
    // rename can never silently break inbound [[WikiLinks]] or external links to the page. This
    // previously regenerated the slug from the new title whenever the title changed, which is
    // exactly the case a rename hits.
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      return previousRecord.getUniqueId();
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getTitle());

    // Find the next available unique instance (within the wiki)
    int count = 1;
    String uniqueId = value;
    while (WikiPageRepository.findByUniqueId(record.getWikiId(), uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
