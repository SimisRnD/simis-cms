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
 * A snapshot of a web page's page_xml taken at the moment it was replaced by a publish (#405) --
 * the outgoing content, not the incoming one, so a prior published state can be viewed or restored.
 *
 * @author SimIS Inc.
 * @created 8/2/2026
 */
public class WebPageVersion extends Entity {

  private long id = -1;
  private long webPageId = -1;
  private String pageXml = null;
  private long publishedBy = -1;
  private Timestamp publishedAt = null;
  private String label = null;

  public WebPageVersion() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getWebPageId() {
    return webPageId;
  }

  public void setWebPageId(long webPageId) {
    this.webPageId = webPageId;
  }

  public String getPageXml() {
    return pageXml;
  }

  public void setPageXml(String pageXml) {
    this.pageXml = pageXml;
  }

  public long getPublishedBy() {
    return publishedBy;
  }

  public void setPublishedBy(long publishedBy) {
    this.publishedBy = publishedBy;
  }

  public Timestamp getPublishedAt() {
    return publishedAt;
  }

  public void setPublishedAt(Timestamp publishedAt) {
    this.publishedAt = publishedAt;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }
}
