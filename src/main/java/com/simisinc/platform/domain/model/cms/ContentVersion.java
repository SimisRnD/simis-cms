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
 * A snapshot of a content block's live content taken at the moment it was replaced by a publish
 * (#406) -- the outgoing content, not the incoming one, so a prior published state can be viewed,
 * diffed, or restored. Always plain rendered HTML, regardless of whether the outgoing content was
 * stored as legacy HTML or Quill Delta JSON at the time (see ContentRepository#publish).
 *
 * @author elizabeth houser
 */
public class ContentVersion extends Entity {

  private long id = -1;
  private long contentId = -1;
  private String content = null;
  private long approvedBy = -1;
  private Timestamp publishedAt = null;
  private String releaseReference = null;

  public ContentVersion() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getContentId() {
    return contentId;
  }

  public void setContentId(long contentId) {
    this.contentId = contentId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public long getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(long approvedBy) {
    this.approvedBy = approvedBy;
  }

  public Timestamp getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Timestamp publishedAt) {
    this.publishedAt = publishedAt;
  }

  public String getReleaseReference() {
    return releaseReference;
  }

  public void setReleaseReference(String releaseReference) {
    this.releaseReference = releaseReference;
  }
}
