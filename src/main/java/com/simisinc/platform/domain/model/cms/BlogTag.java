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

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.Entity;

/**
 * A tag in a blog's own tag vocabulary (issue #633). Scoped by blogId, matching
 * lookup_blog_post_tags' own UNIQUE INDEX on (blog_id, tag_unique_id) -- unlike the Items
 * {@code Tag} model (issue #632), which is scoped by collectionId, this is a separate blog-scoped
 * concept and is not shared with it.
 *
 * @author SimIS
 * @created 8/2/2026
 */
public class BlogTag extends Entity {

  private Long id = -1L;

  private long blogId = -1L;
  private String uniqueId = null;
  private String name = null;
  private long createdBy = -1;
  private Timestamp created = null;

  public BlogTag() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getBlogId() {
    return blogId;
  }

  public void setBlogId(long blogId) {
    this.blogId = blogId;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }
}
