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
 * A website content block
 *
 * @author matt rajkowski
 * @created 4/8/18 4:18 PM
 */
public class Content extends Entity {

  private Long id = -1L;

  private String uniqueId = null;
  private String content = null;
  private String draftContent = null;
  // Format stamp for content/draftContent: 0 = legacy HTML, 2 = visual-editor Quill Delta JSON
  // (DeltaContentCommand.DELTA_FORMAT_VERSION). Draft and published carry their own stamp because a
  // page mid-conversion can have an HTML published version and a Delta draft at the same time.
  private int contentFormat = 0;
  private int draftContentFormat = 0;
  // Governed publish path (P1): a draft moves draft -> submitted -> (approved+published | rejected).
  // The named approver and the release-authority reference are recorded here and, immutably, in the
  // audit trail. Separation of duties (approver != submitter) is enforced in ContentReviewCommand.
  private String draftStatus = null;
  private long submittedBy = -1;
  private long approvedBy = -1;
  private String releaseReference = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  private String highlight = null;

  public Content() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getDraftContent() {
    return draftContent;
  }

  public void setDraftContent(String draftContent) {
    this.draftContent = draftContent;
  }

  public int getContentFormat() {
    return contentFormat;
  }

  public void setContentFormat(int contentFormat) {
    this.contentFormat = contentFormat;
  }

  public int getDraftContentFormat() {
    return draftContentFormat;
  }

  public void setDraftContentFormat(int draftContentFormat) {
    this.draftContentFormat = draftContentFormat;
  }

  public String getDraftStatus() {
    return draftStatus;
  }

  public void setDraftStatus(String draftStatus) {
    this.draftStatus = draftStatus;
  }

  public long getSubmittedBy() {
    return submittedBy;
  }

  public void setSubmittedBy(long submittedBy) {
    this.submittedBy = submittedBy;
  }

  public long getApprovedBy() {
    return approvedBy;
  }

  public void setApprovedBy(long approvedBy) {
    this.approvedBy = approvedBy;
  }

  public String getReleaseReference() {
    return releaseReference;
  }

  public void setReleaseReference(String releaseReference) {
    this.releaseReference = releaseReference;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
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

  public String getHighlight() {
    return highlight;
  }

  public void setHighlight(String highlight) {
    this.highlight = highlight;
  }
}
