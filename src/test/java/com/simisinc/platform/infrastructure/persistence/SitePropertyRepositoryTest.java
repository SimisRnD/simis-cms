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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Base64;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the issue #454 additions to {@code site_properties} -- who/when a value was last
 * changed, and an optional expiry -- against a real PostgreSQL instance. Minimal schema replicated
 * from {@code NEW_10000__new_database.sql}'s {@code site_properties} table.
 *
 * @author SimIS Inc.
 * @created 8/3/2026
 */
class SitePropertyRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String SECRET_KEY_PROP = "cms.secret.key";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping site property integration test");

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
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping site property integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE site_properties RESTART IDENTITY CASCADE");
    }
  }

  @AfterEach
  void clearSecretKey() {
    System.clearProperty(SECRET_KEY_PROP);
  }

  @Test
  void saveStampsModifiedAndModifiedByWhenAnActorIsGiven() throws SQLException {
    long propertyId = seedProperty("site.timezone", "Timezone", "UTC");
    SiteProperty record = SitePropertyRepository.findByName("site.timezone");
    record.setValue("America/New_York");

    SitePropertyRepository.save(record, 42L);

    SiteProperty updated = SitePropertyRepository.findByName("site.timezone");
    assertEquals("America/New_York", updated.getValue());
    assertEquals(42L, updated.getModifiedBy());
    assertNotNull(updated.getModified());
  }

  @Test
  void saveWithoutAnActorLeavesModifiedByUnset() throws SQLException {
    seedProperty("site.timezone", "Timezone", "UTC");
    SiteProperty record = SitePropertyRepository.findByName("site.timezone");
    record.setValue("America/New_York");

    SitePropertyRepository.save(record);

    SiteProperty updated = SitePropertyRepository.findByName("site.timezone");
    assertEquals(-1L, updated.getModifiedBy(), "a system/unattended save (e.g. a Flyway migration) has no actor");
    assertNotNull(updated.getModified(), "modified is still stamped regardless of actor");
  }

  @Test
  void saveDoesNotStampModifiedOrModifiedByWhenTheValueDidNotActuallyChange() throws SQLException {
    // issue #454 review: a settings page save re-submits every property on it, including ones the
    // admin never touched (e.g. a masked-blank secret field left as-is) -- modified/modified_by
    // must reflect a real change, not just "this row was present on a saved page"
    seedProperty("site.timezone", "Timezone", "UTC");
    SiteProperty record = SitePropertyRepository.findByName("site.timezone");
    // Value is left exactly as loaded -- nothing changed

    SitePropertyRepository.save(record, 42L, false);

    SiteProperty updated = SitePropertyRepository.findByName("site.timezone");
    assertEquals("UTC", updated.getValue());
    assertEquals(-1L, updated.getModifiedBy(), "unchanged value must not attribute a rotation to this actor");
    assertNull(updated.getModified(), "unchanged value must not bump the modified timestamp");
  }

  @Test
  void saveStampsModifiedAndModifiedByWhenValueChangedIsTrueEvenIfCallerPassedTheSameText() throws SQLException {
    // valueChanged is caller-asserted, not independently verified by the repository -- this test
    // documents that contract rather than re-deriving "did it change" itself
    seedProperty("site.timezone", "Timezone", "UTC");
    SiteProperty record = SitePropertyRepository.findByName("site.timezone");

    SitePropertyRepository.save(record, 42L, true);

    SiteProperty updated = SitePropertyRepository.findByName("site.timezone");
    assertEquals(42L, updated.getModifiedBy());
    assertNotNull(updated.getModified());
  }

  @Test
  void saveStoresAnExpiryDate() throws SQLException {
    seedProperty("oauth.clientSecret", "OAuth Client Secret", "");
    SiteProperty record = SitePropertyRepository.findByName("oauth.clientSecret");
    Timestamp expiresAt = Timestamp.valueOf("2026-12-31 00:00:00");
    record.setExpiresAt(expiresAt);

    SitePropertyRepository.save(record, -1);

    assertEquals(expiresAt, SitePropertyRepository.findByName("oauth.clientSecret").getExpiresAt());
  }

  @Test
  void saveClearsAPreviouslySetExpiryDate() throws SQLException {
    seedProperty("oauth.clientSecret", "OAuth Client Secret", "");
    SiteProperty record = SitePropertyRepository.findByName("oauth.clientSecret");
    record.setExpiresAt(Timestamp.valueOf("2026-12-31 00:00:00"));
    SitePropertyRepository.save(record, -1);
    assertNotNull(SitePropertyRepository.findByName("oauth.clientSecret").getExpiresAt());

    SiteProperty toClear = SitePropertyRepository.findByName("oauth.clientSecret");
    toClear.setExpiresAt(null);
    SitePropertyRepository.save(toClear, -1);

    assertNull(SitePropertyRepository.findByName("oauth.clientSecret").getExpiresAt());
  }

  @Test
  void saveStillEncryptsASecretValueAlongsideTheNewMetadataColumns() throws SQLException {
    byte[] key = new byte[32];
    System.setProperty(SECRET_KEY_PROP, Base64.getEncoder().encodeToString(key));

    seedProperty("mail.password", "SMTP Password", "");
    SiteProperty record = SitePropertyRepository.findByName("mail.password");
    record.setValue("super-secret-password");
    record.setExpiresAt(Timestamp.valueOf("2027-01-01 00:00:00"));

    SitePropertyRepository.save(record, 7L);

    // Confirm the stored value is actually ciphertext, not plaintext, at the raw column level
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement.executeQuery(
            "SELECT property_value FROM site_properties WHERE property_name = 'mail.password'")) {
      assertTrue(rs.next());
      assertTrue(rs.getString("property_value").startsWith("enc:"), "must be encrypted at rest");
    }

    SiteProperty updated = SitePropertyRepository.findByName("mail.password");
    assertEquals("super-secret-password", updated.getValue(), "decrypted on read");
    assertEquals(7L, updated.getModifiedBy());
    assertEquals(Timestamp.valueOf("2027-01-01 00:00:00"), updated.getExpiresAt());
  }

  private long seedProperty(String name, String label, String value) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('"
          + label + "', '" + name + "', '" + value + "')");
      try (java.sql.ResultSet rs = statement.executeQuery(
          "SELECT property_id FROM site_properties WHERE property_name = '" + name + "'")) {
        rs.next();
        return rs.getLong(1);
      }
    }
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE site_properties ("
          + "property_id SERIAL PRIMARY KEY, "
          + "property_order INTEGER DEFAULT 100, "
          + "property_label VARCHAR(50), "
          + "property_name VARCHAR(50) UNIQUE NOT NULL, "
          + "property_value TEXT NOT NULL, "
          + "property_type VARCHAR(100), "
          + "modified TIMESTAMP, "
          + "modified_by BIGINT, "
          + "expires_at TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the site properties test schema", se);
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
