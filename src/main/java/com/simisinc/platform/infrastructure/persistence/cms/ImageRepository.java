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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.domain.model.cms.Image;
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
 * @created 5/3/18 3:30 PM
 */
public class ImageRepository {

  private static Log LOG = LogFactory.getLog(ImageRepository.class);

  private static String TABLE_NAME = "images";
  private static String PRIMARY_KEY[] = new String[]{"image_id"};

  private static DataResult query(ImageSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("image_id = ?", specification.getId(), -1)
          .addIfExists("created_by = ?", specification.getCreatedBy(), -1);
      if (specification.getFilename() != null) {
        where.add("LOWER(filename) = ?", specification.getFilename().toLowerCase());
      }
      if (specification.getFileType() != null) {
        where.add("LOWER(file_type) = ?", specification.getFileType().toLowerCase());
      }
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, ImageRepository::buildRecord);
  }

  public static Image findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Image) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("image_id = ?", id),
        ImageRepository::buildRecord);
  }

  public static List<Image> findAll() {
    return findAll(null, null);
  }

  public static List<Image> findAll(ImageSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("created DESC");
    DataResult result = query(specification, constraints);
    return (List<Image>) result.getRecords();
  }

  public static Image save(Image record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static Image add(Image record) {
    SqlUtils insertValues = new SqlUtils()
        .add("filename", StringUtils.trimToNull(record.getFilename()))
        .add("path", StringUtils.trimToNull(record.getFileServerPath()))
        .add("created_by", record.getCreatedBy())
        .add("file_length", record.getFileLength())
        .add("file_type", record.getFileType())
        .add("width", record.getWidth())
        .add("height", record.getHeight());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static Image update(Image record) {
    SqlUtils updateValues = new SqlUtils()
        .add("processed", record.getProcessed());
    SqlUtils where = new SqlUtils()
        .add("image_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static void remove(Image record) {
    DB.deleteFrom(TABLE_NAME, new SqlUtils().add("image_id = ?", record.getId()));
  }

  private static Image buildRecord(ResultSet rs) {
    try {
      Image record = new Image();
      record.setId(rs.getLong("image_id"));
      record.setFilename(rs.getString("filename"));
      record.setFileServerPath(rs.getString("path"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setProcessed(rs.getTimestamp("processed"));
      record.setFileLength(rs.getLong("file_length"));
      record.setFileType(rs.getString("file_type"));
      record.setWidth(rs.getInt("width"));
      record.setHeight(rs.getInt("height"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
