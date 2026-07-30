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

package com.simisinc.platform.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves direct capability grants (issue #702) - the individually-trackable,
 * possibly time-limited counterpart to role_capabilities (#701).
 *
 * @author elizabeth houser
 */
public class CapabilityGrantRepository {

  private static Log LOG = LogFactory.getLog(CapabilityGrantRepository.class);

  private static String TABLE_NAME = "capability_grants";
  private static String[] PRIMARY_KEY = new String[]{"capability_grant_id"};

  /**
   * A user's active (not revoked, not yet expired) direct grants, for both the effective-
   * capability resolution at login and the per-user admin UI.
   */
  public static List<CapabilityGrant> findActiveByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils()
        .add("user_id = ?", userId)
        .add("revoked_at IS NULL")
        .add("(expires_at IS NULL OR expires_at > NOW())");
    return query(where);
  }

  /**
   * Every grant for a user, active or not, for the admin UI's history view.
   */
  public static List<CapabilityGrant> findAllByUserId(long userId) {
    if (userId == -1) {
      return null;
    }
    SqlUtils where = new SqlUtils().add("user_id = ?", userId);
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, where, new DataConstraints().setDefaultColumnToSortBy("granted DESC"),
        CapabilityGrantRepository::buildRecord);
    return withCapabilityCodes(result.hasRecords() ? (List<CapabilityGrant>) result.getRecords() : null);
  }

  public static CapabilityGrant findById(long id) {
    if (id == -1) {
      return null;
    }
    CapabilityGrant record = (CapabilityGrant) DB.selectRecordFrom(
        TABLE_NAME, new SqlUtils().add("capability_grant_id = ?", id),
        CapabilityGrantRepository::buildRecord);
    if (record == null) {
      return null;
    }
    withCapabilityCodes(List.of(record));
    return record;
  }

  /**
   * Active grants whose expiration has already passed - CapabilityGrantExpirationJob's revoke
   * sweep.
   */
  public static List<CapabilityGrant> findExpired() {
    SqlUtils where = new SqlUtils()
        .add("revoked_at IS NULL")
        .add("expires_at IS NOT NULL")
        .add("expires_at <= NOW()");
    return query(where);
  }

  /**
   * Active grants expiring within the given number of days that haven't already been notified -
   * CapabilityGrantExpirationJob's notification sweep.
   */
  public static List<CapabilityGrant> findExpiringWithinDaysNotYetNotified(int days) {
    SqlUtils where = new SqlUtils()
        .add("revoked_at IS NULL")
        .add("expires_at IS NOT NULL")
        .add("expires_at > NOW()")
        .add("expires_at <= (NOW() + (? || ' days')::interval)", String.valueOf(days))
        .add("expiration_notified_at IS NULL");
    return query(where);
  }

  private static List<CapabilityGrant> query(SqlUtils where) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME, where, new DataConstraints().setDefaultColumnToSortBy("granted DESC").setUseCount(false),
        CapabilityGrantRepository::buildRecord);
    return withCapabilityCodes(result.hasRecords() ? (List<CapabilityGrant>) result.getRecords() : null);
  }

  public static CapabilityGrant add(CapabilityGrant record) {
    SqlUtils insertValues = new SqlUtils()
        .add("user_id", record.getUserId())
        .add("capability_id", record.getCapabilityId())
        .add("granted_by", record.getGrantedBy())
        .add("reason", record.getReason())
        .add("expires_at", record.getExpiresAt());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static boolean revoke(long id) {
    SqlUtils updateValues = new SqlUtils().add("revoked_at", new Timestamp(System.currentTimeMillis()));
    return DB.update(TABLE_NAME, updateValues, new SqlUtils().add("capability_grant_id = ?", id));
  }

  public static boolean markExpirationNotified(long id) {
    SqlUtils updateValues = new SqlUtils().add("expiration_notified_at", new Timestamp(System.currentTimeMillis()));
    return DB.update(TABLE_NAME, updateValues, new SqlUtils().add("capability_grant_id = ?", id));
  }

  /**
   * The capabilities table is tiny (a handful of rows) - fetching it once per query and mapping
   * in memory is simpler than a join for every call site here, and cheap enough not to matter.
   */
  private static List<CapabilityGrant> withCapabilityCodes(List<CapabilityGrant> grants) {
    if (grants == null || grants.isEmpty()) {
      return grants;
    }
    List<Capability> allCapabilities = CapabilityRepository.findAll();
    Map<Long, String> codesById = new HashMap<>();
    if (allCapabilities != null) {
      for (Capability capability : allCapabilities) {
        codesById.put(capability.getId(), capability.getCode());
      }
    }
    for (CapabilityGrant grant : grants) {
      grant.setCapabilityCode(codesById.get(grant.getCapabilityId()));
    }
    return grants;
  }

  private static CapabilityGrant buildRecord(ResultSet rs) {
    try {
      CapabilityGrant record = new CapabilityGrant();
      record.setId(rs.getLong("capability_grant_id"));
      record.setUserId(rs.getLong("user_id"));
      record.setCapabilityId(rs.getLong("capability_id"));
      long grantedBy = rs.getLong("granted_by");
      record.setGrantedBy(rs.wasNull() ? null : grantedBy);
      record.setGranted(rs.getTimestamp("granted"));
      record.setReason(rs.getString("reason"));
      record.setExpiresAt(rs.getTimestamp("expires_at"));
      record.setRevokedAt(rs.getTimestamp("revoked_at"));
      record.setExpirationNotifiedAt(rs.getTimestamp("expiration_notified_at"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
