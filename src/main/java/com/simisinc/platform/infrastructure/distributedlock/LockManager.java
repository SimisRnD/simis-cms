/*
 * Copyright 2023 SimIS Inc. (https://www.simiscms.com)
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
package com.simisinc.platform.infrastructure.distributedlock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;

/**
 * A distributed lock implementation
 *
 * @author matt rajkowski
 * @created 3/26/23 5:00 PM
 */
public class LockManager {

  private static Log LOG = LogFactory.getLog(LockManager.class);

  private static String TABLE_NAME = "distributed_lock";

  // PostgreSQL's SQLState for "undefined_table" -- the one condition that actually means "this
  // relation does not exist". information_schema.tables always exists, so a missing target table
  // in lockTableExists() below normally surfaces as zero rows (see the rs.next() check), never as
  // this SQLState; it is still checked defensively, in case that query is ever rewritten to hit
  // the target table directly.
  private static final String UNDEFINED_TABLE_SQLSTATE = "42P01";

  public static String lock(String name, Duration duration) {

    String uuid = UUID.randomUUID().toString();

    // INSERT or UPDATE
    SqlUtils insertValues = new SqlUtils()
        .add("name", name)
        .add(new SqlValue("locked_at", SqlValue.AS_IS, "CURRENT_TIMESTAMP"))
        .add(new SqlValue("lock_until", SqlValue.AS_IS, "CURRENT_TIMESTAMP - INTERVAL '10 SECONDS' + INTERVAL '" + duration.toString() + "'"))
        .add("uuid", uuid);

    String onConflict = "ON CONFLICT (name) " +
        "DO UPDATE SET " +
        "locked_at = EXCLUDED.locked_at, " +
        "lock_until = EXCLUDED.lock_until, " +
        "uuid = EXCLUDED.uuid " +
        "WHERE distributed_lock.name = EXCLUDED.name AND CURRENT_TIMESTAMP >= distributed_lock.lock_until";

    if (DB.insertIntoWithConflict(TABLE_NAME, insertValues, onConflict)) {
      LOG.debug("Lock succeeded: " + name);
      return uuid;
    }
    return null;
  }

  /**
   * Reports whether the {@code distributed_lock} table itself exists yet. On a never-installed
   * database this table doesn't exist -- it's created by the same Flyway install migration that
   * {@link #lock} would otherwise be guarding -- so {@link #lock} returning {@code null} is
   * ambiguous between "another node holds this lock" and "this table doesn't exist yet". Callers
   * that need to tell those apart (see DatabaseCommand) should check this first.
   *
   * <p>
   * Fails CLOSED, not open: a {@link SQLException} that isn't clearly "relation does not exist"
   * (connection-pool exhaustion, a network blip, the database restarting, etc.) says nothing about
   * whether the table exists, and simultaneous multi-node boot -- the exact scenario this lock
   * exists to protect -- is precisely when that kind of contention is most likely. Returning {@code
   * false} for those errors would make the caller take the no-lock first-install path, silently
   * reintroducing the race issue #396 exists to close. Only a genuine "relation does not exist"
   * condition (PostgreSQL SQLState {@value #UNDEFINED_TABLE_SQLSTATE}) is treated as "doesn't exist
   * yet"; every other error is treated as "assume it exists" so the caller goes through the normal
   * {@code acquireMigrationLock()} retry path instead.
   * </p>
   *
   * @return true if the table exists, or if its existence could not be determined for a reason
   *         other than the relation genuinely not existing; false only when the relation is
   *         confirmed absent
   */
  public static boolean lockTableExists() {
    String sql = "SELECT 1 FROM information_schema.tables WHERE table_name = ?";
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = connection.prepareStatement(sql)) {
      pst.setString(1, TABLE_NAME);
      try (ResultSet rs = pst.executeQuery()) {
        return rs.next();
      }
    } catch (SQLException se) {
      if (UNDEFINED_TABLE_SQLSTATE.equals(se.getSQLState())) {
        LOG.info("Lock table lookup reports the relation does not exist yet: " + se.getMessage());
        return false;
      }
      LOG.error("Could not determine if the lock table exists; treating it as present so migration "
          + "locking is not silently skipped: " + se.getMessage(), se);
      return true;
    }
  }

  public static boolean unlock(String name, String uuid) {
    // Expire right away -- but a full second in the past, not CURRENT_TIMESTAMP.
    //
    // lock_until is TIMESTAMP(3), and PostgreSQL ROUNDS to the column's precision on store rather
    // than truncating. Writing CURRENT_TIMESTAMP therefore lands up to half a millisecond in the
    // FUTURE: released at ...123.9004 is stored as ...124.000. lock() re-acquires only when
    // CURRENT_TIMESTAMP >= lock_until, so a caller that releases and immediately re-locks is
    // refused for the remainder of that rounding window, having just been told the release
    // succeeded.
    //
    // That is issue 1625: LockManagerTest's round-trip failed on its re-lock, intermittently and
    // only on CI, while the three assertions before it passed every time. It is the same
    // JVM-versus-database precision family as the TIMESTAMP(3) flake in issue 1195.
    //
    // A second of margin is arbitrary but safe in the only direction that matters: lock_until is
    // read solely by lock()'s conflict predicate above, where any past value means "available",
    // and a released lock is available by definition. It also mirrors the 10-second margin lock()
    // already subtracts for clock skew.
    SqlUtils updateValues = new SqlUtils()
        .add(new SqlValue("lock_until", SqlValue.AS_IS, "CURRENT_TIMESTAMP - INTERVAL '1 SECOND'"));

    SqlUtils where = new SqlUtils()
        .add("name = ?", name)
        .add("uuid = ?", uuid);
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return true;
    }
    return false;
  }
}
