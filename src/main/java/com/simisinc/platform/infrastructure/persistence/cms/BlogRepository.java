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

import com.simisinc.platform.domain.model.cms.Blog;
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
 * @created 8/7/18 8:58 AM
 */
public class BlogRepository {

  private static Log LOG = LogFactory.getLog(BlogRepository.class);

  private static String TABLE_NAME = "blogs";
  private static String PRIMARY_KEY[] = new String[]{"blog_id"};

  private static DataResult query(BlogSpecification specification, DataConstraints constraints) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("blog_id = ?", specification.getId(), -1)
          .addIfExists("blog_unique_id = ?", specification.getUniqueId());
    }
    return DB.selectAllFrom(TABLE_NAME, where, constraints, BlogRepository::buildRecord);
  }

  public static Blog findById(long blogId) {
    if (blogId == -1) {
      return null;
    }
    return (Blog) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("blog_id = ?", blogId),
        BlogRepository::buildRecord);
  }

  public static Blog findByUniqueId(String blogUniqueId) {
    if (StringUtils.isBlank(blogUniqueId)) {
      return null;
    }
    return (Blog) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("blog_unique_id = ?", blogUniqueId),
        BlogRepository::buildRecord);
  }

  public static List<Blog> findAll() {
    return findAll(null, null);
  }

  public static List<Blog> findAll(BlogSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("blog_id");
    DataResult result = query(specification, constraints);
    return (List<Blog>) result.getRecords();
  }

  public static Blog save(Blog record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static Blog add(Blog record) {
    SqlUtils insertValues = new SqlUtils()
        .add("blog_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("created_by", record.getCreatedBy())
        .add("modified_by", record.getModifiedBy())
        .add("enabled", record.getEnabled());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static Blog update(Blog record) {
    SqlUtils updateValues = new SqlUtils()
        .add("blog_unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("description", StringUtils.trimToNull(record.getDescription()))
        .add("enabled", record.getEnabled())
        .add("modified_by", record.getModifiedBy())
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("blog_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(Blog record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        BlogPostRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("blog_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static Blog buildRecord(ResultSet rs) {
    try {
      Blog record = new Blog();
      record.setId(rs.getLong("blog_id"));
      record.setUniqueId(rs.getString("blog_unique_id"));
      record.setName(rs.getString("name"));
      record.setDescription(rs.getString("description"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setModified(rs.getTimestamp("modified"));
      record.setEnabled(rs.getBoolean("enabled"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
