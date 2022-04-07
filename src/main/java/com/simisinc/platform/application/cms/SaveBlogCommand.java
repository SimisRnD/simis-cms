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
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateBlogUniqueIdCommand.generateUniqueId;


/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 11:16 AM
 */
public class SaveBlogCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveBlogCommand.class);

  public static Blog saveBlog(Blog blogBean) throws DataException {

    // Required dependencies
    if (blogBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this item was not set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(blogBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Blog blog;
    if (blogBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      blog = BlogRepository.findById(blogBean.getId());
      if (blog == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      blog = new Blog();
    }
    // @note set the uniqueId before setting the name
    blog.setUniqueId(generateUniqueId(blog, blogBean));
    blog.setName(blogBean.getName());
    blog.setDescription(blogBean.getDescription());
    blog.setCreatedBy(blogBean.getCreatedBy());
    blog.setModifiedBy(blogBean.getModifiedBy());
    blog.setEnabled(blogBean.getEnabled());
    return BlogRepository.save(blog);
  }
}
