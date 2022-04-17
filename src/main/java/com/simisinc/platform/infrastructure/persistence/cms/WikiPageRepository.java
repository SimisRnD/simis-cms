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
import com.simisinc.platform.domain.model.cms.WikiPage;
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
 * Persists and retrieves wiki page objects
 *
 * @author matt rajkowski
 * @created 2/10/19 11:18 AM
 */
public class WikiPageRepository {

  private static Log LOG = LogFactory.getLog(WikiPageRepository.class);

  private static String TABLE_NAME = "wiki_pages";
  private static String PRIMARY_KEY[] = new String[]{"wiki_page_id"};

  private static SqlUtils createWhereStatement(WikiPageSpecification specification) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("wiki_page_id = ?", specification.getId(), -1)
          .addIfExists("wiki_id = ?", specification.getWikiId(), -1)
          .addIfExists("page_unique_id = ?", specification.getUniqueId());
      if (specification.getStartingDateRange() != null && specification.getEndingDateRange() != null) {
        where.add("((start_date >= ? AND start_date < ?) OR (end_date >= ? AND end_date < ?))",
            new Timestamp[]{specification.getStartingDateRange(), specification.getEndingDateRange(), specification.getStartingDateRange(), specification.getEndingDateRange()});
      }
    }
    return where;
  }

  private static DataResult query(WikiPageSpecification specification, DataConstraints constraints) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectAllFrom(TABLE_NAME, where, constraints, WikiPageRepository::buildRecord);
  }

  public static WikiPage findByUniqueId(Long wikiId, String pageUniqueId) {
    if (StringUtils.isBlank(pageUniqueId)) {
      return null;
    }
    return (WikiPage) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("wiki_id = ?", wikiId)
            .add("page_unique_id = ?", pageUniqueId.toLowerCase()),
        WikiPageRepository::buildRecord);
  }

  public static WikiPage findById(Long wikiPageId) {
    if (wikiPageId == -1) {
      return null;
    }
    return (WikiPage) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("wiki_page_id = ?", wikiPageId),
        WikiPageRepository::buildRecord);
  }

  public static List<WikiPage> findAll() {
    return findAll(null, null);
  }

  public static List<WikiPage> findAll(WikiPageSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("wiki_page_id");
    DataResult result = query(specification, constraints);
    return (List<WikiPage>) result.getRecords();
  }

  public static long findCount(WikiPageSpecification specification) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectCountFrom(TABLE_NAME, where);
  }

  public static WikiPage save(WikiPage record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static long addDefaultPage(Connection connection, Wiki wiki) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("wiki_id", wiki.getId())
        .add("page_unique_id", "home")
        .add("title", "Home")
        .add("body", "Welcome to the " + wiki.getName() + " wiki!")
        .add("created_by", wiki.getCreatedBy())
        .add("modified_by", wiki.getModifiedBy());
    return DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static WikiPage add(WikiPage record) {
    SqlUtils insertValues = new SqlUtils()
        .add("wiki_id", record.getWikiId())
        .add("page_unique_id", StringUtils.trimToNull(record.getUniqueId()).toLowerCase())
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static WikiPage update(WikiPage record) {
    SqlUtils updateValues = new SqlUtils()
        .add("wiki_id", record.getWikiId())
        .add("page_unique_id", StringUtils.trimToNull(record.getUniqueId()).toLowerCase())
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("body", StringUtils.trimToNull(record.getBody()))
        .add("summary", StringUtils.trimToNull(record.getSummary()))
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("wiki_page_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(WikiPage record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
//        ItemCategoryRepository.removeAll(connection, record);
//        CollectionRepository.updateItemCount(connection, record.getCollectionId(), -1);
//        CategoryRepository.updateItemCount(connection, record.getCategoryId(), -1);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("wiki_page_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  public static void removeAll(Connection connection, Wiki wiki) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("wiki_id = ?", wiki.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  private static WikiPage buildRecord(ResultSet rs) {
    try {
      WikiPage record = new WikiPage();
      record.setId(rs.getLong("wiki_page_id"));
      record.setWikiId(rs.getLong("wiki_id"));
      record.setUniqueId(rs.getString("page_unique_id"));
      record.setTitle(rs.getString("title"));
      record.setBody(rs.getString("body"));
      record.setSummary(rs.getString("summary"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
