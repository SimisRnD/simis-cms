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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.cms.BlogTag;
import com.simisinc.platform.infrastructure.persistence.cms.BlogTagRepository;

/**
 * Validates and saves a blog tag object (issue #633)
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class SaveBlogTagCommand {

  private static Log LOG = LogFactory.getLog(SaveBlogTagCommand.class);

  public static BlogTag saveTag(BlogTag tagBean) throws DataException {

    // Validate the required fields
    if (tagBean.getBlogId() == -1) {
      throw new DataException("A parent blog is required");
    }
    if (StringUtils.isBlank(tagBean.getName())) {
      throw new DataException("A name is required, please check the fields and try again");
    }
    if (tagBean.getCreatedBy() == -1) {
      throw new DataException("The user creating this tag was not set");
    }

    if (tagBean.getId() == -1
        && BlogTagRepository.findByNameWithinBlog(tagBean.getName(), tagBean.getBlogId()) != null) {
      throw new DataException("A unique name is required");
    }

    // Transform the fields and store...
    BlogTag tag;
    if (tagBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      tag = BlogTagRepository.findById(tagBean.getId());
      if (tag == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      tag = new BlogTag();
    }
    // @note set the uniqueId before setting the name
    tag.setUniqueId(GenerateBlogPostTagUniqueIdCommand.generateUniqueId(tag, tagBean));
    tag.setBlogId(tagBean.getBlogId());
    tag.setName(tagBean.getName());
    tag.setCreatedBy(tagBean.getCreatedBy());
    return BlogTagRepository.save(tag);
  }

}
