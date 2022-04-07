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

package com.simisinc.platform.infrastructure.persistence.datasets;

import com.simisinc.platform.application.admin.DatasetColumnJSONCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.domain.model.datasets.ScheduleType;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/24/18 7:40 PM
 */
public class DatasetRepository {

  private static Log LOG = LogFactory.getLog(DatasetRepository.class);

  private static String TABLE_NAME = "datasets";
  private static String PRIMARY_KEY[] = new String[]{"dataset_id"};

  public static Dataset findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Dataset) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("dataset_id = ?", id),
        DatasetRepository::buildRecord);
  }

  public static Dataset findByName(String name) {
    if (StringUtils.isBlank(name)) {
      return null;
    }
    return (Dataset) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("LOWER(name) = ?", name.toLowerCase().trim()),
        DatasetRepository::buildRecord);
  }

  public static List<Dataset> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("name"),
        DatasetRepository::buildRecord);
    return (List<Dataset>) result.getRecords();
  }

  public static Dataset save(Dataset record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Dataset add(Dataset record) {
    SqlUtils insertValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("file_length", record.getFileLength())
        .add("row_count", record.getRowCount())
        .add("column_count", record.getColumnCount(), -1)
        .add("file_type", record.getFileType())
        .add("collection_unique_id", record.getCollectionUniqueId())
        .add("category_id", record.getCategoryId())
        .add("source_info", record.getSourceInfo())
        .add("source_url", record.getSourceUrl());
    insertValues.add(new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Dataset update(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("collection_unique_id", StringUtils.trimToNull(record.getCollectionUniqueId()))
        .add("category_id", record.getCategoryId())
        .add("processed", record.getProcessed())
        .add("processed_ms", record.getTotalProcessTime())

        .add("file_type", record.getFileType())
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("file_length", record.getFileLength())
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))

        .add("row_count", record.getRowCount())
        .add("rows_processed", record.getRowsProcessed())
        .add("modified_by", record.getModifiedBy())
        .add("source_info", record.getSourceInfo())
        .add("source_url", record.getSourceUrl())
        .add("column_count", record.getColumnCount(), -1)
        .add("records_path", record.getRecordsPath())
        .add("last_download", record.getLastDownload())
        .add("schedule_type", record.getScheduleType(), ScheduleType.UNDEFINED)
        .add("scheduled_date", record.getScheduled());
    updateValues.add(new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Dataset record) {
    return (DB.deleteFrom(TABLE_NAME, new SqlUtils().add("dataset_id = ?", record.getId())) > 0);
  }

  private static Dataset buildRecord(ResultSet rs) {
    try {
      Dataset record = new Dataset();
      record.setId(rs.getLong("dataset_id"));
      record.setName(rs.getString("name"));
      record.setFilename(rs.getString("filename"));
      record.setFileServerPath(rs.getString("path"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setFileLength(rs.getLong("file_length"));
      record.setRowCount(rs.getInt("row_count"));
      record.setColumnCount(DB.getInt(rs, "column_count", 0));
      record.setFileType(rs.getString("file_type"));
      record.setCollectionUniqueId(rs.getString("collection_unique_id"));
      record.setCategoryId(rs.getLong("category_id"));
      record.setTotalProcessTime(rs.getLong("processed_ms"));
      record.setRowsProcessed(rs.getInt("rows_processed"));
      record.setSourceInfo(rs.getString("source_info"));
      record.setSourceUrl(rs.getString("source_url"));
      record.setRecordsPath(rs.getString("records_path"));
      record.setScheduleType(DB.getInt(rs, "schedule_type", ScheduleType.UNDEFINED));
      record.setScheduled(rs.getTimestamp("scheduled_date"));
      record.setLastDownload(rs.getTimestamp("last_download"));
      DatasetColumnJSONCommand.populateFromColumnConfig(record, rs.getString("column_config"));

      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
