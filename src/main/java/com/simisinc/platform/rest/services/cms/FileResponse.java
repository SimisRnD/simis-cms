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

import com.simisinc.platform.domain.model.cms.FileItem;

/**
 * Public fields for a file in a folder (issue #412).
 *
 * @author SimIS Inc.
 */
public class FileResponse {

  private long id;
  private String filename;
  private String title;
  private String summary;
  private String mimeType;
  private long fileLength;
  private int width;
  private int height;
  private Timestamp modified;

  public FileResponse(FileItem fileItem) {
    id = fileItem.getId();
    filename = fileItem.getFilename();
    title = fileItem.getTitle();
    summary = fileItem.getSummary();
    mimeType = fileItem.getMimeType();
    fileLength = fileItem.getFileLength();
    width = fileItem.getWidth();
    height = fileItem.getHeight();
    modified = fileItem.getModified();
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(String mimeType) {
    this.mimeType = mimeType;
  }

  public long getFileLength() {
    return fileLength;
  }

  public void setFileLength(long fileLength) {
    this.fileLength = fileLength;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
