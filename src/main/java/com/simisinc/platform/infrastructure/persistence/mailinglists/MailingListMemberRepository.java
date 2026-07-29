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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.dashboard.StatisticsData;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists and retrieves mailing list member objects
 *
 * @author matt rajkowski
 * @created 3/25/19 9:10 PM
 */
public class MailingListMemberRepository {

  private static Log LOG = LogFactory.getLog(MailingListMemberRepository.class);

  private static String TABLE_NAME = "mailing_list_members";
  private static String JOIN =
      "LEFT JOIN emails ON (mailing_list_members.email_id = emails.email_id) " +
      "LEFT JOIN mailing_lists ON (mailing_list_members.list_id = mailing_lists.list_id)";
  private static String[] PRIMARY_KEY = new String[]{"member_id"};

  // Statuses ZeroBounce itself calls undeliverable/dangerous (issue #564). catch-all and unknown
  // are deliberately excluded -- ZeroBounce isn't claiming those are bad, just unresolved, so
  // quarantining them would risk archiving real subscribers ZeroBounce simply couldn't fully verify.
  private static final String QUARANTINE_TRIGGER_STATUSES_SQL = "('invalid', 'spamtrap', 'abuse', 'do_not_mail')";

  private static final int DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT = 10;

  public static void addEmailToList(Email email, MailingList mailingList) {
    // Determine if the email is already listed
    SqlUtils insertValues = new SqlUtils()
        .add("list_id", mailingList.getId())
        .add("email_id", email.getId())
        .addIfExists("created_by", email.getCreatedBy(), -1)
        .addIfExists("modified_by", email.getCreatedBy(), -1);
    long memberId = DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY);
    if (memberId > -1) {
      // New member - Update the related count
      String set = "member_count = member_count + 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    } else {
      // Make sure email is set to subscribed
      SqlUtils updateValues = new SqlUtils()
          .add("unsubscribed", (Timestamp) null)
          .add("modified", new Timestamp(System.currentTimeMillis()))
          .addIfExists("modified_by", email.getModifiedBy(), -1)
          .add("is_valid", true);
      SqlUtils where = new SqlUtils()
          .add("list_id = ?", mailingList.getId())
          .add("email_id = ?", email.getId());
      DB.update(TABLE_NAME, updateValues, where);
    }
  }

  public static void remove(Email email, MailingList mailingList) {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("email_id = ?", email.getId());
    deleteWhere.add("list_id = ?", mailingList.getId());
    int count = DB.deleteFrom(TABLE_NAME, deleteWhere);
    if (count > 0) {
      // Update the related count
      String set = "member_count = member_count - 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    }
  }

  public static void removeAll(Connection connection, MailingList mailingList) throws SQLException {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("list_id = ?", mailingList.getId());
    DB.deleteFrom(connection, TABLE_NAME, deleteWhere);
  }

  public static void unsubscribe(MailingList mailingList, Email email, User user) {
    // Make sure email is set to unsubscribed
    SqlUtils updateValues = new SqlUtils()
        .add("unsubscribed", new Timestamp(System.currentTimeMillis()))
        .add("unsubscribed_by", user.getId())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("modified_by", user.getId())
        .add("is_valid", false);
    SqlUtils where = new SqlUtils()
        .add("list_id = ?", mailingList.getId())
        .add("email_id = ?", email.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  /**
   * Distinct people subscribed to at least one list, ever (issue #562). mailing_list_members is
   * unique per (list_id, email_id), not per person -- someone on 3 lists has 3 rows, so this uses
   * COUNT(DISTINCT email_id), not COUNT(*), to avoid counting that person 3 times. Replaces the old
   * "Total Sign-ups" tile, which summed mailing_lists.member_count -- a counter that is never
   * decremented on unsubscribe() (only a hard delete decrements it), so it drifts upward over time.
   */
  public static long countDistinctSubscribers() {
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, null);
  }

  /** Distinct people with at least one currently-valid (not unsubscribed/invalidated) list membership. */
  public static long countActiveSubscribers() {
    SqlUtils where = new SqlUtils().add("is_valid = ?", true);
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /**
   * Distinct people who have unsubscribed from at least one list. Not the complement of
   * countActiveSubscribers(): a person can be actively subscribed to one list and unsubscribed from
   * another at the same time, so these two counts can overlap.
   */
  public static long countUnsubscribed() {
    SqlUtils where = new SqlUtils().add("unsubscribed IS NOT NULL");
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /** Day-bucketed new-subscription counts, zero-filled, mirroring UserRepository.findDailyUserRegistrations. */
  public static List<StatisticsData> findDailySubscriptions(int daysToLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('day', day)::VARCHAR(10) AS date_column, COUNT(member_id) AS daily_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + daysToLimit + " days', NOW(), INTERVAL '1 day')::date) d(day) " +
            "LEFT JOIN mailing_list_members ON DATE_TRUNC('day', created) = DATE_TRUNC('day', d.day) " +
            "GROUP BY d.day " +
            "ORDER BY d.day";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  /** Month-bucketed new-subscription counts, zero-filled, mirroring UserRepository.findMonthlyUserRegistrations. */
  public static List<StatisticsData> findMonthlySubscriptions(int monthsLimit) {
    String SQL_QUERY =
        "SELECT DATE_TRUNC('month', month)::VARCHAR(10) AS date_column, COUNT(member_id) AS monthly_count " +
            "FROM (SELECT generate_series(NOW() - INTERVAL '" + monthsLimit + " months', NOW(), INTERVAL '1 month')::date) d(month) " +
            "LEFT JOIN mailing_list_members ON DATE_TRUNC('month', created) = DATE_TRUNC('month', month) " +
            "GROUP BY d.month " +
            "ORDER BY d.month";
    return queryDateBucketedCounts(SQL_QUERY);
  }

  private static List<StatisticsData> queryDateBucketedCounts(String sqlQuery) {
    List<StatisticsData> records = null;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(sqlQuery);
         ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        StatisticsData data = new StatisticsData();
        data.setLabel(rs.getString("date_column"));
        data.setValue(String.valueOf(rs.getLong(2)));
        records.add(data);
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return records;
  }

  /**
   * Quarantines (archives, does not delete) every currently-active membership whose linked email
   * has a confirmed-bad deliverability classification and isn't already quarantined (issue #564).
   * Sets is_valid = false, exactly as unsubscribe() does, so a quarantined membership
   * automatically drops out of countActiveSubscribers() -- but does NOT touch the unsubscribed
   * column, since quarantine is a distinct reason a membership stopped being active, not a person
   * choosing to leave.
   *
   * @return the number of memberships newly quarantined by this call
   */
  public static int quarantineFlaggedMembers() {
    String SQL_QUERY =
        "UPDATE " + TABLE_NAME + " SET is_valid = false, quarantined = CURRENT_TIMESTAMP, " +
            "quarantine_reason = emails.validation_status " +
            "FROM emails " +
            "WHERE " + TABLE_NAME + ".email_id = emails.email_id " +
            "AND " + TABLE_NAME + ".quarantined IS NULL " +
            "AND " + TABLE_NAME + ".is_valid = true " +
            "AND emails.validation_status IN " + QUARANTINE_TRIGGER_STATUSES_SQL;
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
      return 0;
    }
  }

  /** Distinct people currently quarantined on at least one list. */
  public static long countQuarantined() {
    SqlUtils where = new SqlUtils().add("quarantined IS NOT NULL");
    return DB.selectFunction("COUNT(DISTINCT email_id)", TABLE_NAME, where);
  }

  /**
   * A 0-100 "mailing list quality" score: of the distinct subscribers who have actually been
   * classified, what percentage are NOT a quarantine-triggering status. Deliberately mirrors
   * QUARANTINE_TRIGGER_STATUSES_SQL rather than only counting "valid" as good, so catch-all/unknown
   * (which don't trigger quarantine either) don't drag the score down as if they were confirmed bad.
   * Returns 100 (no known problems) when nothing has been classified yet, rather than an undefined
   * or misleadingly alarming value -- e.g. before ZeroBounce is even configured.
   */
  public static double findQualityScorePercent() {
    String SQL_QUERY =
        "SELECT COUNT(*) FILTER (WHERE validation_status NOT IN " + QUARANTINE_TRIGGER_STATUSES_SQL + ") AS good_count, " +
            "COUNT(*) AS classified_count " +
            "FROM (SELECT DISTINCT emails.email_id, emails.validation_status " +
            "FROM " + TABLE_NAME + " " + JOIN + " " +
            "WHERE emails.validation_status IS NOT NULL) classified_subscribers";
    try (Connection connection = DB.getConnection();
         PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
         ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        long classifiedCount = rs.getLong("classified_count");
        if (classifiedCount == 0) {
          return 100;
        }
        long goodCount = rs.getLong("good_count");
        return 100.0 * goodCount / classifiedCount;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return 100;
  }

  /**
   * Parses the configurable mailing-list.quarantine.alertThresholdPercent site property, falling
   * back to the default on a blank or unparseable value and clamping to a sane 0-100 range --
   * mirrors AuditLogRepository.resolveRetentionDays's exact shape for the same reason: a bad or
   * missing config value must degrade to a safe default, never break the dashboard tile.
   */
  public static int resolveQuarantineAlertThresholdPercent(String value) {
    if (StringUtils.isBlank(value)) {
      return DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT;
    }
    int percent;
    try {
      percent = Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return DEFAULT_QUARANTINE_ALERT_THRESHOLD_PERCENT;
    }
    if (percent < 0) {
      return 0;
    }
    if (percent > 100) {
      return 100;
    }
    return percent;
  }

  public static void export(MailingListMemberSpecification specification, DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "mailing_lists.name AS list",
            "email",
            "first_name",
            "last_name",
            "organization",
            "mailing_list_members.created AS subscribed",
            "mailing_list_members.unsubscribed AS unsubscribed",
            "emails.unsubscribed AS ref_unsubscribed",
            "is_valid");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    // Use the specification to filter results
    if (specification != null) {
      if (specification.getMailingListId() > -1) {
        where.add("mailing_list_members.list_id = ?", specification.getMailingListId());
      }
    }
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("mailing_list_members.created");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }
}
