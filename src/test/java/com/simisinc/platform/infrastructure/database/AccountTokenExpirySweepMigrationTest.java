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

package com.simisinc.platform.infrastructure.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Covers UPGRADE_20260905.1000, the second account-token sweep.
 *
 * <p>The first sweep dated every outstanding token, and the code shipped with it stopped minting
 * undated ones. Between those two facts sits a window: migrations run on the new slot at startup
 * while the OLD instance is still serving, so anything written by the previous code in the minutes
 * before the slot swap carries no expiry and arrives after the sweep that would have caught it.
 *
 * <p>This sweep exists because the companion change to {@code findByAccountToken} makes a missing
 * expiry fail closed. Dating the stragglers first is what stops that stricter predicate silently
 * killing a link somebody was issued during the swap.
 *
 * <p>The cases below are the same populations the first sweep distinguishes, because the operation
 * has to remain idempotent: running it on a database the first sweep already cleaned must change
 * nothing, and must never extend a window twice.
 */
class AccountTokenExpirySweepMigrationTest {

  /** The migration under test; the harness derives the baseline from it (issue #1755). */
  private static final String TOKEN_EXPIRY_MIGRATION = "20260905.1000";

  private static MigrationTestHarness harness;
  private static MigrateResult migrateResult;

  @BeforeAll
  static void migrate() {
    harness = MigrationTestHarness.start("the account token expiry sweep migration test");

    // Only the columns this migration reads and writes.
    harness.execute(
        "CREATE TABLE users (user_id BIGSERIAL PRIMARY KEY, email VARCHAR(255) NOT NULL,"
            + " account_token VARCHAR(255), account_token_expires TIMESTAMP(3),"
            + " validated TIMESTAMP(3))",
        "INSERT INTO users (email, account_token, account_token_expires, validated) VALUES"
            // Pending, undated: the actual defect. Gets a fresh 7-day window.
            + " ('pending@example.com', 'tok-pending', NULL, NULL),"
            // Activated but still holding an undated token: a leftover, cleared outright.
            + " ('activated@example.com', 'tok-stale', NULL, NOW()),"
            // Already dated (a password reset): must keep its own expiry untouched.
            + " ('reset@example.com', 'tok-dated', NOW() + INTERVAL '1 hour', NULL),"
            // Dated and already lapsed: must stay lapsed, not be revived by the backfill.
            + " ('expired@example.com', 'tok-lapsed', NOW() - INTERVAL '1 hour', NULL),"
            // No token at all: nothing to do.
            + " ('none@example.com', NULL, NULL, NOW())");

    migrateResult = harness.applyOnly(TOKEN_EXPIRY_MIGRATION);
  }

  @AfterAll
  static void stopDatabase() {
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (harness != null) {
      harness.close();
    }
  }

  @Test
  void migrationAppliesSuccessfully() {
    assertTrue(migrateResult.success,
        "account token expiry sweep did not apply cleanly: " + migrateResult.warnings);
  }

  @Test
  void datesAPendingTokenAboutSevenDaysOut() {
    assertNotNull(tokenOf("pending@example.com"), "a pending token must be kept, not cleared");
    Timestamp expires = expiresOf("pending@example.com");
    assertNotNull(expires, "the pending token was left with no expiry");

    // Asserted as a range rather than an exact instant: the migration computes NOW() + 7 days on
    // the database clock, which is not the clock this assertion runs on.
    long days = (expires.getTime() - System.currentTimeMillis()) / 86_400_000L;
    assertTrue(days >= 6 && days <= 7,
        "expected a window of about 7 days, got " + days + " days (" + expires + ")");
  }

  @Test
  void clearsALeftoverTokenOnAnAlreadyValidatedAccount() {
    // updateValidated and updatePassword both clear the token on completion, so one still present
    // on a validated account is a leftover with nothing waiting on it.
    assertNull(tokenOf("activated@example.com"),
        "a validated account should not keep an activation token");
  }

  @Test
  void leavesAnAlreadyDatedTokenExactlyAsItWas() {
    // This is the idempotency guarantee: anything already carrying an expiry -- every password
    // reset, and everything the fixed code writes -- is never re-dated, so re-running the migration
    // cannot extend a window a second time.
    Timestamp expires = expiresOf("reset@example.com");
    assertNotNull(expires);
    long hours = (expires.getTime() - System.currentTimeMillis()) / 3_600_000L;
    assertTrue(hours < 2, "an already-dated token was extended: " + expires);
  }

  @Test
  void doesNotReviveATokenThatHadAlreadyLapsed() {
    Timestamp expires = expiresOf("expired@example.com");
    assertNotNull(expires);
    assertTrue(expires.getTime() < System.currentTimeMillis(),
        "a lapsed token was pushed back into the future: " + expires);
  }

  @Test
  void leavesAnAccountWithNoTokenAlone() {
    assertNull(tokenOf("none@example.com"));
    assertNull(expiresOf("none@example.com"));
  }

  @Test
  void everyRemainingTokenNowCarriesAnExpiry() {
    // The point of the whole change: after this runs, no token anywhere is open-ended, which is
    // what lets the lookup stop accepting a missing expiry in the follow-up.
    assertEquals(0, countUndatedTokens(),
        "tokens remain with no expiry, so the lookup would still treat them as valid forever");
  }

  private static String tokenOf(String email) {
    return (String) selectOne("account_token", email);
  }

  private static Timestamp expiresOf(String email) {
    return (Timestamp) selectOne("account_token_expires", email);
  }

  private static Object selectOne(String column, String email) {
    // The column name is a literal from this test, never input.
    try (Connection connection = harness.connection();
        PreparedStatement statement =
            connection.prepareStatement("SELECT " + column + " FROM users WHERE email = ?")) {
      statement.setString(1, email);
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next(), "no users row for " + email);
        return rs.getObject(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not read users." + column + " for " + email, e);
    }
  }

  private static int countUndatedTokens() {
    try (Connection connection = harness.connection();
        PreparedStatement statement = connection.prepareStatement(
            "SELECT count(*) FROM users WHERE account_token IS NOT NULL"
                + " AND account_token_expires IS NULL")) {
      try (ResultSet rs = statement.executeQuery()) {
        assertTrue(rs.next());
        return rs.getInt(1);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("could not count undated tokens", e);
    }
  }
}
