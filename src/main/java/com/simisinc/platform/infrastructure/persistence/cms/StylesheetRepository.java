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

import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves style sheet objects
 *
 * @author matt rajkowski
 * @created 1/25/21 10:05 PM
 */
public class StylesheetRepository {

  private static Log LOG = LogFactory.getLog(StylesheetRepository.class);

  private static String TABLE_NAME = "stylesheets";
  private static String PRIMARY_KEY[] = new String[]{"stylesheet_id"};

  public static Stylesheet findById(long id) {
    if (id == -1) {
      return null;
    }
    return (Stylesheet) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("stylesheet_id = ?", id),
        StylesheetRepository::buildRecord);
  }

  public static Stylesheet findByWebPageId(long webPageId) {
    return (Stylesheet) DB.selectRecordFrom(
        TABLE_NAME,
        (webPageId > 0 ?
            new SqlUtils().add("web_page_id = ?", webPageId) :
            new SqlUtils().add("web_page_id IS NULL")),
        StylesheetRepository::buildRecord);
  }

  public static List<Stylesheet> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("stylesheet_id"),
        StylesheetRepository::buildRecord);
    if (result.hasRecords()) {
      return (List<Stylesheet>) result.getRecords();
    }
    return null;
  }

  public static Stylesheet save(Stylesheet record) {
    if (record.getId() > -1) {
      if (StringUtils.isBlank(record.getCss())) {
        if (remove(record)) {
          return record;
        }
      }
      return update(record);
    }
    return add(record);
  }

  private static Stylesheet add(Stylesheet record) {
    Timestamp modified = new Timestamp(System.currentTimeMillis());
    SqlUtils insertValues = new SqlUtils()
        .add("web_page_id", record.getWebPageId(), -1)
        .add("css", StringUtils.trimToNull(record.getCss()))
        .add("modified", modified);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    record.setModified(modified);
    return record;
  }

  private static Stylesheet update(Stylesheet record) {
    Timestamp modified = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("css", StringUtils.trimToNull(record.getCss()))
        .add("modified", modified);
    SqlUtils where = new SqlUtils()
        .add("stylesheet_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      CacheManager.invalidateKey(CacheManager.STYLESHEET_WEB_PAGE_ID_CACHE, record.getWebPageId());
      record.setModified(modified);
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Stylesheet record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
//        ItemCategoryRepository.removeAll(connection, record);
//        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
//        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("stylesheet_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        CacheManager.invalidateKey(CacheManager.STYLESHEET_WEB_PAGE_ID_CACHE, record.getWebPageId());
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static Stylesheet buildRecord(ResultSet rs) {
    try {
      Stylesheet record = new Stylesheet();
      record.setId(rs.getLong("stylesheet_id"));
      record.setWebPageId(DB.getLong(rs, "web_page_id", -1));
      record.setCss(rs.getString("css"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
