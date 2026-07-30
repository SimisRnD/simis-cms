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

package com.simisinc.platform.infrastructure.persistence.login;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.login.UnsuspendRequest;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves maker-checker unsuspend requests (issue #492 Phase 3). The
 * claimForApproval/claimForDenial methods perform an atomic conditional UPDATE (WHERE
 * status='pending') as the first step of a decision, so a double-submit or two-approver race
 * never double-decides the same request -- the loser gets 0 affected rows, not a corrupted row.
 *
 * @author SimIS Inc.
 */
public class UnsuspendRequestRepository {

  private static Log LOG = LogFactory.getLog(UnsuspendRequestRepository.class);

  private static String TABLE_NAME = "unsuspend_requests";
  private static String[] PRIMARY_KEY = new String[] { "request_id" };

  public static UnsuspendRequest findById(long id) {
    if (id == -1) {
      return null;
    }
    return (UnsuspendRequest) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("request_id = ?", id),
        UnsuspendRequestRepository::buildRecord);
  }

  public static UnsuspendRequest findPendingByTargetUserId(long targetUserId) {
    if (targetUserId == -1) {
      return null;
    }
    return (UnsuspendRequest) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("target_user_id = ?", targetUserId)
            .add("status = ?", UnsuspendRequest.STATUS_PENDING),
        UnsuspendRequestRepository::buildRecord);
  }

  public static UnsuspendRequest findApprovedByTargetUserId(long targetUserId) {
    if (targetUserId == -1) {
      return null;
    }
    return (UnsuspendRequest) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils()
            .add("target_user_id = ?", targetUserId)
            .add("status = ?", UnsuspendRequest.STATUS_APPROVED),
        UnsuspendRequestRepository::buildRecord);
  }

  public static List<UnsuspendRequest> findAll(String status, DataConstraints constraints) {
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("requested_at DESC");
    SqlUtils where = new SqlUtils();
    if (StringUtils.isNotBlank(status)) {
      where.add("status = ?", status);
    }
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, constraints, UnsuspendRequestRepository::buildRecord);
    return (List<UnsuspendRequest>) result.getRecords();
  }

  public static long countPending() {
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("status = ?", UnsuspendRequest.STATUS_PENDING));
  }

  public static UnsuspendRequest add(UnsuspendRequest record) {
    SqlUtils insertValues = new SqlUtils()
        .add("target_user_id", record.getTargetUserId())
        .addIfExists("target_email", StringUtils.trimToNull(record.getTargetEmail()))
        .addIfExists("target_role_snapshot", StringUtils.trimToNull(record.getTargetRoleSnapshot()))
        .add("requested_by", record.getRequestedBy())
        .addIfExists("requested_by_email", StringUtils.trimToNull(record.getRequestedByEmail()))
        .add("reason", StringUtils.trimToNull(record.getReason()), 255)
        .add("status", UnsuspendRequest.STATUS_PENDING);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  /**
   * Atomically claims a pending request for approval -- returns false (no rows affected) if the
   * request was already decided by someone else, rather than overwriting a prior decision.
   */
  public static boolean claimForApproval(long requestId, long approvingAdminId, String approvingAdminEmail) {
    SqlUtils updateValues = new SqlUtils()
        .add("status", UnsuspendRequest.STATUS_APPROVED)
        .add("decided_by", approvingAdminId)
        .addIfExists("decided_by_email", StringUtils.trimToNull(approvingAdminEmail))
        .add("decided_at", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("request_id = ?", requestId)
        .add("status = ?", UnsuspendRequest.STATUS_PENDING);
    return DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Atomically claims a pending request for denial -- same race protection as claimForApproval.
   */
  public static boolean claimForDenial(long requestId, long decidingAdminId, String decidingAdminEmail,
      String denialReason) {
    SqlUtils updateValues = new SqlUtils()
        .add("status", UnsuspendRequest.STATUS_DENIED)
        .add("decided_by", decidingAdminId)
        .addIfExists("decided_by_email", StringUtils.trimToNull(decidingAdminEmail))
        .add("decided_at", new Timestamp(System.currentTimeMillis()))
        .add("decision_reason", StringUtils.trimToNull(denialReason), 255);
    SqlUtils where = new SqlUtils()
        .add("request_id = ?", requestId)
        .add("status = ?", UnsuspendRequest.STATUS_PENDING);
    return DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Marks a request 'reverified' once the account holder completes the forced password reset.
   */
  public static boolean markReverified(long requestId) {
    SqlUtils updateValues = new SqlUtils().add("status", UnsuspendRequest.STATUS_REVERIFIED);
    SqlUtils where = new SqlUtils()
        .add("request_id = ?", requestId)
        .add("status = ?", UnsuspendRequest.STATUS_APPROVED);
    return DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Closes out any request left in a now-moot state for this target -- called when the target is
   * suspended again before ever completing the flow. Covers both a still-pending request (the
   * approval question is moot, a fresh suspend/unsuspend cycle supersedes it) and an
   * approved-but-not-yet-reverified request (the "you must reset your password" state is moot too
   * -- a later unsuspend of this NEW suspension starts a brand-new governance cycle).
   */
  public static void supersedePendingForTarget(long targetUserId, String reason) {
    SqlUtils updateValues = new SqlUtils()
        .add("status", UnsuspendRequest.STATUS_SUPERSEDED)
        .add("decision_reason", StringUtils.trimToNull(reason), 255);
    for (String supersededFromStatus : new String[] { UnsuspendRequest.STATUS_PENDING, UnsuspendRequest.STATUS_APPROVED }) {
      SqlUtils where = new SqlUtils()
          .add("target_user_id = ?", targetUserId)
          .add("status = ?", supersededFromStatus);
      DB.update(TABLE_NAME, updateValues, where);
    }
  }

  private static UnsuspendRequest buildRecord(ResultSet rs) {
    try {
      UnsuspendRequest record = new UnsuspendRequest();
      record.setId(rs.getLong("request_id"));
      record.setTargetUserId(rs.getLong("target_user_id"));
      record.setTargetEmail(rs.getString("target_email"));
      record.setTargetRoleSnapshot(rs.getString("target_role_snapshot"));
      record.setRequestedBy(rs.getLong("requested_by"));
      record.setRequestedByEmail(rs.getString("requested_by_email"));
      record.setReason(rs.getString("reason"));
      record.setRequestedAt(rs.getTimestamp("requested_at"));
      record.setStatus(rs.getString("status"));
      long decidedBy = rs.getLong("decided_by");
      record.setDecidedBy(rs.wasNull() ? null : decidedBy);
      record.setDecidedByEmail(rs.getString("decided_by_email"));
      record.setDecidedAt(rs.getTimestamp("decided_at"));
      record.setDecisionReason(rs.getString("decision_reason"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
