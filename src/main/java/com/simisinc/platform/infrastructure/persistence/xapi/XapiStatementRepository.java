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

package com.simisinc.platform.infrastructure.persistence.xapi;

import com.simisinc.platform.domain.model.xapi.XapiStatement;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves experience api (xAPI) statement objects
 *
 * @author matt rajkowski
 * @created 4/6/2021 8:15 AM
 */
public class XapiStatementRepository {

  private static Log LOG = LogFactory.getLog(XapiStatementRepository.class);

  private static String TABLE_NAME = "xapi_statements";
  private static String PRIMARY_KEY[] = new String[]{"statement_id"};

  private static SqlUtils createWhereStatement(XapiStatementSpecification specification) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("statement_id = ?", specification.getId(), -1)
          .addIfExists("actor_id = ?", specification.getActorId(), -1)
          .addIfExists("verb = ?", specification.getVerb())
          .addIfExists("object = ?", specification.getObject());
    }
    return where;
  }

  private static DataResult query(XapiStatementSpecification specification, DataConstraints constraints) {
    SqlUtils where = createWhereStatement(specification);
    return DB.selectAllFrom(TABLE_NAME, where, constraints, XapiStatementRepository::buildRecord);
  }

  public static XapiStatement findById(long statementId) {
    if (statementId == -1) {
      return null;
    }
    return (XapiStatement) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("statement_id = ?", statementId),
        XapiStatementRepository::buildRecord);
  }

  public static List<XapiStatement> findAll() {
    return findAll(null, null);
  }

  public static List<XapiStatement> findAll(XapiStatementSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("statement_id");
    // occurred_at < CURRENT_TIMESTAMP
    // constraints.setColumnToSortBy("occurred_at", "desc");
    DataResult result = query(specification, constraints);
    return (List<XapiStatement>) result.getRecords();
  }

  public static XapiStatement save(XapiStatement record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  public static XapiStatement add(XapiStatement record) {
    SqlUtils insertValues = new SqlUtils()
        .add("message", StringUtils.trimToNull(record.getMessage()))
        .add("message_snapshot", StringUtils.trimToNull(record.getMessageSnapshot()))
        .addIfExists("actor_id", record.getActorId(), -1)
        .add("verb", StringUtils.trimToNull(record.getVerb()))
        .add("object", StringUtils.trimToNull(record.getObject()))
        .addIfExists("object_id", record.getObjectId(), -1)
        .addIfExists("occurred_at", record.getOccurredAt())
        .addIfExists("authority", StringUtils.trimToNull(record.getAuthority()))
        .addIfExists("user_context", record.getContextUserId(), -1)
        .addIfExists("item_context", record.getContextItemId(), -1)
        .addIfExists("project_context", record.getContextProjectId(), -1)
        .addIfExists("issue_context", record.getContextIssueId(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static XapiStatement update(XapiStatement record) {
    SqlUtils updateValues = new SqlUtils()
        .add("message", StringUtils.trimToNull(record.getMessage()))
        .add("message_snapshot", StringUtils.trimToNull(record.getMessageSnapshot()))
        .add("actor_id", record.getActorId(), -1)
        .add("verb", StringUtils.trimToNull(record.getVerb()))
        .add("object", StringUtils.trimToNull(record.getObject()))
        .add("object_id", record.getObjectId(), -1)
        .add("occurred_at", record.getOccurredAt())
        .add("authority", StringUtils.trimToNull(record.getAuthority()))
        .add("user_context", record.getContextUserId(), -1)
        .add("item_context", record.getContextItemId(), -1)
        .add("project_context", record.getContextProjectId(), -1)
        .add("issue_context", record.getContextIssueId(), -1);
    SqlUtils where = new SqlUtils()
        .add("statement_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
//      CacheManager.invalidateKey(CacheManager.CONTENT_UNIQUE_ID_CACHE, record.getUniqueId());
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(XapiStatement record) {
    try {
      try (Connection connection = DB.getConnection();
           AutoStartTransaction a = new AutoStartTransaction(connection);
           AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("statement_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static XapiStatement buildRecord(ResultSet rs) {
    try {
      XapiStatement record = new XapiStatement();
      record.setId(rs.getLong("statement_id"));
      record.setMessage(rs.getString("message"));
      record.setMessageSnapshot(rs.getString("message_snapshot"));
      record.setActorId(DB.getLong(rs, "actor_id", -1));
      record.setVerb(rs.getString("verb"));
      record.setObject(rs.getString("object"));
      record.setObjectId(DB.getLong(rs, "object_id", -1));
      record.setOccurredAt(rs.getTimestamp("occurred_at"));
      record.setCreated(rs.getTimestamp("created"));
      record.setAuthority(rs.getString("authority"));
      record.setContextUserId(DB.getLong(rs, "user_context", -1));
      record.setContextItemId(DB.getLong(rs, "item_context", -1));
      record.setContextProjectId(DB.getLong(rs, "project_context", -1));
      record.setContextIssueId(DB.getLong(rs, "issue_context", -1));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
