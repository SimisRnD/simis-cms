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

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * The information about a webpage, including links, redirects, SEO, and configuration
 *
 * @author matt rajkowski
 * @created 5/4/18 5:38 PM
 */
public class WebPage extends Entity {

  private long id = -1;
  private String link = null;
  private String redirectUrl = null;
  private String title = null;
  private String keywords = null;
  private String description = null;
  private String imageUrl = null;
  private boolean draft = false;
  private boolean enabled = false;
  private boolean searchable = false;
  private boolean showInSitemap = false;
  private String sitemapChangeFrequency = null;
  private BigDecimal sitemapPriority = new BigDecimal(0);
  //  private boolean showPageHeader = false;
  //  private boolean showPageFooter = false;
  //  private long popupId = -1;
  //  private String abTestingRedirectLink = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private long modifiedBy = -1;
  private String roleIdList = null;
  private String pageXml = null;
  private String draftPageXml = null;
  private String template = null;
  private String comments = null;

  public WebPage() {
  }

  public WebPage(String link, String pageXml) {
    this.link = link;
    this.pageXml = pageXml;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  public String getRedirectUrl() {
    return redirectUrl;
  }

  public void setRedirectUrl(String redirectUrl) {
    this.redirectUrl = redirectUrl;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getKeywords() {
    return keywords;
  }

  public void setKeywords(String keywords) {
    this.keywords = keywords;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getImageUrl() {
    return imageUrl;
  }

  public void setImageUrl(String imageUrl) {
    this.imageUrl = imageUrl;
  }

  public boolean getDraft() {
    return draft;
  }

  public void setDraft(boolean draft) {
    this.draft = draft;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isSearchable() {
    return searchable;
  }

  public boolean getSearchable() {
    return searchable;
  }

  public void setSearchable(boolean searchable) {
    this.searchable = searchable;
  }

  public boolean isShowInSitemap() {
    return showInSitemap;
  }

  public boolean getShowInSitemap() {
    return showInSitemap;
  }

  public void setShowInSitemap(boolean showInSitemap) {
    this.showInSitemap = showInSitemap;
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

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public String getRoleIdList() {
    return roleIdList;
  }

  public void setRoleIdList(String roleIdList) {
    this.roleIdList = roleIdList;
  }

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public String getPageXml() {
    return pageXml;
  }

  public void setPageXml(String pageXml) {
    this.pageXml = pageXml;
  }

  public String getDraftPageXml() {
    return draftPageXml;
  }

  public void setDraftPageXml(String draftPageXml) {
    this.draftPageXml = draftPageXml;
  }

  public String getComments() {
    return comments;
  }

  public void setComments(String comments) {
    this.comments = comments;
  }

  public BigDecimal getSitemapPriority() {
    return sitemapPriority;
  }

  public void setSitemapPriority(BigDecimal sitemapPriority) {
    this.sitemapPriority = sitemapPriority;
  }

  public String getSitemapChangeFrequency() {
    return sitemapChangeFrequency;
  }

  public void setSitemapChangeFrequency(String sitemapChangeFrequency) {
    this.sitemapChangeFrequency = sitemapChangeFrequency;
  }
}
