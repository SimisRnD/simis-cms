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

    // Use an existing uniqueId
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      // See if the name changed
      if (previousRecord.getTitle().equals(record.getTitle())) {
        return previousRecord.getUniqueId();
      }
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getTitle());

    // See if it's really the same
    if (previousRecord != null && value.equals(previousRecord.getTitle())) {
      return previousRecord.getUniqueId();
    }

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
