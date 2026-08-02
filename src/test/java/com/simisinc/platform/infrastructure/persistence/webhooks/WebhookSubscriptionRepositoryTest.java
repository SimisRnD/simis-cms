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

package com.simisinc.platform.infrastructure.persistence.webhooks;

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

import com.simisinc.platform.application.SecretCryptoCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebhookSubscriptionRepository} CRUD and event-type matching (issue #418)
 * against a real PostgreSQL instance. Minimal schema replicated from
 * NEW_10130__new_webhooks.sql, without the users(user_id) FK on created_by/modified_by (not
 * needed here -- same simplification NewsletterSendQueueRepositoryTest makes for mailing_lists).
 *
 * <p>
 * A {@code cms.secret.key} system property is configured for the whole class (issue #453) so
 * {@link SecretCryptoCommand#encrypt} does not fail closed -- every test here writes a secret,
 * and {@code add()}/{@code update()} now always encrypt it. The pre-existing round-trip tests
 * below are otherwise unchanged: {@code findById(...).getSecret()} must keep returning plaintext
 * to every caller, encryption is purely an at-rest concern.
 * </p>
 */
class WebhookSubscriptionRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String SECRET_KEY_PROPERTY = "cms.secret.key";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook subscription integration test");

    byte[] key = new byte[32]; // AES-256, deterministic content doesn't matter for this test
    key[0] = 42;
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
  void clearTables() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook subscription integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE webhook_delivery, webhook_subscription RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void addThenFindByIdRoundTripsAllFields() {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl("https://example.com/hooks/simis");
    subscription.setEventTypeList(List.of("web-page-published", "blog-post-published"));
    subscription.setSecret("shh-its-a-secret");
    subscription.setEnabled(true);
    subscription.setCreatedBy(1L);
    subscription.setModifiedBy(1L);

    WebhookSubscription saved = WebhookSubscriptionRepository.add(subscription);
    assertNotNull(saved);
    assertTrue(saved.getId() > -1);

    WebhookSubscription found = WebhookSubscriptionRepository.findById(saved.getId());
    assertEquals("https://example.com/hooks/simis", found.getUrl());
    assertEquals("shh-its-a-secret", found.getSecret());
    assertTrue(found.getEnabled());
    assertEquals(List.of("web-page-published", "blog-post-published"), found.getEventTypeList());
  }

  @Test
  void updateChangesUrlAndEventTypesAndEnabled() {
    WebhookSubscription subscription = seed("https://example.com/a", "web-page-published", true);

    subscription.setUrl("https://example.com/b");
    subscription.setEventTypeList(List.of("order-submitted"));
    subscription.setEnabled(false);
    WebhookSubscriptionRepository.update(subscription);

    WebhookSubscription found = WebhookSubscriptionRepository.findById(subscription.getId());
    assertEquals("https://example.com/b", found.getUrl());
    assertEquals(List.of("order-submitted"), found.getEventTypeList());
    assertFalse(found.getEnabled());
  }

  @Test
  void removeDeletesTheRow() {
    WebhookSubscription subscription = seed("https://example.com/a", "web-page-published", true);
    assertTrue(WebhookSubscriptionRepository.remove(subscription));
    assertNull(WebhookSubscriptionRepository.findById(subscription.getId()));
  }

  @Test
  void findEnabledBySubscribedEventTypeMatchesOnlyEnabledSubscriptionsWithThatType() {
    seed("https://example.com/match", "web-page-published,blog-post-published", true);
    seed("https://example.com/no-match", "order-submitted", true);
    seed("https://example.com/disabled", "web-page-published", false);

    List<WebhookSubscription> matches = WebhookSubscriptionRepository.findEnabledBySubscribedEventType("web-page-published");

    assertEquals(1, matches.size());
    assertEquals("https://example.com/match", matches.get(0).getUrl());
  }

  @Test
  void findEnabledBySubscribedEventTypeDoesNotSubstringMatchADifferentEventType() {
    // "web-page-published" must not match a subscription only listing
    // "web-page-published-old" or similar -- exact CSV-entry match only.
    seed("https://example.com/similar", "web-page-published-old", true);

    List<WebhookSubscription> matches = WebhookSubscriptionRepository.findEnabledBySubscribedEventType("web-page-published");

    assertTrue(matches.isEmpty());
  }

  @Test
  void theSecretIsStoredEncryptedAtRestNotAsPlaintext() throws SQLException {
    WebhookSubscription subscription = seed("https://example.com/a", "web-page-published", true);
    subscription.setSecret("shh-its-a-secret");
    // Overwrite with a known plaintext via update() too, so both write paths are covered here.
    WebhookSubscriptionRepository.update(subscription);

    String rawColumnValue = readRawSecretColumn(subscription.getId());

    assertNotEquals("shh-its-a-secret", rawColumnValue, "the secret column must never hold plaintext");
    assertTrue(SecretCryptoCommand.isEncrypted(rawColumnValue), "expected an enc:-prefixed ciphertext");
    // The read path must still transparently decrypt back to the original plaintext for every caller.
    assertEquals("shh-its-a-secret", WebhookSubscriptionRepository.findById(subscription.getId()).getSecret());
  }

  @Test
  void addAlsoEncryptsTheSecretAtRest() throws SQLException {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl("https://example.com/new");
    subscription.setEventTypeList(List.of("web-page-published"));
    subscription.setSecret("brand-new-secret");
    subscription.setEnabled(true);
    subscription.setCreatedBy(1L);
    subscription.setModifiedBy(1L);
    WebhookSubscription saved = WebhookSubscriptionRepository.add(subscription);

    String rawColumnValue = readRawSecretColumn(saved.getId());

    assertNotEquals("brand-new-secret", rawColumnValue);
    assertTrue(SecretCryptoCommand.isEncrypted(rawColumnValue));
    assertEquals("brand-new-secret", WebhookSubscriptionRepository.findById(saved.getId()).getSecret());
  }

  private String readRawSecretColumn(long webhookSubscriptionId) throws SQLException {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement();
        java.sql.ResultSet rs = statement
            .executeQuery("SELECT secret FROM webhook_subscription WHERE webhook_subscription_id = " + webhookSubscriptionId)) {
      assertTrue(rs.next(), "expected a row for id " + webhookSubscriptionId);
      return rs.getString("secret");
    }
  }

  private WebhookSubscription seed(String url, String eventTypesCsv, boolean enabled) {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl(url);
    subscription.setEventTypes(eventTypesCsv);
    subscription.setSecret("secret");
    subscription.setEnabled(enabled);
    subscription.setCreatedBy(1L);
    subscription.setModifiedBy(1L);
    return WebhookSubscriptionRepository.add(subscription);
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE webhook_subscription ("
          + "webhook_subscription_id BIGSERIAL PRIMARY KEY, "
          + "url VARCHAR(2000) NOT NULL, "
          + "event_types VARCHAR(2000) NOT NULL, "
          + "secret VARCHAR(255) NOT NULL, "
          + "enabled BOOLEAN DEFAULT true, "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "created_by BIGINT, "
          + "modified TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP, "
          + "modified_by BIGINT)");
      statement.execute("CREATE TABLE webhook_delivery ("
          + "webhook_delivery_id BIGSERIAL PRIMARY KEY, "
          + "webhook_subscription_id BIGINT NOT NULL REFERENCES webhook_subscription(webhook_subscription_id), "
          + "event_type VARCHAR(200) NOT NULL, "
          + "delivery_uuid VARCHAR(36) NOT NULL UNIQUE, "
          + "payload TEXT NOT NULL, "
          + "attempt_count INTEGER DEFAULT 0, "
          + "status VARCHAR(20) DEFAULT 'pending', "
          + "last_attempted_at TIMESTAMP(3), "
          + "next_retry_at TIMESTAMP(3), "
          + "response_code INTEGER, "
          + "response_snippet VARCHAR(1000), "
          + "created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP)");
    } catch (SQLException se) {
      throw new IllegalStateException("Could not create the webhook test schema", se);
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
