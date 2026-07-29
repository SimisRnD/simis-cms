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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.domain.model.mailinglists.MailingListSent;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * Persists and retrieves per-recipient rows within a mailing list send batch
 * (mailing_list_history) -- queued, processing, sent, or failed.
 *
 * @author SimIS Inc.
 */
public class MailingListSentRepository {

  private static Log LOG = LogFactory.getLog(MailingListSentRepository.class);

  private static String TABLE_NAME = "mailing_list_sent";
  private static String[] PRIMARY_KEY = new String[] { "item_id" };

  /** Queues one row per member, all belonging to the same batch (history). */
  public static void enqueue(Connection connection, long historyId, long listId, List<Long> emailIds)
      throws SQLException {
    for (Long emailId : emailIds) {
      SqlUtils insertValues = new SqlUtils()
          .add("email_id", emailId)
          .add("list_id", listId)
          .add("history_id", historyId)
          .add("status", MailingListSent.QUEUED);
      DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
    }
  }

  /**
   * Claims up to batchSize queued rows for processing by this node. Safe under this codebase's
   * existing job-locking convention (LockManager ensures only one node's job execution is inside
   * the caller at a time), matching every other scheduled job here -- there is no row-level
   * SELECT-FOR-UPDATE/claim pattern precedent to follow, so this uses a plain select-then-update.
   */
  public static List<MailingListSent> claimBatch(int batchSize) {
    SqlUtils where = new SqlUtils().add("status = ?", MailingListSent.QUEUED);
    DataConstraints constraints = new DataConstraints(1, batchSize)
        .setDefaultColumnToSortBy("item_id")
        .setUseCount(false);
    DataResult result = DB.selectAllFrom(TABLE_NAME, where, constraints, MailingListSentRepository::buildRecord);
    List<MailingListSent> claimed = (List<MailingListSent>) result.getRecords();
    Timestamp now = new Timestamp(System.currentTimeMillis());
    for (MailingListSent record : claimed) {
      SqlUtils updateValues = new SqlUtils()
          .add("status", MailingListSent.PROCESSING)
          .add("claimed_at", now)
          .add("modified", now);
      SqlUtils itemWhere = new SqlUtils().add("item_id = ?", record.getId());
      DB.update(TABLE_NAME, updateValues, itemWhere);
      record.setStatus(MailingListSent.PROCESSING);
      record.setClaimedAt(now);
    }
    return claimed;
  }

  /** The recipient unsubscribed (or their membership was removed) between enqueue and send. */
  public static void markSkipped(MailingListSent record) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("status", MailingListSent.SKIPPED)
        .add("modified", now);
    SqlUtils where = new SqlUtils().add("item_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  public static void markSent(MailingListSent record) {
    Timestamp now = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("status", MailingListSent.SENT)
        .add("modified", now);
    SqlUtils where = new SqlUtils().add("item_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Records a failed send attempt. Requeues for another try if under the retry cap, otherwise
   * marks permanently failed -- neither OrderManagementProcessNewOrders nor any other job in this
   * codebase demonstrates a retry-count/cutoff pattern, so this is new, not copied.
   */
  public static void markFailedOrRequeue(MailingListSent record, String errorMessage, int maxAttempts) {
    int attemptCount = record.getAttemptCount() + 1;
    boolean permanentlyFailed = attemptCount >= maxAttempts;
    Timestamp now = new Timestamp(System.currentTimeMillis());
    SqlUtils updateValues = new SqlUtils()
        .add("status", permanentlyFailed ? MailingListSent.FAILED : MailingListSent.QUEUED)
        .add("attempt_count", attemptCount)
        .add("error_message", errorMessage, 500)
        .add("modified", now);
    SqlUtils where = new SqlUtils().add("item_id = ?", record.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  private static MailingListSent buildRecord(ResultSet rs) {
    try {
      MailingListSent record = new MailingListSent();
      record.setId(rs.getLong("item_id"));
      record.setEmailId(rs.getLong("email_id"));
      record.setListId(rs.getLong("list_id"));
      record.setHistoryId(rs.getLong("history_id"));
      record.setCreated(rs.getTimestamp("created"));
      record.setStatus(rs.getString("status"));
      record.setAttemptCount(DB.getInt(rs, "attempt_count", 0));
      record.setErrorMessage(rs.getString("error_message"));
      record.setClaimedAt(rs.getTimestamp("claimed_at"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
