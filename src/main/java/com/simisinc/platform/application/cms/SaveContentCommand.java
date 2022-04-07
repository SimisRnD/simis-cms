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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/26/18 11:12 AM
 */
public class SaveContentCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz-1234567890";
  private static Log LOG = LogFactory.getLog(SaveContentCommand.class);

  public static Content saveSafeContent(String contentUniqueId, String contentHtml, long userId, boolean publish) throws DataException {

    if (contentHtml == null) {
      throw new DataException("Content is required");
    }

    // Clean the content
    String cleanedContent = HtmlCommand.cleanContent(contentHtml);

    // Save the content
    Content content = ContentRepository.findByUniqueId(contentUniqueId);
    if (content == null) {
      LOG.debug("Saving new content record...");
      content = new Content();
      content.setUniqueId(contentUniqueId);
    }
    // Determine if the content is immediately published
    if (publish) {
      // Publish it
      content.setContent(cleanedContent);
      content.setDraftContent(null);
    } else {
      // Save as draft
      content.setDraftContent(cleanedContent);
    }
    content.setCreatedBy(userId);
    content.setModifiedBy(userId);
    return ContentRepository.save(content);

  }

}
