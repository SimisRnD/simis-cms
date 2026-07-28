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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
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

import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies the email deliverability classification path (#574) against a real PostgreSQL
 * instance: {@link EmailRepository#markValidated} and {@link EmailRepository#findUnvalidatedEmails}.
 *
 * <p>
 * This is an integration test: it starts a throwaway PostgreSQL container (Testcontainers) and
 * exercises the repository through the real JDBC/HikariCP stack. It is skipped automatically when
 * Docker is not available, so it does not break the build on hosts without a Docker daemon.
 * </p>
 *
 * <p>
 * The test creates a focused subset of the real {@code emails} schema. It is not the full column
 * list from the install script: {@code tags} and {@code last_interaction} are omitted because
 * nothing under test reads or writes them. Every other column is kept, including the ones that
 * look unrelated to classification (name, geo, order history, etc.) -- {@link
 * EmailRepository#buildRecord} selects the full row and reads every one of those columns by name,
 * so a schema that were any narrower would turn each row into a swallowed SQLException and a null
 * record instead of a real assertion failure. The {@code created_by}/{@code modified_by} foreign
 * keys to {@code users} are dropped since there is no users table here, matching
 * ContentRepositoryTest's precedent.
 * </p>
 *
 * @author SimIS Inc.
 */
class EmailRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    // Only run when a Docker daemon is reachable; otherwise mark the whole class as skipped
    Assumptions.assumeTrue(isDockerAvailable(),
        "Docker is not available - skipping EmailRepository integration test");

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

    // Point the application's shared DataSource at the container
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
      // The DataSource is never initialized when Docker is unavailable
    }
    if (postgres != null) {
      postgres.stop();
    }
  }

  @BeforeEach
  void resetTable() {
    if (postgres == null || !postgres.isRunning()) {
      return;
    }
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE emails RESTART IDENTITY");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not reset emails table", se);
    }
  }

  @Test
  void markValidatedPersistsTheClassification() {
    Email email = addEmail("valid@example.com");

    EmailRepository.markValidated(email, "valid", "");

    Email reloaded = EmailRepository.findById(email.getId());
    assertNotNull(reloaded);
    assertEquals("valid", reloaded.getValidationStatus());
    assertEquals("", reloaded.getValidationSubStatus());
    assertNotNull(reloaded.getValidatedAt(), "validated_at should be stamped once classified");
  }

  @Test
  void markValidatedOverwritesAPreviousClassification() {
    Email email = addEmail("recheck@example.com");
    EmailRepository.markValidated(email, "invalid", "mailbox_not_found");

    EmailRepository.markValidated(email, "valid", null);

    Email reloaded = EmailRepository.findById(email.getId());
    assertEquals("valid", reloaded.getValidationStatus());
    assertNull(reloaded.getValidationSubStatus());
  }

  @Test
  void markValidatedIsSafeOnNullOrUnpersistedRecord() {
    // Neither call should throw, and neither should touch the table
    EmailRepository.markValidated(null, "valid", null);
    EmailRepository.markValidated(new Email(), "valid", null);

    assertEquals(0, DB.selectCountFrom("emails"), "no row should have been created or modified");
  }

  @Test
  void findUnvalidatedEmailsOnlyReturnsEmailsNeverClassified() {
    Email classified = addEmail("classified@example.com");
    EmailRepository.markValidated(classified, "valid", "");
    Email unvalidated = addEmail("pending@example.com");

    List<Email> results = EmailRepository.findUnvalidatedEmails(new DataConstraints(1, 100));

    assertNotNull(results);
    assertEquals(1, results.size(), "only the never-classified address should come back");
    assertEquals(unvalidated.getId(), results.get(0).getId());
    assertEquals("pending@example.com", results.get(0).getEmail());
    assertNull(results.get(0).getValidatedAt());
  }

  @Test
  void findUnvalidatedEmailsReturnsNullWhenNoneArePending() {
    Email onlyEmail = addEmail("already-checked@example.com");
    EmailRepository.markValidated(onlyEmail, "valid", "");

    assertNull(EmailRepository.findUnvalidatedEmails(new DataConstraints(1, 100)),
        "matches findAll()'s null-vs-empty-list convention, which EmailClassificationJob relies on");
  }

  private static boolean isDockerAvailable() {
    try {
      return DockerClientFactory.instance().isDockerAvailable();
    } catch (Throwable t) {
      return false;
    }
  }

  private static String resolveImage() {
    String image = System.getenv("TEST_POSTGRES_IMAGE");
    return (image != null && !image.isBlank()) ? image : DEFAULT_IMAGE;
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("DROP TABLE IF EXISTS emails CASCADE");
      statement.execute("CREATE TABLE emails ("
          + "email_id BIGSERIAL PRIMARY KEY, "
          + "email VARCHAR(255) UNIQUE NOT NULL, "
          + "first_name VARCHAR(100), "
          + "last_name VARCHAR(100), "
          + "organization VARCHAR(100), "
          + "source VARCHAR(50), "
          + "ip_address VARCHAR(200), "
          + "session_id VARCHAR(255), "
          + "user_agent VARCHAR(500), "
          + "referer VARCHAR(255), "
          + "continent VARCHAR(20), "
          + "country_iso VARCHAR(2), "
          + "country VARCHAR(100), "
          + "city VARCHAR(100), "
          + "state_iso VARCHAR(3), "
          + "state VARCHAR(100), "
          + "postal_code VARCHAR(50), "
          + "timezone VARCHAR(50), "
          + "latitude float, "
          + "longitude float, "
          + "metro_code INTEGER, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT, "
          + "subscribed TIMESTAMP(3), "
          + "unsubscribed TIMESTAMP(3), "
          + "unsubscribe_reason VARCHAR(100), "
          + "last_emailed TIMESTAMP(3), "
          + "last_order TIMESTAMP(3), "
          + "number_of_orders INTEGER DEFAULT 0, "
          + "total_spent NUMERIC(15,6) DEFAULT 0, "
          + "sync_date TIMESTAMP(3), "
          + "validation_status VARCHAR(20), "
          + "validation_sub_status VARCHAR(50), "
          + "validated_at TIMESTAMP(3))");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the emails schema", se);
    }
  }

  private static Email addEmail(String address) {
    Email email = new Email();
    email.setEmail(address);
    return EmailRepository.add(email);
  }
}
