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

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;

/**
 * Generates a plain text string - a uniqueId for URLs and referencing
 *
 * @author matt rajkowski
 * @created 8/7/18 11:11 AM
 */
public class GenerateBlogUniqueIdCommand {

  public static String generateUniqueId(Blog previousRecord, Blog record) {

    // An existing blog's URL prefix must stay stable across a rename -- a site page built from
    // the "Blog" web-template has this uniqueId baked into its own layout config (as
    // blogUniqueId) at creation time, so regenerating it here on a name change orphans the
    // entire public blog section. See GenerateWikiUniqueIdCommand for the identical fix already
    // applied to wiki containers.
    if (previousRecord != null && previousRecord.getUniqueId() != null) {
      return previousRecord.getUniqueId();
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getName());

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (BlogRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
