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

import com.simisinc.platform.domain.model.cms.Wiki;
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
 * Description
 *
 * @author matt rajkowski
 * @created 2/9/19 5:29 PM
 */
public class WikiRepository {

  private static Log LOG = LogFactory.getLog(WikiRepository.class);

  private static String TABLE_NAME = "wikis";
  private static String PRIMARY_KEY[] = new String[]{"wiki_id"};

  private static DataResult query(WikiSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("wiki_id = ?", specification.getId(), -1)
          .addIfExists("wiki_unique_id = ?", specification.getUniqueId());
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, WikiRepository::buildRecord);
  }

  public static Wiki findById(long wikiId) {
    if (wikiId == -1) {
      return null;
    }
    return (Wiki) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("wiki_id = ?", wikiId),
        WikiRepository::buildRecord);
  }

  public static Wiki findByUniqueId(String wikiUniqueId) {
    if (StringUtils.isBlank(wikiUniqueId)) {
      return null;
    }
    return (Wiki) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("wiki_unique_id = ?", wikiUniqueId),
        WikiRepository::buildRecord);
  }

  public static List<Wiki> findAll() {
    return findAll(null, null);
  }

  public static List<Wiki> findAll(WikiSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("wiki_id");
    DataResult result = query(specification, constraints);
    return (List<Wiki>) result.getRecords();
  }

  public static Wiki save(Wiki record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Wiki add(Wiki record) {
    SqlUtils insertValues = new SqlUtils()
        .add("wiki_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("enabled", record.getEnabled())
        .add("starting_page", record.getStartingPage());
    // Use a transaction
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // In a transaction (use the existing connection)
        record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
        if (record.getStartingPage() == -1L) {
          // Create the default page
          long wikiPageId = WikiPageRepository.addDefaultPage(connection, record);
          // @todo Create the first revision entry
          // Update the reference
          SqlUtils update = new SqlUtils().add("starting_page", wikiPageId);
          SqlUtils where = new SqlUtils().add("wiki_id = ?", record.getId());
          DB.update(connection, TABLE_NAME, update, where);
        }
        // Finish the transaction
        transaction.commit();
        return record;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("An id was not set!");
    return null;
  }

  public static Wiki update(Wiki record) {
    SqlUtils updateValues = new SqlUtils()
        .add("wiki_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("enabled", record.getEnabled())
        .add("starting_page", record.getStartingPage())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("wiki_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Wiki record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        WikiPageRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("wiki_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static Wiki buildRecord(ResultSet rs) {
    try {
      Wiki record = new Wiki();
      record.setId(rs.getLong("wiki_id"));
      record.setUniqueId(rs.getString("wiki_unique_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setStartingPage(rs.getLong("starting_page"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
