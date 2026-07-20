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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.audit.AuditLogIntegrityCommand;
import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves security audit records. Records are append-only (insert, never update) so the
 * trail cannot be silently rewritten; there is no foreign key on actor_user_id so a record survives the
 * deletion of the user it references.
 *
 * <p>Each insert extends a tamper-evident SHA-256 hash chain (see {@link AuditLogIntegrityCommand}). To keep
 * the chain linear, appends are serialized with a Postgres transaction-level advisory lock: within one
 * transaction the writer takes the lock, reads the current tail's hash, computes this record's hash, and
 * inserts -- so two concurrent writers cannot both chain off the same tail. The lock is held only for that
 * read-then-insert (sub-millisecond) and is released on commit; audit volume is low enough that the
 * serialization is not a bottleneck.
 *
 * @author SimIS Inc.
 */
public class AuditLogRepository {

  private static Log LOG = LogFactory.getLog(AuditLogRepository.class);

  private static String TABLE_NAME = "audit_log";
  private static String[] PRIMARY_KEY = new String[]{"audit_id"};

  // A fixed key so every audit append contends on the same advisory lock (and nothing else does).
  private static final long AUDIT_CHAIN_LOCK_KEY = 872025601L;
  // Cap the wait for the advisory lock (milliseconds) so a stalled holder cannot block request threads
  // unboundedly. A constant, never user input, so it is safe to inline into the SET statement.
  private static final int LOCK_TIMEOUT_MS = 2000;

  // Column widths, applied before hashing so the value that is hashed is exactly the value that is stored.
  private static final int MAX_EVENT_CATEGORY = 50;
  private static final int MAX_EVENT_TYPE = 100;
  private static final int MAX_OUTCOME = 20;
  private static final int MAX_ACTOR_USERNAME = 255;
  private static final int MAX_SOURCE_IP = 200;
  private static final int MAX_TARGET_TYPE = 50;
  private static final int MAX_TARGET_ID = 255;
  private static final int MAX_TARGET_LABEL = 255;
  private static final int MAX_SESSION_ID = 255;
  private static final int HASH_LENGTH = 64;

  private static final int DEFAULT_RETENTION_DAYS = 2555; // ~7 years
  private static final int MIN_RETENTION_DAYS = 90;       // a floor so retention cannot erase recent evidence
  private static final int MAX_RETENTION_DAYS = 3650;     // ~10 years, to avoid an unbounded interval

  public static AuditLog save(AuditLog record) {
    return add(record);
  }

  /**
   * Appends a record to the tamper-evident chain inside a single serialized transaction. Never throws --
   * on any failure it rolls back and returns null (the caller, SaveAuditEventCommand, still emits the event
   * to the JSON sink, so the event is not lost). Returning null signals only that the database row was not
   * written.
   */
  private static AuditLog add(AuditLog record) {
    Connection connection = null;
    boolean priorAutoCommit = true;
    try {
      normalizeForStorage(record);
      connection = DB.getConnection();
      priorAutoCommit = connection.getAutoCommit();
      connection.setAutoCommit(false);

      // Bound the wait for the advisory lock so a stalled holder cannot pile up request threads
      // indefinitely; a timeout surfaces as SQLException and is handled as a fail-safe rollback below.
      try (Statement timeout = connection.createStatement()) {
        timeout.execute("SET LOCAL lock_timeout = " + LOCK_TIMEOUT_MS);
      }
      // Serialize appenders so concurrent audit writes extend one linear chain rather than forking it.
      try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
        lock.setLong(1, AUDIT_CHAIN_LOCK_KEY);
        lock.execute();
      }

      String previousHash = selectTailRecordHash(connection);
      String recordHash = AuditLogIntegrityCommand.computeRecordHash(record, previousHash);
      record.setPreviousHash(previousHash);
      record.setRecordHash(recordHash);

      long id = DB.insertInto(connection, TABLE_NAME, buildInsertValues(record), PRIMARY_KEY);
      connection.commit();

      record.setId(id);
      if (id == -1) {
        LOG.error("An id was not set!");
        return null;
      }
      return record;
    } catch (Exception e) {
      // Never throw to the caller (auditing must not break the action it observes) -- catch everything,
      // including an unchecked exception from acquiring a connection before the DataSource is ready.
      rollbackQuietly(connection);
      LOG.error("Audit chain append failed: " + e.getMessage(), e);
      return null;
    } finally {
      closeQuietly(connection, priorAutoCommit);
    }
  }

  private static SqlUtils buildInsertValues(AuditLog record) {
    return new SqlUtils()
        .add("occurred", record.getOccurred())
        .add("event_category", record.getEventCategory(), MAX_EVENT_CATEGORY)
        .add("event_type", record.getEventType(), MAX_EVENT_TYPE)
        .add("outcome", record.getOutcome(), MAX_OUTCOME)
        .add("actor_user_id", record.getActorUserId(), -1)
        .add("actor_username", record.getActorUsername(), MAX_ACTOR_USERNAME)
        .add("source_ip", record.getSourceIp(), MAX_SOURCE_IP)
        .add("target_type", record.getTargetType(), MAX_TARGET_TYPE)
        .add("target_id", record.getTargetId(), MAX_TARGET_ID)
        .add("target_label", record.getTargetLabel(), MAX_TARGET_LABEL)
        .add("details", record.getDetails())
        .add("session_id", record.getSessionId(), MAX_SESSION_ID)
        .add("schema_version", record.getSchemaVersion())
        .add("previous_hash", record.getPreviousHash(), HASH_LENGTH)
        .add("record_hash", record.getRecordHash(), HASH_LENGTH);
  }

  /**
   * Truncates the record's fields to their column widths and rounds occurred to millisecond precision (the
   * column is TIMESTAMP(3)). This is done before the hash is computed so that the hash covers exactly the
   * bytes that are stored and read back -- otherwise database truncation or rounding would break the chain.
   */
  private static void normalizeForStorage(AuditLog record) {
    Timestamp occurred = record.getOccurred();
    if (occurred != null) {
      record.setOccurred(Timestamp.from(occurred.toInstant().truncatedTo(ChronoUnit.MILLIS)));
    }
    record.setEventCategory(truncate(record.getEventCategory(), MAX_EVENT_CATEGORY));
    record.setEventType(truncate(record.getEventType(), MAX_EVENT_TYPE));
    record.setOutcome(truncate(record.getOutcome(), MAX_OUTCOME));
    record.setActorUsername(truncate(record.getActorUsername(), MAX_ACTOR_USERNAME));
    record.setSourceIp(truncate(record.getSourceIp(), MAX_SOURCE_IP));
    record.setTargetType(truncate(record.getTargetType(), MAX_TARGET_TYPE));
    record.setTargetId(truncate(record.getTargetId(), MAX_TARGET_ID));
    record.setTargetLabel(truncate(record.getTargetLabel(), MAX_TARGET_LABEL));
    record.setSessionId(truncate(record.getSessionId(), MAX_SESSION_ID));
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    // Do not split a UTF-16 surrogate pair: a lone surrogate is not encodable as UTF-8, so the driver would
    // store a replacement character while the hash was computed over the lone surrogate -- a false mismatch.
    int end = maxLength;
    if (Character.isHighSurrogate(value.charAt(end - 1))) {
      end--;
    }
    return value.substring(0, end);
  }

  /** The record_hash of the newest record, or the genesis hash when the table is empty or unhashed. */
  private static String selectTailRecordHash(Connection connection) throws SQLException {
    try (PreparedStatement pst = connection.prepareStatement(
        "SELECT record_hash FROM " + TABLE_NAME + " ORDER BY audit_id DESC LIMIT 1");
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        String hash = rs.getString(1);
        if (hash != null) {
          return hash;
        }
      }
      return AuditLogIntegrityCommand.GENESIS_HASH;
    }
  }

  private static void rollbackQuietly(Connection connection) {
    if (connection != null) {
      try {
        connection.rollback();
      } catch (SQLException e) {
        LOG.error("Audit append rollback failed: " + e.getMessage());
      }
    }
  }

  private static void closeQuietly(Connection connection, boolean priorAutoCommit) {
    if (connection == null) {
      return;
    }
    try {
      connection.setAutoCommit(priorAutoCommit);
    } catch (SQLException e) {
      LOG.debug("Could not restore autoCommit on the pooled connection");
    }
    try {
      connection.close();
    } catch (SQLException e) {
      LOG.debug("Could not close the pooled connection");
    }
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

  /**
   * Returns up to {@code limit} records with an audit_id greater than {@code afterAuditId}, in ascending
   * order -- keyset pagination for a full chain walk (see AuditLogIntegrityCommand.verify). Pass 0 to start.
   */
  public static List<AuditLog> findChainPage(long afterAuditId, int limit) {
    DataConstraints constraints = new DataConstraints();
    constraints.setPageSize(limit);
    constraints.setUseCount(false);
    constraints.setColumnToSortBy("audit_id", "asc");
    SqlUtils where = new SqlUtils().add("audit_id > ?", afterAuditId);
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, constraints, AuditLogRepository::buildRecord);
    return (List<AuditLog>) result.getRecords();
  }

  /**
   * Deletes aged records and returns the count removed (NIST AU-11). Only a contiguous oldest-first prefix
   * (by audit_id) is ever removed: it deletes records whose audit_id is below the FIRST record still inside
   * the retention window. This keeps the chain verifiable -- the oldest survivor becomes a clean anchor and
   * no mid-chain gap can open -- even if occurred is not perfectly monotonic with audit_id (a backward clock
   * step or a concurrent-append inversion). The trade-off is that an aged record sitting behind a younger one
   * is retained a little longer rather than deleted out of order; over-retention is the safe direction.
   */
  public static int deleteOlderThan(int days) {
    if (days < 1) {
      // A non-positive window would place the cutoff at or in the future and delete recent evidence.
      return 0;
    }
    String threshold = "NOW() - INTERVAL '" + days + " days'";
    // The first audit_id still inside the window; if every record is aged, one past the last id so the whole
    // (contiguous) table is purged.
    String firstInWindow = "COALESCE("
        + "(SELECT MIN(audit_id) FROM " + TABLE_NAME + " WHERE occurred >= " + threshold + "), "
        + "(SELECT COALESCE(MAX(audit_id), 0) + 1 FROM " + TABLE_NAME + "))";
    return DB.deleteFrom(TABLE_NAME, new SqlUtils().add("audit_id < " + firstInWindow));
  }

  /**
   * Parses the configured audit retention window to a bounded integer. Unlike analytics retention, the floor
   * is high (90 days) so a misconfigured or hostile value cannot turn the retention job into a tool for
   * erasing recent evidence.
   */
  public static int resolveRetentionDays(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_RETENTION_DAYS;
    }
    int days;
    try {
      days = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_RETENTION_DAYS;
    }
    if (days < MIN_RETENTION_DAYS) {
      return MIN_RETENTION_DAYS;
    }
    if (days > MAX_RETENTION_DAYS) {
      return MAX_RETENTION_DAYS;
    }
    return days;
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
      record.setPreviousHash(rs.getString("previous_hash"));
      record.setRecordHash(rs.getString("record_hash"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
