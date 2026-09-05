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
 * One file download, with the date on it. FileItem.downloadCount is a cumulative counter that can
 * only answer "most downloaded ever"; this is what lets the same question be asked over a window.
 *
 * @author SimIS Inc.
 */
public class FileDownload extends Entity {

  private long id = -1;
  private long fileId = -1;
  private long versionId = -1;
  private long downloadBy = -1;
  private Timestamp downloadDate = null;
  private String sessionId = null;

  public FileDownload() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getFileId() {
    return fileId;
  }

  public void setFileId(long fileId) {
    this.fileId = fileId;
  }

  public long getVersionId() {
    return versionId;
  }

  public void setVersionId(long versionId) {
    this.versionId = versionId;
  }

  public long getDownloadBy() {
    return downloadBy;
  }

  public void setDownloadBy(long downloadBy) {
    this.downloadBy = downloadBy;
  }

  public Timestamp getDownloadDate() {
    return downloadDate;
  }

  public void setDownloadDate(Timestamp downloadDate) {
    this.downloadDate = downloadDate;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }
}
