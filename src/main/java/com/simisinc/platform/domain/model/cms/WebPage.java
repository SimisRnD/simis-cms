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
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * The information about a webpage, including links, redirects, SEO, and configuration
 *
 * @author matt rajkowski
 * @created 5/4/18 5:38 PM
 */
public class WebPage extends Entity implements Reviewable {

  private long id = -1;
  private String link = null;
  private String redirectUrl = null;
  private String title = null;
  private String keywords = null;
  private String description = null;
  private String imageUrl = null;
  private boolean draft = false;
  // enabled, searchable, and showInSitemap each match their column's own DEFAULT true
  // (NEW_10010__new_cms.sql) -- every save path (WebPageFormWidget.post(), WebPageRepository's
  // insert/update) writes these values explicitly, so the SQL defaults were otherwise dead code.
  // Every page created through the app -- not the install/ seed data, which sets its own values
  // directly in SQL -- got these silently false: enabled is never even exposed as a form field
  // (so no page created through the CMS was ever enabled, which also gates
  // WebPageRepository.search() -- new pages were unfindable there too, not just in the sitemap);
  // searchable and showInSitemap are exposed as form toggles, but a blank/unchecked submission
  // (the default state for a brand-new page's render of the form) explicitly writes false.
  private boolean enabled = true;
  private boolean searchable = true;
  private boolean showInSitemap = true;
  private String sitemapChangeFrequency = null;
  // sitemapPriority is the same bug in a field the 726dfe3d fix above didn't cover (it only
  // audited booleans): the column declares DEFAULT 0.5, but every save path writes this field
  // explicitly, so a brand-new page's first save got the Java default -- 0, not 0.5, the sitemap's
  // documented "unset" priority -- with no admin ever having deliberately chosen it. SitemapServlet
  // treats a stored 0 the same as "unset" for the same reason: fixing the default here only helps
  // pages saved after this fix, not ones that already have 0 stored.
  private BigDecimal sitemapPriority = new BigDecimal("0.5");
  //  private boolean showPageHeader = false;
  //  private boolean showPageFooter = false;
  //  private long popupId = -1;
  //  private String abTestingRedirectLink = null;
  private long createdBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private long modifiedBy = -1;
  // Bulk-actions archive state (issue #427), mirroring CalendarEvent.archived exactly -- no
  // archivedBy companion column, matching calendar_events' own shape.
  private Timestamp archived = null;
  private String roleIdList = null;
  private String pageXml = null;
  private String draftPageXml = null;
  private String template = null;
  private String comments = null;
  private Timestamp publishAt = null;
  private Timestamp expiresAt = null;
  // Free-text solution-page tag for business-KPI reporting (issue #570), e.g. "government-solution",
  // "contract-past-performance", "careers" -- mirrors template's simplicity: nullable, not a foreign
  // key to a taxonomy table. See SolutionTypeOptions for the common values offered in the admin UI.
  private String solutionType = null;
  // Governed publish workflow (issue #407): a draft moves draft -> submitted -> (approved+published |
  // rejected), mirroring Content's own fields exactly -- see ContentReviewCommand/Reviewable. Never
  // reachable through WebPageFormWidget's BeanUtils.populate() form save (mass-assignment risk); only
  // WebPageReviewWidget's explicit submit/approve/reject actions may change these.
  private String draftStatus = null;
  private long submittedBy = -1;
  private long approvedBy = -1;
  private String releaseReference = null;
  // issue #497 cheap-tier slice: a real, settable "internal page" flag for the /admin/web-pages
  // "Hide Internal Pages" filter -- role_id_list above looked like it could serve this but turned out
  // to be persisted and never actually consulted anywhere for access control (dead field).
  private boolean internal = false;
  // Documents WHY a redirect exists (e.g. "old marketing URL, kept for inbound links") so "is this
  // 301 still needed or is it clutter?" (issue #497's "Redirect Confusion" section) isn't a guess.
  private String redirectNotes = null;

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

  public Timestamp getArchived() {
    return archived;
  }

  public void setArchived(Timestamp archived) {
    this.archived = archived;
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

  public String getSolutionType() {
    return solutionType;
  }

  public void setSolutionType(String solutionType) {
    this.solutionType = solutionType;
  }

  @Override
  public String getDraftStatus() {
    return draftStatus;
  }

  @Override
  public void setDraftStatus(String draftStatus) {
    this.draftStatus = draftStatus;
  }

  @Override
  public long getSubmittedBy() {
    return submittedBy;
  }

  @Override
  public void setSubmittedBy(long submittedBy) {
    this.submittedBy = submittedBy;
  }

  @Override
  public long getApprovedBy() {
    return approvedBy;
  }

  @Override
  public void setApprovedBy(long approvedBy) {
    this.approvedBy = approvedBy;
  }

  @Override
  public String getReleaseReference() {
    return releaseReference;
  }

  @Override
  public void setReleaseReference(String releaseReference) {
    this.releaseReference = releaseReference;
  }

  @Override
  public boolean hasDraftContent() {
    return StringUtils.isNotBlank(draftPageXml);
  }

  public boolean isInternal() {
    return internal;
  }

  public boolean getInternal() {
    return internal;
  }

  public void setInternal(boolean internal) {
    this.internal = internal;
  }

  public String getRedirectNotes() {
    return redirectNotes;
  }

  public void setRedirectNotes(String redirectNotes) {
    this.redirectNotes = redirectNotes;
  }

  /**
   * True when this page has a publishAt date set in the future (matches the "not yet live"
   * semantics used by WebPageRepository.countScheduledNotYetLive())
   */
  public boolean isScheduled() {
    return publishAt != null && publishAt.getTime() > System.currentTimeMillis();
  }

  /**
   * True when this page has an expiresAt date set within the next 30 days (matches
   * WebPageRepository.countExpiringSoon()'s window)
   */
  public boolean isExpiringSoon() {
    if (expiresAt == null) {
      return false;
    }
    long now = System.currentTimeMillis();
    long expiresAtMillis = expiresAt.getTime();
    return expiresAtMillis > now && expiresAtMillis <= now + (30L * 24 * 60 * 60 * 1000);
  }
}
