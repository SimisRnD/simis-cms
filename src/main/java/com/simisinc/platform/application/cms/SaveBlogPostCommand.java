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
import com.simisinc.platform.domain.events.cms.BlogPostPublishedEvent;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.cms.GenerateBlogPostUniqueIdCommand.generateUniqueId;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/7/18 3:36 PM
 */
public class SaveBlogPostCommand {

  public static final String allowedChars = "abcdefghijklmnopqrstuvwyxz";
  private static Log LOG = LogFactory.getLog(SaveBlogPostCommand.class);

  public static BlogPost saveBlogPost(BlogPost blogPostBean) throws DataException {

    // Required dependencies
    if (blogPostBean.getCreatedBy() == -1) {
      throw new DataException("The user saving this blog post was not set");
    }
    if (blogPostBean.getBlogId() == -1) {
      throw new DataException("A blog must be set");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(blogPostBean.getTitle())) {
      errorMessages.append("A title is required");
    }
    if (StringUtils.isBlank(blogPostBean.getBody())) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("A body is required");
    }
    if (blogPostBean.getStartDate() != null && blogPostBean.getEndDate() != null && blogPostBean.getEndDate().before(blogPostBean.getStartDate())) {
      if (errorMessages.length() > 0) {
        errorMessages.append("; ");
      }
      errorMessages.append("The end date needs to come after the start date");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Clean the content
    String cleanedContent = HtmlCommand.cleanContent(blogPostBean.getBody());

    // Transform the fields and store...
    BlogPost blogPost;
    if (blogPostBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      blogPost = BlogPostRepository.findById(blogPostBean.getId());
      if (blogPost == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      blogPost = new BlogPost();
    }

    // Check for events
    boolean justPublished = false;
    if (blogPost.getId() > -1) {
      // If it's existing, and just now being published (from draft)
      if (blogPost.getPublished() == null && blogPostBean.getPublished() != null) {
        justPublished = true;
      }
    } else {
      // If it's new and being published (not draft)
      if (blogPostBean.getPublished() != null) {
        justPublished = true;
      }
    }

    // @note set the uniqueId before setting the name
    blogPost.setUniqueId(generateUniqueId(blogPost, blogPostBean));
    blogPost.setBlogId(blogPostBean.getBlogId());
    blogPost.setTitle(blogPostBean.getTitle());
    blogPost.setBody(cleanedContent);
    blogPost.setSummary(blogPostBean.getSummary());
    blogPost.setKeywords(blogPostBean.getKeywords());
    blogPost.setImageUrl(blogPostBean.getImageUrl());
    blogPost.setCreatedBy(blogPostBean.getCreatedBy());
    blogPost.setModifiedBy(blogPostBean.getModifiedBy());
    blogPost.setPublished(blogPostBean.getPublished());
    blogPost.setStartDate(blogPostBean.getStartDate());
    blogPost.setEndDate(blogPostBean.getEndDate());
    if (blogPost.getStartDate() == null && blogPost.getPublished() != null) {
      blogPost.setStartDate(blogPost.getPublished());
    }
    BlogPost result = BlogPostRepository.save(blogPost);
    if (result != null) {
      // Trigger events
      if (justPublished) {
        WorkflowManager.triggerWorkflowForEvent(new BlogPostPublishedEvent(result.getId()));
      }
    }
    return result;
  }
}
