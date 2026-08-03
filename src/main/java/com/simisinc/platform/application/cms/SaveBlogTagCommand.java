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

    // Transform the fields and store...
    BlogTag tag;
    if (tagBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      tag = BlogTagRepository.findById(tagBean.getId());
      if (tag == null) {
        throw new DataException("The existing record could not be found");
      }
      // Always scope to the tag's own existing blog, never a client-submitted value, so a
      // tampered blogId field can't move a tag into (or borrow the uniqueness scope of)
      // another blog's tag vocabulary
      tagBean.setBlogId(tag.getBlogId());
    } else {
      LOG.debug("Saving a new record... ");
      tag = new BlogTag();
    }

    // A duplicate name is rejected on both create and rename, excluding the tag's own record
    // (unlike SaveTagCommand's #632 item-tag equivalent, this can't rely on a database-level
    // unique index on name alone -- lookup_blog_post_tags is only uniquely indexed on
    // (blog_id, tag_unique_id))
    BlogTag existingWithName = BlogTagRepository.findByNameWithinBlog(tagBean.getName(), tagBean.getBlogId());
    if (existingWithName != null && !existingWithName.getId().equals(tagBean.getId())) {
      throw new DataException("A unique name is required");
    }

    // @note set the uniqueId before setting the name
    tag.setUniqueId(GenerateBlogPostTagUniqueIdCommand.generateUniqueId(tag, tagBean));
    tag.setBlogId(tagBean.getBlogId());
    tag.setName(tagBean.getName());
    tag.setCreatedBy(tagBean.getCreatedBy());
    return BlogTagRepository.save(tag);
  }

}
