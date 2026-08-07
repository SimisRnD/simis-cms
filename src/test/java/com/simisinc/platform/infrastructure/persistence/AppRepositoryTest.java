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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link AppRepository} against a real PostgreSQL instance (schema replicated from
 * NEW_10000__new_database.sql's {@code apps} table), following the same Testcontainers pattern as
 * {@code WebhookSubscriptionRepositoryTest}. Covers:
 * <ul>
 * <li>the enabled column actually round-tripping through both add() and update() -- the previous
 * update() SQL only wrote name/summary, so toggling "Enabled" from the admin form silently did
 * nothing;</li>
 * <li>private_key encrypted at rest via {@link SecretCryptoCommand}, the same encrypt-on-write/
 * decrypt-on-read pattern {@code WebhookSubscriptionRepositoryTest} verifies for its own secret
 * column, PLUS the fallback this repository adds on top of that pattern: unlike
 * WebhookSubscriptionRepository, {@code encrypt()} is not called unguarded here, because App
 * creation/update is core admin functionality (not opt-in like MFA or webhooks) for a field
 * confirmed unused anywhere else in this codebase -- see {@code noKeyConfigured()} below for that
 * specific behavior; and</li>
 * <li>remove() actually deleting the row (no delete method existed before this change).</li>
 * </ul>
 *
 * @author elizabeth houser
 */
class AppRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String SECRET_KEY_PROPERTY = "cms.secret.key";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping AppRepository integration test");

    byte[] key = new byte[32]; // AES-256, deterministic content doesn't matter for this test
    key[0] = 7;
    System.setProperty(SECRET_KEY_PROPERTY, Base64.getEncoder().encodeToString(key));

    postgres = new GenericContainer<>(DockerImageName.parse(resolveImage()))
        .withEnv("POSTGRES_USER", DB_USER)
        .withEnv("POSTGRES_PASSWORD", DB_PASSWORD)
        .withEnv("POSTGRES_DB", DB_NAME)
        .withExposedPorts(POSTGRES_PORT)
        .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
            .withStartupTimeout(Duration.ofSeconds(120)));
    try {
      postgres.start();
    } catch (Throwable t) {
      Assumptions.abort("Unable to start PostgreSQL test container: " + t.getMessage());
    }

    String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(POSTGRES_PORT)
        + "/" + DB_NAME;
    Properties properties = new Properties();
    properties.setProperty("jdbcUrl", jdbcUrl);
    properties.setProperty("username", DB_USER);
    properties.setProperty("password", DB_PASSWORD);
    DataSource.init(properties);

    createSchema();
  }

  @AfterAll
  static void stopDatabase() {
    System.clearProperty(SECRET_KEY_PROPERTY);
    try {
      DataSource.shutdown();
    } catch (Exception e) {
      // Never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void clearTable() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping AppRepository integration test");
    // A key may have been cleared by noKeyConfigured() in a previous test -- restore it up front so
    // every other test in this class runs with encryption enabled, matching @BeforeAll's setup.
    if (System.getProperty(SECRET_KEY_PROPERTY) == null) {
      byte[] key = new byte[32];
      key[0] = 7;
      System.setProperty(SECRET_KEY_PROPERTY, Base64.getEncoder().encodeToString(key));
    }
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE apps RESTART IDENTITY");
    }
  }

  @Test
  void addThenFindByIdRoundTripsAllFields() {
    App app = new App();
    app.setName("Mobile App");
    app.setSummary("Used by the mobile client");
    app.setPublicKey("public-key-value");
    app.setPrivateKey("private-key-value");
    app.setCreatedBy(1L);
    app.setEnabled(true);

    App saved = AppRepository.save(app);
    assertNotNull(saved);
    assertTrue(saved.getId() > -1);

    App found = AppRepository.findById(saved.getId());
    assertEquals("Mobile App", found.getName());
    assertEquals("Used by the mobile client", found.getSummary());
    assertEquals("public-key-value", found.getPublicKey());
    assertEquals("private-key-value", found.getPrivateKey());
    assertTrue(found.isEnabled());
    assertEquals(1L, found.getCreatedBy());
  }

  @Test
  void updateActuallyPersistsTheEnabledFlag() {
    // Regression coverage for the fix: update()'s SQL previously only wrote name/summary, so
    // toggling "Enabled" from the admin form silently did nothing at all.
    App app = seed("App To Disable", true);

    app.setEnabled(false);
    App updated = AppRepository.save(app);
    assertNotNull(updated);

    assertFalse(AppRepository.findById(app.getId()).isEnabled());
  }

  @Test
  void updateReEnablingAlsoPersists() {
    App app = seed("App To Re-enable", false);

    app.setEnabled(true);
    AppRepository.save(app);

    assertTrue(AppRepository.findById(app.getId()).isEnabled());
  }

  @Test
  void updateChangesNameAndSummary() {
    App app = seed("Original Name", true);

    app.setName("Renamed");
    app.setSummary("New summary");
    AppRepository.save(app);

    App found = AppRepository.findById(app.getId());
    assertEquals("Renamed", found.getName());
    assertEquals("New summary", found.getSummary());
  }

  @Test
  void removeDeletesTheRow() {
    App app = seed("Deletable App", true);

    assertTrue(AppRepository.remove(app));
    assertNull(AppRepository.findById(app.getId()));
  }

  @Test
  void removeOfANonexistentRowReturnsFalse() {
    App phantom = new App();
    phantom.setId(999999L);
    phantom.setPublicKey("does-not-exist");

    assertFalse(AppRepository.remove(phantom));
  }

  @Test
  void thePrivateKeyIsStoredEncryptedAtRestNotAsPlaintext() throws SQLException {
    App app = seed("Encrypted App", true);

    String rawColumnValue = readRawPrivateKeyColumn(app.getId());

    assertNotEquals(app.getPrivateKey(), rawColumnValue, "the private_key column must never hold plaintext");
    assertTrue(SecretCryptoCommand.isEncrypted(rawColumnValue), "expected an enc:-prefixed ciphertext");
    // The read path must still transparently decrypt back to the original plaintext for every caller,
    // even though privateKey is confirmed unused elsewhere in this codebase today.
    assertEquals(app.getPrivateKey(), AppRepository.findById(app.getId()).getPrivateKey());
  }

  @Test
  void updateAlsoReEncryptsThePrivateKeyAtRest() throws SQLException {
    App app = seed("Encrypted App", true);

    // Simulate an edit round trip: load, then save again (privateKey passes through unchanged --
    // SaveAppCommand never regenerates it on update).
    App loaded = AppRepository.findById(app.getId());
    loaded.setSummary("Touched on edit");
    AppRepository.save(loaded);

    String rawColumnValue = readRawPrivateKeyColumn(app.getId());
    assertTrue(SecretCryptoCommand.isEncrypted(rawColumnValue));
    assertEquals(app.getPrivateKey(), AppRepository.findById(app.getId()).getPrivateKey());
  }

  @Test
  void noKeyConfiguredFallsBackToPlaintextInsteadOfBreakingAppCreation() throws SQLException {
    // App creation/update is core admin functionality, unlike the opt-in MFA/webhook features that
    // call SecretCryptoCommand.encrypt() unguarded -- since issue #16 made encrypt() fail closed
    // (throw) without a configured key, AppRepository must not let that turn into "you can no
    // longer create an App at all" for a field (privateKey) confirmed unused elsewhere. Clearing the
    // property here (no CMS_SECRET_KEY env var is set in this test process either) reproduces the
    // "nothing configured" case.
    System.clearProperty(SECRET_KEY_PROPERTY);
    try {
      App app = new App();
      app.setName("No Key Configured");
      app.setPublicKey("public-key-no-key");
      app.setPrivateKey("plain-private-key");
      app.setCreatedBy(1L);
      app.setEnabled(true);

      App saved = AppRepository.save(app);
      assertNotNull(saved, "App creation must not fail just because CMS_SECRET_KEY isn't configured");

      String rawColumnValue = readRawPrivateKeyColumn(saved.getId());
      assertEquals("plain-private-key", rawColumnValue, "falls back to storing as-is, matching pre-encryption behavior");
      assertEquals("plain-private-key", AppRepository.findById(saved.getId()).getPrivateKey());
    } finally {
      // Restored by the next test's @BeforeEach regardless, but be tidy within this test too.
      byte[] key = new byte[32];
      key[0] = 7;
      System.setProperty(SECRET_KEY_PROPERTY, Base64.getEncoder().encodeToString(key));
    }
  }

  private String readRawPrivateKeyColumn(long appId) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement.executeQuery("SELECT private_key FROM apps WHERE app_id = " + appId)) {
      assertTrue(rs.next(), "expected a row for id " + appId);
      return rs.getString("private_key");
    }
  }

  private App seed(String name, boolean enabled) {
    App app = new App();
    app.setName(name);
    app.setPublicKey("public-key-" + name);
    app.setPrivateKey("private-key-" + name);
    app.setCreatedBy(1L);
    app.setEnabled(enabled);
    return AppRepository.save(app);
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS apps CASCADE");
      statement.execute("CREATE TABLE apps ("
          + "app_id BIGSERIAL PRIMARY KEY, "
          + "name VARCHAR(255) NOT NULL, "
          + "summary TEXT, "
          + "created_by BIGINT, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "public_key VARCHAR(255) UNIQUE, "
          + "private_key VARCHAR(255), "
          + "enabled BOOLEAN DEFAULT true)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the apps test schema", se);
    }
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (RuntimeException | LinkageError e) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }
}
