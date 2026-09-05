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

package com.simisinc.platform.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.Entity;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.persistence.login.UserRoleRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserGroupRepository;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlJoins;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;
import com.simisinc.platform.infrastructure.persistence.login.UnsuspendRequestRepository;
import com.simisinc.platform.infrastructure.persistence.login.UserTokenRepository;

/**
 * Covers UserRepository behavior that is easy to silently regress: the security-relevant cleanup
 * that runs when a user's password changes (alongside revoking user tokens, the cached plaintext
 * credentials must be invalidated, or the old password keeps authenticating via
 * AuthenticateLoginCommand's credentials cache), and the split between the list-serving row
 * mapper (must not decrypt the TOTP secret) and the single-record row mapper (must).
 *
 * @author Liz Houser
 * @created 7/23/2026
 */
class UserRepositoryTest {

  @Test
  void updatePasswordInvalidatesTheCachedCredentials() {
    User user = new User();
    user.setId(42L);
    user.setPassword("$argon2id$v=19$m=65536,t=3,p=1$newhash");

    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UserTokenRepository> tokens = mockStatic(UserTokenRepository.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      db.when(() -> DB.update(anyString(), any(SqlUtils.class), any(SqlUtils.class))).thenReturn(true);

      UserRepository.updatePassword(user);

      // The old cached "username:password" for this user must be dropped so it cannot authenticate
      cacheManager.verify(() -> CacheManager.invalidateKey(CacheManager.USER_CREDENTIALS_CACHE, 42L));
      // ... and the existing token revocation must still happen
      tokens.verify(() -> UserTokenRepository.removeAll(42L));
    }
  }

  @Test
  void updatePasswordDoesNotTouchTheCacheWhenTheWriteFails() {
    User user = new User();
    user.setId(7L);
    user.setPassword("$argon2id$hash");

    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UserTokenRepository> tokens = mockStatic(UserTokenRepository.class);
        MockedStatic<CacheManager> cacheManager = mockStatic(CacheManager.class)) {
      db.when(() -> DB.update(anyString(), any(SqlUtils.class), any(SqlUtils.class))).thenReturn(false);

      UserRepository.updatePassword(user);

      // No successful write -> no revocation and no cache eviction
      cacheManager.verify(() -> CacheManager.invalidateKey(anyString(), any()), never());
      tokens.verify(() -> UserTokenRepository.removeAll(anyLong()), never());
    }
  }

  @Test
  void suspendAccountPersistsTheReason() {
    User user = new User();
    user.setId(9L);

    ArgumentCaptor<SqlUtils> valuesCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UnsuspendRequestRepository> requestRepo = mockStatic(UnsuspendRequestRepository.class)) {
      db.when(() -> DB.update(anyString(), valuesCaptor.capture(), any(SqlUtils.class))).thenReturn(true);

      UserRepository.suspendAccount(user, "Reported phishing attempt from this account");

      // #492 Phase 3: suspendAccount() also supersedes any in-flight unsuspend request/decision
      // for this target -- verified separately; not the captured call this test cares about.
      requestRepo.verify(() -> UnsuspendRequestRepository.supersedePendingForTarget(9L,
          "Account was suspended again"));

      assertEquals("Reported phishing attempt from this account", fieldValue(valuesCaptor.getValue(), "suspension_reason"));
    }
  }

  @Test
  void restoreAccountClearsTheReason() {
    User user = new User();
    user.setId(9L);

    ArgumentCaptor<SqlUtils> valuesCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(() -> DB.update(anyString(), valuesCaptor.capture(), any(SqlUtils.class))).thenReturn(true);

      UserRepository.restoreAccount(user);

      assertEquals(null, fieldValue(valuesCaptor.getValue(), "suspension_reason"));
    }
  }

  /**
   * findAll() (used by the /admin/users list, the editorial-calendar author dropdown, and the
   * user lookup autocomplete) must map rows with the lighter-weight summary mapper: it should
   * never touch SecretCryptoCommand.decrypt(), and the resulting User's mfaSecret must stay null,
   * even though the underlying row has an encrypted secret present.
   */
  @Test
  @SuppressWarnings("unchecked")
  void findAllDoesNotDecryptTheMfaSecret() throws SQLException {
    ArgumentCaptor<Function<ResultSet, Entity>> mapperCaptor = ArgumentCaptor.forClass(Function.class);
    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<SecretCryptoCommand> crypto = mockStatic(SecretCryptoCommand.class)) {
      db.when(() -> DB.selectAllFrom(anyString(), any(SqlUtils.class), any(SqlUtils.class), any(SqlUtils.class),
          any(DataConstraints.class), mapperCaptor.capture())).thenReturn(new DataResult());

      UserRepository.findAll(new UserSpecification(), new DataConstraints());

      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("mfa_secret")).thenReturn("enc:should-not-be-read");
      User mapped = (User) mapperCaptor.getValue().apply(rs);

      assertNull(mapped.getMfaSecret(), "the list-serving row mapper must not populate mfaSecret");
      crypto.verify(() -> SecretCryptoCommand.decrypt(anyString()), never());
    }
  }

  /**
   * A single-record lookup (e.g. findByUserId, used by login/MFA-verification flows) must keep
   * decrypting the TOTP seed via the full row mapper -- this is the counterpart to
   * findAllDoesNotDecryptTheMfaSecret(), confirming the split didn't regress the path that
   * actually needs the plaintext secret.
   */
  @Test
  @SuppressWarnings("unchecked")
  void findByUserIdStillDecryptsTheMfaSecret() throws SQLException {
    ArgumentCaptor<Function<ResultSet, Entity>> mapperCaptor = ArgumentCaptor.forClass(Function.class);
    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<SecretCryptoCommand> crypto = mockStatic(SecretCryptoCommand.class)) {
      db.when(() -> DB.selectRecordFrom(anyString(), any(SqlUtils.class), mapperCaptor.capture()))
          .thenReturn(null);
      crypto.when(() -> SecretCryptoCommand.decrypt("enc:real-secret")).thenReturn("JBSWY3DPEHPK3PXP");

      UserRepository.findByUserId(42L);

      ResultSet rs = mock(ResultSet.class);
      when(rs.getString("mfa_secret")).thenReturn("enc:real-secret");
      User mapped = (User) mapperCaptor.getValue().apply(rs);

      assertEquals("JBSWY3DPEHPK3PXP", mapped.getMfaSecret());
    }
  }

  @Test
  void resolvePasswordMaxAgeDaysFallsBackOnBlankOrUnparseableOrNonPositive() {
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays(null), "default on null");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays(""), "default on blank");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("not-a-number"), "default on unparseable");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("0"), "default on non-positive");
    assertEquals(90, UserRepository.resolvePasswordMaxAgeDays("-5"), "default on negative");
    assertEquals(180, UserRepository.resolvePasswordMaxAgeDays("180"), "valid value passes through");
  }

  /**
   * The export must use an explicit column allowlist -- never SELECT * or the full-record buildRecord
   * mapper -- so the password hash, MFA TOTP secret, and account-token/reset-token columns can never end
   * up in the downloaded file, no matter what columns are added to the users table in the future.
   */
  @Test
  void exportCsvSelectsExactlyTheIntendedColumnAllowlist() {
    ArgumentCaptor<SqlUtils> selectFieldsCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      // exportCsv passes null for joins and orderBy (same shape as AuditLogRepository.exportCsv), so those
      // two positions need a null-tolerant matcher -- any(Class) excludes null and would never match.
      db.when(() -> DB.exportToCsvAllFrom(
          anyString(), selectFieldsCaptor.capture(), nullable(SqlJoins.class), any(SqlUtils.class),
          nullable(SqlUtils.class), any(DataConstraints.class), any(File.class)))
          .thenAnswer(invocation -> null);

      UserSpecification specification = new UserSpecification();
      UserRepository.exportCsv(specification, new File("export.csv"));

      List<SqlValue> selected = selectFieldsCaptor.getValue().getValues();
      Set<String> selectedClauses = new LinkedHashSet<>();
      for (SqlValue value : selected) {
        selectedClauses.add(value.getFieldOrClause());
      }

      Set<String> expected = Set.of(
          "first_name AS \"First Name\"",
          "last_name AS \"Last Name\"",
          "email AS \"Email\"",
          "username AS \"Username\"",
          "(SELECT string_agg(lr.title, ', ' ORDER BY lr.level DESC) FROM lookup_role lr "
              + "JOIN user_roles ur ON ur.role_id = lr.role_id WHERE ur.user_id = users.user_id) AS \"Roles\"",
          "enabled AS \"Enabled\"",
          "validated AS \"Validated\"",
          "created AS \"Created\"",
          "(SELECT MAX(created) FROM user_logins WHERE user_id = users.user_id) AS \"Last Login\"");
      assertEquals(expected, selectedClauses, "the export column list must match the allowlist exactly");

      // Belt-and-suspenders: none of the secret columns may appear in any selected clause, by substring.
      for (String clause : selectedClauses) {
        String lower = clause.toLowerCase();
        assertFalse(lower.contains("password"), "password must never be exported: " + clause);
        assertFalse(lower.contains("mfa_secret"), "mfa_secret must never be exported: " + clause);
        assertFalse(lower.contains("account_token"), "account_token must never be exported: " + clause);
      }
    }
  }

  private static String fieldValue(SqlUtils sqlUtils, String fieldName) {
    for (com.simisinc.platform.infrastructure.database.SqlValue value : sqlUtils.getValues()) {
      if (fieldName.equals(value.getFieldOrClause())) {
        return value.getStringValue();
      }
    }
    throw new AssertionError("No field named " + fieldName + " was set");
  }

  @Test
  void anUndatedTokenIsNoLongerAcceptedAsValid() {
    // The predicate used to read "account_token_expires IS NULL OR account_token_expires > NOW()".
    // That IS NULL arm is what made every pre-migration token valid forever, which is the defect the
    // expiry work set out to close. Asserted on the generated clause rather than a round trip
    // because the arm's absence IS the security property: a token with no expiry is a credential
    // with no lifetime, and accepting one is the more dangerous of the two possible mistakes.
    ArgumentCaptor<SqlUtils> where = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(() -> DB.selectRecordFrom(anyString(), where.capture(), any())).thenReturn(null);

      UserRepository.findByAccountToken("some-token");

      String clause = clauseOf(where.getValue());
      assertTrue(clause.contains("account_token_expires > NOW()"),
          "a valid token must be required to carry a future expiry: " + clause);
      assertFalse(clause.contains("IS NULL"),
          "an undated token must not be treated as valid: " + clause);
    }
  }

  @Test
  void anUndatedTokenReadsAsExpiredRatherThanMissing() {
    // The two lookups have to stay exhaustive. If "valid" requires a future expiry while "expired"
    // requires a non-null one, a row with no expiry matches neither -- and the admin screen shows
    // nothing at all for an account that plainly has a token, which is the state #1838 exists to
    // display.
    ArgumentCaptor<SqlUtils> where = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(() -> DB.selectRecordFrom(anyString(), where.capture(), any())).thenReturn(null);

      UserRepository.findExpiredByAccountToken("some-token");

      String clause = clauseOf(where.getValue());
      assertTrue(clause.contains("IS NULL"),
          "a token with no expiry has to report as expired, not vanish: " + clause);
    }
  }

  private static String clauseOf(SqlUtils sqlUtils) {
    StringBuilder sb = new StringBuilder();
    for (com.simisinc.platform.infrastructure.database.SqlValue v : sqlUtils.getValues()) {
      sb.append(' ').append(v.getFieldOrClause());
    }
    return sb.toString();
  }

  @Test
  void addMintsAnActivationTokenWithAnExpiry() throws Exception {
    // add() used to write account_token with no account_token_expires. The column has no DEFAULT,
    // and findByAccountToken treats a missing expiry as valid indefinitely, so every activation
    // link issued at sign-up or by admin account creation never stopped working. Both the value on
    // the record and the column in the INSERT are asserted -- setting one without the other leaves
    // the row undated just the same.
    User user = new User();
    user.setEmail("invited@example.com");
    user.setPassword("new");

    Connection connection = mock(Connection.class);
    ArgumentCaptor<SqlUtils> valuesCaptor = ArgumentCaptor.forClass(SqlUtils.class);
    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<UserGroupRepository> groups = mockStatic(UserGroupRepository.class);
        MockedStatic<UserRoleRepository> roles = mockStatic(UserRoleRepository.class)) {
      db.when(DB::getConnection).thenReturn(connection);
      db.when(() -> DB.insertInto(any(Connection.class), anyString(), valuesCaptor.capture(), any(String[].class)))
          .thenReturn(7L);

      User saved = UserRepository.add(user);

      assertNotNull(saved, "add() did not complete");
      assertNotNull(saved.getAccountToken(), "no activation token was minted");

      Timestamp expires = saved.getAccountTokenExpires();
      assertNotNull(expires, "the activation token was left with no expiry");
      long days = (expires.getTime() - System.currentTimeMillis()) / 86_400_000L;
      assertTrue(days >= 6 && days <= 7,
          "expected an activation window of about 7 days, got " + days + " days");

      // The column has to reach the INSERT, not just the in-memory record. fieldValue throws when
      // a column was never set, which is precisely the regression this guards.
      assertDoesNotThrow(() -> fieldValue(valuesCaptor.getValue(), "account_token"),
          "account_token was not written");
      assertDoesNotThrow(() -> fieldValue(valuesCaptor.getValue(), "account_token_expires"),
          "account_token_expires was not written alongside the token, so the row is left undated");
    }
  }
}
