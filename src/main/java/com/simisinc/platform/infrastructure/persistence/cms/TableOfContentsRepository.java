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

import com.simisinc.platform.application.cms.TableOfContentsJSONCommand;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves table of contents objects
 *
 * @author matt rajkowski
 * @created 12/7/18 4:42 PM
 */
public class TableOfContentsRepository {

  private static Log LOG = LogFactory.getLog(TableOfContentsRepository.class);

  private static String TABLE_NAME = "table_of_contents";
  private static String[] PRIMARY_KEY = new String[]{"toc_id"};

  private static DataResult query(TableOfContentsSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("toc_id = ?", specification.getId(), -1)
          .addIfExists("toc_unique_id = ?", specification.getTocUniqueId());
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, TableOfContentsRepository::buildRecord);
  }

  public static TableOfContents findByUniqueId(String tocUniqueId) {
    if (StringUtils.isBlank(tocUniqueId)) {
      return null;
    }
    return (TableOfContents) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("toc_unique_id = ?", tocUniqueId),
        TableOfContentsRepository::buildRecord);
  }

  public static List<TableOfContents> findAll(TableOfContentsSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("toc_unique_id");
    DataResult result = query(specification, constraints);
    return (List<TableOfContents>) result.getRecords();
  }

  public static TableOfContents save(TableOfContents record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static TableOfContents add(TableOfContents record) {
    SqlUtils insertValues = new SqlUtils()
        .add("toc_unique_id", StringUtils.trimToNull(record.getTocUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    insertValues.add(new SqlValue("entries", SqlValue.JSONB_TYPE, TableOfContentsJSONCommand.createJSONString(record)));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static TableOfContents update(TableOfContents record) {
    SqlUtils updateValues = new SqlUtils()
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    updateValues.add(new SqlValue("entries", SqlValue.JSONB_TYPE, TableOfContentsJSONCommand.createJSONString(record)));
    SqlUtils where = new SqlUtils()
        .add("toc_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      // Expire the cache
      CacheManager.invalidateKey(CacheManager.TABLE_OF_CONTENTS_UNIQUE_ID_CACHE, record.getTocUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  private static TableOfContents buildRecord(ResultSet rs) {
    try {
      TableOfContents record = new TableOfContents();
      record.setId(rs.getLong("toc_id"));
      record.setTocUniqueId(rs.getString("toc_unique_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      TableOfContentsJSONCommand.populateFromJSONString(record, rs.getString("entries"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
