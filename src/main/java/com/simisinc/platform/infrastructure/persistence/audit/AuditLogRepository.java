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

package com.simisinc.platform.infrastructure.persistence.audit;

import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persists and retrieves security audit records. Records are append-only (insert, never update) so the
 * trail cannot be silently rewritten; there is no foreign key on actor_user_id so a record survives the
 * deletion of the user it references.
 *
 * @author SimIS Inc.
 */
public class AuditLogRepository {

  private static Log LOG = LogFactory.getLog(AuditLogRepository.class);

  private static String TABLE_NAME = "audit_log";
  private static String[] PRIMARY_KEY = new String[]{"audit_id"};

  public static AuditLog save(AuditLog record) {
    return add(record);
  }

  private static AuditLog add(AuditLog record) {
    SqlUtils insertValues = new SqlUtils()
        .add("occurred", record.getOccurred())
        .add("event_category", record.getEventCategory(), 50)
        .add("event_type", record.getEventType(), 100)
        .add("outcome", record.getOutcome(), 20)
        .add("actor_user_id", record.getActorUserId(), -1)
        .add("actor_username", record.getActorUsername(), 255)
        .add("source_ip", record.getSourceIp(), 200)
        .add("target_type", record.getTargetType(), 50)
        .add("target_id", record.getTargetId(), 255)
        .add("target_label", record.getTargetLabel(), 255)
        .add("details", record.getDetails())
        .add("session_id", record.getSessionId(), 255)
        .add("schema_version", record.getSchemaVersion());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static List<AuditLog> findAll(DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("audit_id desc");
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, new SqlUtils(), new SqlUtils(), new SqlUtils(), constraints, AuditLogRepository::buildRecord);
    return (List<AuditLog>) result.getRecords();
  }

  private static SqlUtils createWhereStatement(AuditLogSpecification specification) {
    SqlUtils where = null;
    if (specification != null) {
      where = new SqlUtils()
          .addIfExists("event_category = ?", specification.getEventCategory())
          .addIfExists("event_type = ?", specification.getEventType())
          .addIfExists("outcome = ?", specification.getOutcome())
          .addIfExists("actor_user_id = ?", specification.getActorUserId(), -1)
          .addIfExists("LOWER(actor_username) LIKE ?", specification.getActorUsername() != null
              ? "%" + specification.getActorUsername().toLowerCase() + "%" : null)
          .addIfExists("source_ip = ?", specification.getSourceIp())
          .addIfExists("occurred >= ?", specification.getOccurredAfter())
          .addIfExists("occurred < ?", specification.getOccurredBefore());
    }
    return where;
  }

  public static List<AuditLog> findAll(AuditLogSpecification specification, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("audit_id desc");
    SqlUtils where = createWhereStatement(specification);
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, constraints, AuditLogRepository::buildRecord);
    return (List<AuditLog>) result.getRecords();
  }

  private static AuditLog buildRecord(ResultSet rs) {
    try {
      AuditLog record = new AuditLog();
      record.setId(rs.getLong("audit_id"));
      record.setOccurred(rs.getTimestamp("occurred"));
      record.setEventCategory(rs.getString("event_category"));
      record.setEventType(rs.getString("event_type"));
      record.setOutcome(rs.getString("outcome"));
      long actorUserId = rs.getLong("actor_user_id");
      record.setActorUserId(rs.wasNull() ? -1L : actorUserId);
      record.setActorUsername(rs.getString("actor_username"));
      record.setSourceIp(rs.getString("source_ip"));
      record.setTargetType(rs.getString("target_type"));
      record.setTargetId(rs.getString("target_id"));
      record.setTargetLabel(rs.getString("target_label"));
      record.setDetails(rs.getString("details"));
      record.setSessionId(rs.getString("session_id"));
      record.setSchemaVersion(rs.getInt("schema_version"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
