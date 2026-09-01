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

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.MakeContentUniqueIdCommand;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;

/**
 * Generates a plain text string - a uniqueId for referencing a mailing list from page configuration
 *
 * @author elizabeth houser
 * @created 8/31/26 9:00 AM
 */
public class GenerateMailingListUniqueIdCommand {

  /**
   * What a name reduces to when it has no id-safe characters at all -- "!!!", or a name written
   * entirely in a non-Latin script. Without this the id would be the empty string, which the
   * collision loop below would then hand '-2', '-3' to, and every such list would be named after
   * nothing.
   */
  static final String FALLBACK_UNIQUE_ID = "list";

  public static String generateUniqueId(MailingList previousRecord, MailingList record) {

    // An existing list's uniqueId must stay stable across a rename -- that is the entire reason
    // this column exists (issue #1724). The emailSubscribe widget's mailingListUniqueId preference
    // is stored in a page's own layout XML at configuration time, so regenerating the id on a name
    // change would break every page pointing at the list, which is the same failure the name-based
    // preference already had. GenerateBlogUniqueIdCommand keeps blog ids stable for the same
    // reason.
    if (previousRecord != null && StringUtils.isNotBlank(previousRecord.getUniqueId())) {
      return previousRecord.getUniqueId();
    }

    // Create a new one
    String value = MakeContentUniqueIdCommand.parseToValidValue(record.getName());
    if (StringUtils.isBlank(value)) {
      value = FALLBACK_UNIQUE_ID;
    }

    // Find the next available unique instance
    int count = 1;
    String uniqueId = value;
    while (MailingListRepository.findByUniqueId(uniqueId) != null) {
      ++count;
      uniqueId = value + "-" + count;
    }
    return uniqueId;
  }

}
