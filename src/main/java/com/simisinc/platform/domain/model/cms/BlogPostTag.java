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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

/**
 * The tag a blog post carries (issue #633), backed by the blog_post_tags join table.
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogPostTag extends Entity {

  private Long id = -1L;

  private long postId = -1L;
  private long tagId = -1L;

  public BlogPostTag() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getPostId() {
    return postId;
  }

  public void setPostId(long postId) {
    this.postId = postId;
  }

  public long getTagId() {
    return tagId;
  }

  public void setTagId(long tagId) {
    this.tagId = tagId;
  }
}
