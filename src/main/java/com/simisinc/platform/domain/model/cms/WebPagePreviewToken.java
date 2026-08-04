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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A time-limited bearer token that lets a visitor holding the link view a web page's current
 * draft content at its real URL, before the draft is reviewed or published (issue #419)
 *
 * @author matt rajkowski
 * @created 8/4/26 9:00 AM
 */
public class WebPagePreviewToken extends Entity {

  private Long id = -1L;

  private long webPageId = -1L;
  // The exact request path the token was minted for (#419 review finding): a WebPage row can back
  // many distinct URLs for a wildcard/dynamic page (link ending in "/*", e.g. "/news/*"), so scoping
  // a token by webPageId alone would let it validate -- and disclose the shared draft -- against
  // every other URL matching that same template, not just the one page the link-holder was shown.
  private String pagePath = null;
  private String token = null;
  private Timestamp expiresAt = null;
  private long createdBy = -1L;
  private Timestamp created = null;

  public WebPagePreviewToken() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getWebPageId() {
    return webPageId;
  }

  public void setWebPageId(long webPageId) {
    this.webPageId = webPageId;
  }

  public String getPagePath() {
    return pagePath;
  }

  public void setPagePath(String pagePath) {
    this.pagePath = pagePath;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Timestamp getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Timestamp expiresAt) {
    this.expiresAt = expiresAt;
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
