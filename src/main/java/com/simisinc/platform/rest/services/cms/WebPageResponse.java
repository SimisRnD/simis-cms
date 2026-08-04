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

import com.simisinc.platform.domain.model.cms.WebPage;

/**
 * Public metadata for a web page (issue #412) -- not the rendered widget content, which would
 * require the same section/column/widget role-gating {@code PageServlet} applies and is out of
 * scope for this read endpoint.
 *
 * @author SimIS Inc.
 */
public class WebPageResponse {

  private String link;
  private String title;
  private String description;
  private String keywords;
  private String imageUrl;
  private String redirectUrl;
  private Timestamp publishAt;
  private Timestamp expiresAt;
  private Timestamp modified;

  public WebPageResponse(WebPage webPage) {
    link = webPage.getLink();
    title = webPage.getTitle();
    description = webPage.getDescription();
    keywords = webPage.getKeywords();
    imageUrl = webPage.getImageUrl();
    redirectUrl = webPage.getRedirectUrl();
    publishAt = webPage.getPublishAt();
    expiresAt = webPage.getExpiresAt();
    modified = webPage.getModified();
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
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

  public String getRedirectUrl() {
    return redirectUrl;
  }

  public void setRedirectUrl(String redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  public Timestamp getPublishAt() {
    return publishAt;
  }

  public void setPublishAt(Timestamp publishAt) {
    this.publishAt = publishAt;
  }

  public Timestamp getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Timestamp expiresAt) {
    this.expiresAt = expiresAt;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
