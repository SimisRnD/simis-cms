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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.BlogPost;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.BlogPostRepository;
import lombok.NoArgsConstructor;

/**
 * Event details for when a blog post is published
 *
 * @author matt rajkowski
 * @created 4/9/21 4:33 PM
 */
@NoArgsConstructor
public class BlogPostPublishedEvent extends Event {

  public static final String ID = "blog-post-published";

  private long blogPostId = -1L;

  public BlogPostPublishedEvent(long blogPostId) {
    this.blogPostId = blogPostId;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  @Override
  public long getOccurred() {
    if (getBlogPost() != null && getBlogPost().getStartDate() != null) {
      return getBlogPost().getStartDate().getTime();
    }
    return super.getOccurred();
  }

  public long getBlogPostId() {
    return blogPostId;
  }

  public void setBlogPostId(long blogPostId) {
    this.blogPostId = blogPostId;
  }

  public User getUser() {
    return UserRepository.findByUserId(getBlogPost().getModifiedBy());
  }

  public BlogPost getBlogPost() {
    return BlogPostRepository.findById(blogPostId);
  }

}
