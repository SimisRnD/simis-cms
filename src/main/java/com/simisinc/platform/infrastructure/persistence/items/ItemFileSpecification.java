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

package com.simisinc.platform.infrastructure.persistence.items;

import com.simisinc.platform.domain.model.Entity;
import com.simisinc.platform.presentation.controller.DataConstants;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/19/2021 1:00 PM
 */
public class ItemFileSpecification extends Entity {

  private Long id = -1L;
  private long itemId = -1L;
  private long folderId = -1L;
  private long subFolderId = -1L;
  private String filename = null;
  private String barcode = null;
  private long createdBy = -1;
  private String fileType = null;
  private Long forUserId = -1L;
  private String matchesName = null;
  private String searchName = null;
  private String searchContent = null;
  private int withinLastDays = -1;
  private int inASubFolder = DataConstants.UNDEFINED;

  public ItemFileSpecification() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getItemId() {
    return itemId;
  }

  public void setItemId(long itemId) {
    this.itemId = itemId;
  }

  public long getFolderId() {
    return folderId;
  }

  public void setFolderId(long folderId) {
    this.folderId = folderId;
  }

  public long getSubFolderId() {
    return subFolderId;
  }

  public void setSubFolderId(long subFolderId) {
    this.subFolderId = subFolderId;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
  }

  public String getBarcode() {
    return barcode;
  }

  public void setBarcode(String barcode) {
    this.barcode = barcode;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public Long getForUserId() {
    return forUserId;
  }

  public void setForUserId(Long forUserId) {
    this.forUserId = forUserId;
  }

  public String getMatchesName() {
    return matchesName;
  }

  public void setMatchesName(String matchesName) {
    this.matchesName = matchesName;
  }

  public String getSearchName() {
    return searchName;
  }

  public void setSearchName(String searchName) {
    this.searchName = searchName;
  }

  public String getSearchContent() {
    return searchContent;
  }

  public void setSearchContent(String searchContent) {
    this.searchContent = searchContent;
  }

  public int getWithinLastDays() {
    return withinLastDays;
  }

  public void setWithinLastDays(int withinLastDays) {
    this.withinLastDays = withinLastDays;
  }

  public int getInASubFolder() {
    return inASubFolder;
  }

  public void setInASubFolder(int inASubFolder) {
    this.inASubFolder = inASubFolder;
  }

  public void setInASubFolder(boolean inASubFolder) {
    this.inASubFolder = (inASubFolder ? DataConstants.TRUE : DataConstants.FALSE);
  }
}
