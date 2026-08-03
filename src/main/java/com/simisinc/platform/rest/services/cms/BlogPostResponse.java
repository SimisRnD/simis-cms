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

package com.simisinc.platform.rest.services.cms;

import java.sql.Timestamp;

import com.simisinc.platform.domain.model.cms.BlogPost;

/**
 * Public fields for a published blog post (issue #412).
 *
 * @author SimIS Inc.
 */
public class BlogPostResponse {

  private String uniqueId;
  private String title;
  private String body;
  private String summary;
  private String keywords;
  private String imageUrl;
  private String videoUrl;
  private Timestamp published;
  private Timestamp startDate;
  private Timestamp endDate;
  private Timestamp modified;

  public BlogPostResponse(BlogPost blogPost) {
    uniqueId = blogPost.getUniqueId();
    title = blogPost.getTitle();
    body = blogPost.getBody();
    summary = blogPost.getSummary();
    keywords = blogPost.getKeywords();
    imageUrl = blogPost.getImageUrl();
    videoUrl = blogPost.getVideoUrl();
    published = blogPost.getPublished();
    startDate = blogPost.getStartDate();
    endDate = blogPost.getEndDate();
    modified = blogPost.getModified();
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public String getVideoUrl() {
    return videoUrl;
  }

  public void setVideoUrl(String videoUrl) {
    this.videoUrl = videoUrl;
  }

  public Timestamp getPublished() {
    return published;
  }

  public void setPublished(Timestamp published) {
    this.published = published;
  }

  public Timestamp getStartDate() {
    return startDate;
  }

  public void setStartDate(Timestamp startDate) {
    this.startDate = startDate;
  }

  public Timestamp getEndDate() {
    return endDate;
  }

  public void setEndDate(Timestamp endDate) {
    this.endDate = endDate;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
