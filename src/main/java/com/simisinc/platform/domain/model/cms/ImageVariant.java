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
 * A derived, resized rendition of an {@link Image} (issue #411) -- e.g. a thumbnail generated
 * from the original upload. One row per (imageId, variantType).
 *
 * @author SimIS Inc.
 */
public class ImageVariant extends Entity {

  private Long id = -1L;
  private long imageId = -1;
  private String variantType = null;
  private String fileServerPath = null;
  private long fileLength = -1;
  private String fileType = null;
  private int width = -1;
  private int height = -1;
  private Timestamp created = null;
  private Timestamp modified = null;

  public ImageVariant() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getImageId() {
    return imageId;
  }

  public void setImageId(long imageId) {
    this.imageId = imageId;
  }

  public String getVariantType() {
    return variantType;
  }

  public void setVariantType(String variantType) {
    this.variantType = variantType;
  }

  public String getFileServerPath() {
    return fileServerPath;
  }

  public void setFileServerPath(String fileServerPath) {
    this.fileServerPath = fileServerPath;
  }

  public long getFileLength() {
    return fileLength;
  }

  public void setFileLength(long fileLength) {
    this.fileLength = fileLength;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
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
}
