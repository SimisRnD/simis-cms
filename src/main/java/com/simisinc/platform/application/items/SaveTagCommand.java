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

package com.simisinc.platform.application.items;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Tag;
import com.simisinc.platform.infrastructure.persistence.items.TagRepository;

/**
 * Validates and saves a tag object (issue #632)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class SaveTagCommand {

  private static Log LOG = LogFactory.getLog(SaveTagCommand.class);

  public static Tag saveTag(Tag tagBean) throws DataException {

    // Validate the required fields
    if (tagBean.getCollectionId() == -1) {
      throw new DataException("A parent collection is required");
    }
    if (StringUtils.isBlank(tagBean.getName())) {
      throw new DataException("A name is required, please check the fields and try again");
    }
    if (tagBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this tag was not set");
    }

    if (tagBean.getId() == -1 && TagRepository.findByNameWithinCollection(tagBean.getName(),
        tagBean.getCollectionId()) != null) {
      throw new DataException("A unique name is required");
    }

    // Transform the fields and store...
    Tag tag;
    if (tagBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      tag = TagRepository.findById(tagBean.getId());
      if (tag == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      tag = new Tag();
    }
    tag.setCollectionId(tagBean.getCollectionId());
    tag.setName(tagBean.getName());
    tag.setCreatedBy(tagBean.getCreatedBy());
    return TagRepository.save(tag);
  }

}
