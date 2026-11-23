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

package com.simisinc.platform.infrastructure.persistence.cms;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.FormUpload;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists files visitors attach to forms (issue #2130). See the form_uploads table.
 *
 * @author SimIS Inc.
 */
public class FormUploadRepository {

  private static Log LOG = LogFactory.getLog(FormUploadRepository.class);

  private static final String TABLE_NAME = "form_uploads";
  private static final String[] PRIMARY_KEY = new String[] { "form_upload_id" };

  public static FormUpload findById(long id) {
    if (id <= 0) {
      return null;
    }
    return (FormUpload) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("form_upload_id = ?", id),
        FormUploadRepository::buildRecord);
  }

  public static FormUpload add(FormUpload record) {
    SqlUtils insertValues = new SqlUtils()
        .add("form_unique_id", record.getFormUniqueId())
        .add("field_name", record.getFieldName())
        .add("original_filename", StringUtils.left(StringUtils.trimToNull(record.getOriginalFilename()), 255))
        .add("file_path", record.getFilePath())
        .add("content_type", record.getContentType())
        .add("file_length", record.getFileLength())
        .addIfExists("width", record.getWidth(), -1)
        .addIfExists("height", record.getHeight(), -1)
        .add("thumbnail_path", StringUtils.trimToNull(record.getThumbnailPath()))
        .add("thumbnail_content_type", StringUtils.trimToNull(record.getThumbnailContentType()))
        .add("scan_status", record.getScanStatus())
        .addIfExists("scan_checked", record.getScanChecked());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("A form upload record was not saved");
      return null;
    }
    return record;
  }

  /** Points the uploads at the submission they were attached to, once it has been saved */
  public static boolean linkToFormData(List<FormUpload> uploads, long formDataId) {
    boolean linked = true;
    for (FormUpload upload : uploads) {
      SqlUtils updateValues = new SqlUtils().add("form_data_id", formDataId);
      SqlUtils where = new SqlUtils().add("form_upload_id = ?", upload.getId());
      if (DB.update(TABLE_NAME, updateValues, where)) {
        upload.setFormDataId(formDataId);
      } else {
        LOG.error("A form upload could not be linked to its submission: " + upload.getId());
        linked = false;
      }
    }
    return linked;
  }

  public static boolean remove(FormUpload record) {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("form_upload_id = ?", record.getId())) > 0;
  }

  private static FormUpload buildRecord(ResultSet rs) {
    try {
      FormUpload record = new FormUpload();
      record.setId(rs.getLong("form_upload_id"));
      record.setFormUniqueId(rs.getString("form_unique_id"));
      record.setFormDataId(DB.getLong(rs, "form_data_id", -1));
      record.setFieldName(rs.getString("field_name"));
      record.setOriginalFilename(rs.getString("original_filename"));
      record.setFilePath(rs.getString("file_path"));
      record.setContentType(rs.getString("content_type"));
      record.setFileLength(rs.getLong("file_length"));
      record.setWidth(DB.getInt(rs, "width", -1));
      record.setHeight(DB.getInt(rs, "height", -1));
      record.setThumbnailPath(rs.getString("thumbnail_path"));
      record.setThumbnailContentType(rs.getString("thumbnail_content_type"));
      record.setScanStatus(rs.getString("scan_status"));
      record.setScanChecked(rs.getTimestamp("scan_checked"));
      record.setCreated(rs.getTimestamp("created"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
