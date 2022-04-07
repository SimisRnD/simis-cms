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

package com.simisinc.platform.domain.model.datasets;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

/**
 * Information, file meta-data, and dataset to item mappings for datasets
 *
 * @author matt rajkowski
 * @created 4/24/18 7:39 PM
 */
public class Dataset extends Entity {

  private String collectionUniqueId = null;
  private long categoryId = -1;
  private Long id = -1L;
  private String name = null;
  private String filename = null;
  private String fileServerPath = null;
  private long fileLength = -1;
  private int rowCount = -1;
  private int columnCount = -1;
  private String[] columnNames = null;
  private String[] fieldTitles = null;
  private String[] fieldMappings = null;
  private String[] fieldOptions = null;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private int rowsProcessed = 0;
  private Timestamp processed = null;
  private long totalProcessTime = 0;
  private String fileType = null;
  private String sourceInfo = null;
  private String sourceUrl = null;
  private String recordsPath = null;
  private int scheduleType = ScheduleType.UNDEFINED;
  private Timestamp scheduled = null;
  private Timestamp lastDownload = null;

  // not saved
  private boolean skipDuplicateNames = false;

  public Dataset() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getFilename() {
    return filename;
  }

  public void setFilename(String filename) {
    this.filename = filename;
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

  public int getRowCount() {
    return rowCount;
  }

  public void setRowCount(int rowCount) {
    this.rowCount = rowCount;
  }

  public int getColumnCount() {
    return columnCount;
  }

  public void setColumnCount(int columnCount) {
    this.columnCount = columnCount;
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

  public int getRowsProcessed() {
    return rowsProcessed;
  }

  public void setRowsProcessed(int rowsProcessed) {
    this.rowsProcessed = rowsProcessed;
  }

  public Timestamp getProcessed() {
    return processed;
  }

  public void setProcessed(Timestamp processed) {
    this.processed = processed;
  }

  public long getTotalProcessTime() {
    return totalProcessTime;
  }

  public void setTotalProcessTime(long totalProcessTime) {
    this.totalProcessTime = totalProcessTime;
  }

  public String getFileType() {
    return fileType;
  }

  public void setFileType(String fileType) {
    this.fileType = fileType;
  }

  public String[] getColumnNames() {
    return columnNames;
  }

  public void setColumnNames(String[] columnNames) {
    this.columnNames = columnNames;
  }

  public List<String> getColumnNamesList() {
    if (columnNames == null) {
      return null;
    }
    return Arrays.asList(columnNames);
  }

  public String[] getFieldTitles() {
    return fieldTitles;
  }

  public void setFieldTitles(String[] fieldTitles) {
    this.fieldTitles = fieldTitles;
  }

  public List<String> getFieldTitlesList() {
    if (fieldTitles == null) {
      return null;
    }
    return Arrays.asList(fieldTitles);
  }

  public String[] getFieldMappings() {
    return fieldMappings;
  }

  public void setFieldMappings(String[] fieldMappings) {
    this.fieldMappings = fieldMappings;
  }

  public List<String> getFieldMappingsList() {
    if (fieldMappings == null) {
      return null;
    }
    return Arrays.asList(fieldMappings);
  }

  public String[] getFieldOptions() {
    return fieldOptions;
  }

  public void setFieldOptions(String[] fieldOptions) {
    this.fieldOptions = fieldOptions;
  }

  public List<String> getFieldOptionsList() {
    if (fieldOptions == null) {
      return null;
    }
    return Arrays.asList(fieldOptions);
  }

  public String getCollectionUniqueId() {
    return collectionUniqueId;
  }

  public void setCollectionUniqueId(String collectionUniqueId) {
    this.collectionUniqueId = collectionUniqueId;
  }

  public long getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(long categoryId) {
    this.categoryId = categoryId;
  }

  public String getSourceInfo() {
    return sourceInfo;
  }

  public void setSourceInfo(String sourceInfo) {
    this.sourceInfo = sourceInfo;
  }

  public String getSourceUrl() {
    return sourceUrl;
  }

  public void setSourceUrl(String sourceUrl) {
    this.sourceUrl = sourceUrl;
  }

  public String getRecordsPath() {
    return recordsPath;
  }

  public void setRecordsPath(String recordsPath) {
    this.recordsPath = recordsPath;
  }

  public int getScheduleType() {
    return scheduleType;
  }

  public void setScheduleType(int scheduleType) {
    this.scheduleType = scheduleType;
  }

  public Timestamp getScheduled() {
    return scheduled;
  }

  public void setScheduled(Timestamp scheduled) {
    this.scheduled = scheduled;
  }

  public Timestamp getLastDownload() {
    return lastDownload;
  }

  public void setLastDownload(Timestamp lastDownload) {
    this.lastDownload = lastDownload;
  }

  public boolean isSkipDuplicateNames() {
    return skipDuplicateNames;
  }

  public void setSkipDuplicateNames(boolean skipDuplicateNames) {
    this.skipDuplicateNames = skipDuplicateNames;
  }
}
