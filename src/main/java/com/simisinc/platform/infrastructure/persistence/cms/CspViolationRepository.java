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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.CspViolation;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists aggregated Content-Security-Policy violation reports.
 *
 * @author SimIS Inc.
 */
public class CspViolationRepository {

  private static Log LOG = LogFactory.getLog(CspViolationRepository.class);

  private static String TABLE_NAME = "csp_violation";
  private static String PRIMARY_KEY[] = new String[] { "violation_id" };

  /**
   * The most distinct (directive, host) pairs that will ever be stored.
   *
   * <p>
   * /csp-report has to be unauthenticated -- browsers post violation reports without credentials --
   * so anyone can post to it, and a report names the blocked host. Counting repeats is safe because
   * the upsert only touches an existing row, but a poster inventing a new host every time would add
   * a row every time. Past this many distinct pairs, existing rows keep counting up and new ones are
   * refused.
   * </p>
   *
   * <p>
   * A real site produces a handful: the directives under test times the third-party hosts actually
   * in use. Reaching this cap means either the policy under test is far too strict to learn anything
   * from, or the endpoint is being fed junk. Either way the answer is to look, not to store more.
   * </p>
   */
  public static final long MAX_DISTINCT_VIOLATIONS = 500;

  /**
   * Records one violation, counting it against an existing (directive, host) row when there is one.
   *
   * @param effectiveDirective the directive that refused the load, e.g. "connect-src"
   * @param blockedHost the host of the blocked url, or a CSP keyword like 'inline'
   * @param documentPath the path of the page it happened on, without any query string
   * @return true when the violation was recorded
   */
  public static boolean save(String effectiveDirective, String blockedHost, String documentPath) {
    if (effectiveDirective == null || blockedHost == null) {
      return false;
    }
    // Only guard the cap when this pair is new; an existing row is a counter update, not growth
    if (!exists(effectiveDirective, blockedHost) && count() >= MAX_DISTINCT_VIOLATIONS) {
      LOG.warn("Refusing a new CSP violation pair; already holding " + MAX_DISTINCT_VIOLATIONS
          + " distinct pairs. Review /admin/csp-violations and clear it once the results are recorded.");
      return false;
    }
    SqlUtils insertValues = new SqlUtils()
        .add("effective_directive", effectiveDirective)
        .add("blocked_host", blockedHost)
        .add("occurrences", 1L)
        .add("sample_document_path", documentPath);
    // The sample path is kept from the first report only. It exists to answer "where do I go to see
    // this?", and the first page to trip a directive answers that as well as the newest one would.
    String onConflict = "ON CONFLICT (effective_directive, blocked_host) "
        + "DO UPDATE SET "
        + "occurrences = " + TABLE_NAME + ".occurrences + 1, "
        + "last_seen = CURRENT_TIMESTAMP";
    return DB.insertIntoWithConflict(TABLE_NAME, insertValues, onConflict);
  }

  public static long count() {
    return DB.selectCountFrom(TABLE_NAME);
  }

  private static boolean exists(String effectiveDirective, String blockedHost) {
    SqlUtils where = new SqlUtils()
        .add("effective_directive = ?", effectiveDirective)
        .add("blocked_host = ?", blockedHost);
    return DB.selectCountFrom(TABLE_NAME, where) > 0;
  }

  /** Everything collected, the most recently seen first. */
  public static List<CspViolation> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils(),
        new DataConstraints().setDefaultColumnToSortBy("last_seen DESC"),
        CspViolationRepository::buildRecord);
    if (result != null && result.hasRecords()) {
      return (List<CspViolation>) result.getRecords();
    }
    return new ArrayList<>();
  }

  /** Removes everything, for once a policy has been updated from the results. */
  public static int deleteAll() {
    return DB.deleteFrom(TABLE_NAME, new SqlUtils());
  }

  private static CspViolation buildRecord(ResultSet rs) {
    try {
      CspViolation record = new CspViolation();
      record.setId(rs.getLong("violation_id"));
      record.setEffectiveDirective(rs.getString("effective_directive"));
      record.setBlockedHost(rs.getString("blocked_host"));
      record.setOccurrences(rs.getLong("occurrences"));
      record.setSampleDocumentPath(rs.getString("sample_document_path"));
      record.setFirstSeen(rs.getTimestamp("first_seen"));
      record.setLastSeen(rs.getTimestamp("last_seen"));
      return record;
    } catch (Exception se) {
      LOG.error("buildRecord", se);
    }
    return null;
  }
}
