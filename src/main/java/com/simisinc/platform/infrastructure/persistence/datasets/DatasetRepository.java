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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.CustomFieldListJSONCommand;
import com.simisinc.platform.application.datasets.DatasetColumnJSONCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;

/**
 * Persists and retrieves dataset objects
 *
 * @author matt rajkowski
 * @created 4/24/18 7:40 PM
 */
public class DatasetRepository {

  private static Log LOG = LogFactory.getLog(DatasetRepository.class);

  private static String TABLE_NAME = "datasets";
  private static String[] PRIMARY_KEY = new String[] { "dataset_id" };

  private static final int STATUS_READY = 0;
  private static final int STATUS_RUNNING = 1;
  private static final int STATUS_FAILED = 2;

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

  public static List<Dataset> findAllScheduledForDownload() {
    SqlUtils where = new SqlUtils();
    where.add("schedule_enabled = ?", true)
        .add("schedule_frequency IS NOT NULL")
        .add("source_url IS NOT NULL")
        .add("CURRENT_TIMESTAMP > last_download + schedule_frequency")
        .add("queue_date IS NULL OR CURRENT_TIMESTAMP > queue_date + queue_interval")
        .add("queue_status = " + STATUS_READY);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        where,
        new DataConstraints().setDefaultColumnToSortBy("queue_status"),
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
        .add("source_url", record.getSourceUrl())
        .add("source_info", record.getSourceInfo())
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("file_hash", record.getFileHash())
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("last_download", record.getLastDownload())
        .add("records_path", record.getRecordsPath())
        .add("paging_url_path", record.getPagingUrlPath())
        .add("column_count", record.getColumnCount(), -1)
        .add("row_count", record.getRowCount())
        .add("collection_unique_id", record.getCollectionUniqueId())
        .add("category_id", record.getCategoryId())
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    insertValues.add(
        new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
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
        .add("source_url", record.getSourceUrl())
        .add("source_info", record.getSourceInfo())
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("file_hash", record.getFileHash())
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("last_download", record.getLastDownload())
        .add("records_path", record.getRecordsPath())
        .add("paging_url_path", record.getPagingUrlPath())
        .add("column_count", record.getColumnCount(), -1)
        .add("row_count", record.getRowCount())
        .add("modified_by", record.getModifiedBy());
    updateValues.add(
        new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static Dataset updateDetails(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("name", record.getName())
        .add("source_info", record.getSourceInfo())
        .add("modified_by", record.getModifiedBy());
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateDetails failed!");
    return null;
  }

  public static Dataset updateConfiguration(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("records_path", record.getRecordsPath())
        .add("paging_url_path", record.getPagingUrlPath())
        .add("column_count", record.getColumnCount(), -1)
        .add("row_count", record.getRowCount());
    updateValues.add(
        new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateConfiguration failed!");
    return null;
  }

  public static Dataset updateMapping(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("collection_unique_id", StringUtils.trimToNull(record.getCollectionUniqueId()))
        .add("category_id", record.getCategoryId())
        .add("unique_column_name", record.getUniqueColumnName());
    updateValues.add(
        new SqlValue("column_config", SqlValue.JSONB_TYPE, DatasetColumnJSONCommand.createColumnJSONString(record)));
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateMapping failed!");
    return null;
  }

  public static Dataset updateScheduleAndSyncDetails(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("schedule_enabled", record.getScheduleEnabled())
        .add(new SqlValue("schedule_frequency", SqlValue.INTERVAL_TYPE, record.getScheduleFrequency()))
        .add("sync_enabled", record.getSyncEnabled())
        .add("sync_merge_type", record.getSyncMergeType());
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateScheduleAndSyncDetails failed!");
    return null;
  }

  public static Dataset updateCustomFields(Dataset record) {
    SqlUtils updateValues = new SqlUtils()
        .add("modified", new Timestamp(System.currentTimeMillis()));
    if (record.getCustomFieldList() != null && !record.getCustomFieldList().isEmpty()) {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE,
          CustomFieldListJSONCommand.createJSONString(record.getCustomFieldList())));
    } else {
      updateValues.add(new SqlValue("field_values", SqlValue.JSONB_TYPE, null));
    }
    SqlUtils where = new SqlUtils().add("collection_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateCustomFields failed!");
    return null;
  }

  public static Dataset updateCollectionUniqueId(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("collection_unique_id", StringUtils.trimToNull(record.getCollectionUniqueId()))
        .add("category_id", record.getCategoryId());
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateCollectionUniqueId failed!");
    return null;
  }

  public static Dataset updateRowsProcessed(Dataset record) {
    // Update
    SqlUtils updateValues = new SqlUtils()
        .add("rows_processed", record.getRowsProcessed());
    // Where
    SqlUtils where = new SqlUtils().add("dataset_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("updateRowsProcessed failed!");
    return null;
  }

  /*
   * Attempts to lock a record for processing.
   * Status 0 = available, 1 = locked, 2 = red flag
   */
  public static boolean markAsQueuedIfAllowed(Dataset record) {
    SqlUtils updateValues = new SqlUtils()
        .add("queue_status", STATUS_RUNNING)
        .add("queue_date = CURRENT_TIMESTAMP")
        .add("queue_attempts = queue_attempts + 1")
        .add("schedule_last_run = CURRENT_TIMESTAMP");
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId())
        .add("queue_status = ?", STATUS_READY);
    return DB.update(TABLE_NAME, updateValues, where);
  }

  public static boolean markAsUnqueued(Dataset record) {
    SqlUtils updateValues = new SqlUtils()
        .add("queue_status", STATUS_READY)
        .add("queue_date = NULL")
        .add("queue_message = NULL")
        .add("queue_attempts = 0")
        .add(new SqlValue("queue_interval", SqlValue.INTERVAL_TYPE, "PT5M"));
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    return DB.update(TABLE_NAME, updateValues, where);
  }

  public static boolean markToRetryDownload(Dataset record, String reason) {
    // Determine when to retry or finally fail
    int queueStatus = STATUS_READY;
    String retryInterval = "PT5M";
    if (record.getQueueAttempts() < 5) {
      retryInterval = "PT5M";
    } else if (record.getQueueAttempts() < 10) {
      retryInterval = "PT30M";
    } else if (record.getQueueAttempts() < 20) {
      retryInterval = "PT6H";
    } else if (record.getQueueAttempts() < 30) {
      retryInterval = "P1D";
    } else {
      queueStatus = STATUS_FAILED;
    }
    SqlUtils updateValues = new SqlUtils()
        .add("queue_status", queueStatus)
        .add("queue_message", reason)
        .add(new SqlValue("queue_interval", SqlValue.INTERVAL_TYPE, retryInterval));
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    return DB.update(TABLE_NAME, updateValues, where);
  }

  public static boolean markLastDownload(Dataset record) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("last_download", timestamp);
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setLastDownload(timestamp);
    }
    return updated;
  }

  public static boolean markScheduleLastRun(Dataset record, int status, String message) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("schedule_last_run", timestamp);
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setScheduleLastRun(timestamp);
    }
    return updated;
  }

  public static boolean markAsProcessStarted(Dataset record) {
    SqlUtils updateValues = new SqlUtils()
        .add("process_status", STATUS_RUNNING)
        .add("process_message", (String) null);
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setProcessStatus(STATUS_RUNNING);
      record.setProcessMessage(null);
    }
    return updated;
  }

  public static boolean markAsProcessFinished(Dataset record, String message) {
    Timestamp timestamp = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("process_status = ?", STATUS_READY)
        .add("processed", timestamp)
        .add("process_message", message)
        .add("processed_ms", record.getTotalProcessTime());
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setProcessStatus(STATUS_READY);
      record.setProcessMessage(message);
    }
    return updated;
  }

  public static boolean resetSyncTimestamp(Dataset record, Timestamp timestamp) {
    SqlUtils updateValues = new SqlUtils()
        .add("sync_status", STATUS_RUNNING)
        .add("sync_date", timestamp)
        .add("sync_message", (String) null);
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setSyncStatus(STATUS_RUNNING);
      record.setSyncDate(timestamp);
      record.setSyncMessage(null);
    }
    return updated;
  }

  public static boolean saveSyncResult(Dataset record, String message) {
    SqlUtils updateValues = new SqlUtils()
        .add("sync_status", STATUS_READY)
        .add("sync_message", message);
    SqlUtils where = new SqlUtils()
        .add("dataset_id = ?", record.getId());
    boolean updated = DB.update(TABLE_NAME, updateValues, where);
    if (updated) {
      // Update the record for additional workflows
      record.setSyncStatus(STATUS_READY);
      record.setSyncMessage(message);
    }
    return updated;
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
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setTotalProcessTime(rs.getLong("processed_ms"));
      record.setFileLength(rs.getLong("file_length"));
      record.setRowCount(rs.getInt("row_count"));
      record.setColumnCount(DB.getInt(rs, "column_count", 0));
      record.setFileType(rs.getString("file_type"));
      record.setCollectionUniqueId(rs.getString("collection_unique_id"));
      record.setRowsProcessed(rs.getInt("rows_processed"));
      record.setSourceInfo(rs.getString("source_info"));
      record.setSourceUrl(rs.getString("source_url"));
      DatasetColumnJSONCommand.populateFromColumnConfig(record, rs.getString("column_config"));
      record.setCategoryId(rs.getLong("category_id"));
      record.setRecordsPath(rs.getString("records_path"));
      record.setScheduledDate(rs.getTimestamp("scheduled_date"));
      record.setLastDownload(rs.getTimestamp("last_download"));
      record.setProcessStatus(DB.getInt(rs, "process_status", 0));
      record.setProcessMessage(rs.getString("process_message"));
      record.setScheduleEnabled(rs.getBoolean("schedule_enabled"));
      record.setScheduleFrequency(DB.getPeriod(rs, "schedule_frequency"));
      record.setScheduleLastRun(rs.getTimestamp("schedule_last_run"));
      record.setSyncEnabled(rs.getBoolean("sync_enabled"));
      record.setSyncDate(rs.getTimestamp("sync_date"));
      record.setSyncStatus(DB.getInt(rs, "sync_status", 0));
      record.setSyncMessage(rs.getString("sync_message"));
      record.setSyncMergeType(rs.getString("sync_merge_type"));
      record.setUniqueColumnName(rs.getString("unique_column_name"));
      record.setCustomFieldList(CustomFieldListJSONCommand.populateFromJSONString(rs.getString("field_values")));
      record.setQueueStatus(DB.getInt(rs, "queue_status", 0));
      record.setQueueDate(rs.getTimestamp("queue_date"));
      record.setQueueAttempts(DB.getInt(rs, "queue_attempts", 0));
      record.setPagingUrlPath(rs.getString("paging_url_path"));
      record.setRecordCount(DB.getInt(rs, "record_count", 0));
      record.setSyncRecordCount(DB.getInt(rs, "sync_record_count", 0));
      record.setSyncAddCount(DB.getInt(rs, "sync_add_count", 0));
      record.setSyncUpdateCount(DB.getInt(rs, "sync_update_count", 0));
      record.setSyncDeleteCount(DB.getInt(rs, "sync_delete_count", 0));
      record.setFileHash(rs.getString("file_hash"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
