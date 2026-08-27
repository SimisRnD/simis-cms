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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists role_capabilities join rows (issue #704) - the only place role_capabilities is ever
 * mutated at runtime. Everything seeded by the #701 walking-skeleton migration was a one-time
 * INSERT with no equivalent runtime path until this repository.
 *
 * @author elizabeth houser
 */
public class RoleCapabilityRepository {

  private static Log LOG = LogFactory.getLog(RoleCapabilityRepository.class);

  private static String TABLE_NAME = "role_capabilities";

  /**
   * Sentinel meaning "don't exclude anything" for {@link #countDistinctUsersHoldingCapability(long,
   * long, long)}'s excludeRoleId/excludeGrantId, matching this codebase's existing convention of -1
   * meaning "no id" (e.g. CapabilityGrantRepository#findActiveByUserId).
   */
  private static final long NO_EXCLUSION = -1L;

  public static boolean grant(long roleId, long capabilityId) {
    SqlUtils insertValues = new SqlUtils()
        .add("role_id", roleId)
        .add("capability_id", capabilityId);
    return DB.insertIntoWithConflict(TABLE_NAME, insertValues, "ON CONFLICT DO NOTHING");
  }

  public static boolean revoke(long roleId, long capabilityId) {
    SqlUtils where = new SqlUtils()
        .add("role_id = ?", roleId)
        .add("capability_id = ?", capabilityId);
    return DB.deleteFrom(TABLE_NAME, where) > 0;
  }

  /**
   * How many distinct users, system-wide, currently effectively hold this capability - either
   * through a role that grants it (joined through user_roles), or through their own active (not
   * revoked, not expired - same criteria as CapabilityGrantRepository#findActiveByUserId) direct
   * grant of it. Used by SaveRoleCapabilitiesCommand and SaveCapabilityGrantCommand's admin:manage
   * self-lockout guards, in place of the old, unsound "how many roles list this capability" count
   * (a role with zero members still counted as a holder).
   */
  public static long countDistinctUsersHoldingCapability(long capabilityId) {
    return countDistinctUsersHoldingCapability(capabilityId, NO_EXCLUSION, NO_EXCLUSION);
  }

  /**
   * The user ids that currently effectively hold this capability -- the same population
   * {@link #countDistinctUsersHoldingCapability(long)} counts, returned rather than tallied.
   *
   * <p>Deliberately the same two branches and the same gates: a role that grants it (through
   * user_roles), or the user's own direct grant that is neither revoked nor expired, and in both
   * cases only accounts that are enabled and validated. A notification addressed at a capability
   * should reach exactly the people the lockout guards consider to hold it -- if the two ever
   * disagreed, one of them would be wrong about who is responsible for the system.
   *
   * <p>Returns an empty list on a query failure. Every caller treats that as "tell nobody", which
   * is the safe direction here: a missed notification is recoverable, whereas failing open would
   * mean mailing an unknown set of people about an event they may have no business seeing.
   */
  public static List<Long> findUserIdsHoldingCapability(long capabilityId) {
    List<Long> userIds = new ArrayList<>();
    String sql = "SELECT DISTINCT effective_holders.user_id FROM ("
        + "SELECT user_roles.user_id AS user_id "
        + "FROM user_roles "
        + "JOIN role_capabilities ON role_capabilities.role_id = user_roles.role_id "
        + "JOIN users ON users.user_id = user_roles.user_id "
        + "WHERE role_capabilities.capability_id = ? "
        + "AND users.enabled = true AND users.validated IS NOT NULL "
        + "UNION "
        + "SELECT capability_grants.user_id AS user_id "
        + "FROM capability_grants "
        + "JOIN users ON users.user_id = capability_grants.user_id "
        + "WHERE capability_grants.capability_id = ? "
        + "AND capability_grants.revoked_at IS NULL "
        + "AND (capability_grants.expires_at IS NULL OR capability_grants.expires_at > NOW()) "
        + "AND users.enabled = true AND users.validated IS NOT NULL "
        + ") AS effective_holders";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setLong(1, capabilityId);
      pst.setLong(2, capabilityId);
      try (ResultSet rs = pst.executeQuery()) {
        while (rs.next()) {
          userIds.add(rs.getLong(1));
        }
      }
    } catch (SQLException se) {
      LOG.error("findUserIdsHoldingCapability SQLException: " + se.getMessage());
      return new ArrayList<>();
    }
    return userIds;
  }

  /**
   * Same effective-holder count as {@link #countDistinctUsersHoldingCapability(long)}, but with one
   * role's role_capabilities row and/or one specific capability_grants row left out of the union
   * before counting. This is how the self-lockout guards ask "if I removed *this one* grant right
   * now, would anyone still be covered?" as a single query against current data, rather than reading
   * the current total and subtracting in Java against a snapshot that can go stale between the read
   * and the write. Pass {@code -1} for whichever of excludeRoleId/excludeGrantId doesn't apply.
   *
   * <p>On a query failure this returns 0 rather than propagating - every caller of this method
   * treats 0 as "block the revoke", which is the safe direction to fail toward: better to
   * occasionally refuse a technically-safe revoke than to silently allow a real lockout because a
   * transient DB error was misread as "nobody else holds this."
   */
  public static long countDistinctUsersHoldingCapability(long capabilityId, long excludeRoleId,
      long excludeGrantId) {
    // Both branches join to users and require enabled = true AND validated IS NOT NULL -- the
    // same two gates AuthenticateLoginCommand checks before allowing a login. A user who fails
    // either can never authenticate, so counting them as a "still holds it" safety margin would
    // let the self-lockout guard approve a revoke that leaves nobody who can actually log in and
    // reach /admin/role-capabilities or /admin/capability-grants to fix it.
    StringBuilder sql = new StringBuilder()
        .append("SELECT COUNT(DISTINCT effective_holders.user_id) FROM (")
        .append("SELECT user_roles.user_id AS user_id ")
        .append("FROM user_roles ")
        .append("JOIN role_capabilities ON role_capabilities.role_id = user_roles.role_id ")
        .append("JOIN users ON users.user_id = user_roles.user_id ")
        .append("WHERE role_capabilities.capability_id = ? ")
        .append("AND users.enabled = true AND users.validated IS NOT NULL ");
    if (excludeRoleId != NO_EXCLUSION) {
      sql.append("AND role_capabilities.role_id != ? ");
    }
    sql.append("UNION ")
        .append("SELECT capability_grants.user_id AS user_id ")
        .append("FROM capability_grants ")
        .append("JOIN users ON users.user_id = capability_grants.user_id ")
        .append("WHERE capability_grants.capability_id = ? ")
        .append("AND capability_grants.revoked_at IS NULL ")
        .append("AND (capability_grants.expires_at IS NULL OR capability_grants.expires_at > NOW()) ")
        .append("AND users.enabled = true AND users.validated IS NOT NULL ");
    if (excludeGrantId != NO_EXCLUSION) {
      sql.append("AND capability_grants.capability_grant_id != ? ");
    }
    sql.append(") AS effective_holders");

    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql.toString())) {
      int fieldIdx = 1;
      pst.setLong(fieldIdx++, capabilityId);
      if (excludeRoleId != NO_EXCLUSION) {
        pst.setLong(fieldIdx++, excludeRoleId);
      }
      pst.setLong(fieldIdx++, capabilityId);
      if (excludeGrantId != NO_EXCLUSION) {
        pst.setLong(fieldIdx++, excludeGrantId);
      }
      try (ResultSet rs = pst.executeQuery()) {
        if (rs.next()) {
          return rs.getLong(1);
        }
      }
    } catch (SQLException se) {
      LOG.error("countDistinctUsersHoldingCapability SQLException: " + se.getMessage());
    }
    return 0;
  }
}
