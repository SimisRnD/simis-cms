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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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

import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;

/**
 * Verifies {@link WebhookDeliveryRepository} CRUD and attempt-recording (issue #418) against a
 * real PostgreSQL instance.
 */
class WebhookDeliveryRepositoryTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String SECRET_KEY_PROPERTY = "cms.secret.key";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook delivery integration test");

    // seedSubscription() below writes a webhook_subscription row (issue #453's
    // WebhookSubscriptionRepository now encrypts secret on write) -- configure a key so that
    // encryption doesn't fail closed; this test only cares about webhook_delivery behavior.
    byte[] key = new byte[32];
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
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook delivery integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE webhook_delivery, webhook_subscription RESTART IDENTITY CASCADE");
    }
  }

  @Test
  void addThenFindByIdRoundTripsAllFields() {
    long subscriptionId = seedSubscription();

    WebhookDelivery delivery = new WebhookDelivery();
    delivery.setWebhookSubscriptionId(subscriptionId);
    delivery.setEventType("web-page-published");
    delivery.setDeliveryUuid("11111111-1111-1111-1111-111111111111");
    delivery.setPayload("{\"event\":\"web-page-published\"}");
    delivery.setStatus(WebhookDelivery.PENDING);

    WebhookDelivery saved = WebhookDeliveryRepository.add(delivery);
    assertNotNull(saved);
    assertTrue(saved.getId() > -1);

    WebhookDelivery found = WebhookDeliveryRepository.findById(saved.getId());
    assertEquals(subscriptionId, found.getWebhookSubscriptionId());
    assertEquals("web-page-published", found.getEventType());
    assertEquals("11111111-1111-1111-1111-111111111111", found.getDeliveryUuid());
    assertEquals("{\"event\":\"web-page-published\"}", found.getPayload());
    assertEquals(0, found.getAttemptCount());
    assertEquals(WebhookDelivery.PENDING, found.getStatus());
  }

  @Test
  void findByDeliveryUuidFindsTheRightRow() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "22222222-2222-2222-2222-222222222222");

    WebhookDelivery found = WebhookDeliveryRepository.findByDeliveryUuid("22222222-2222-2222-2222-222222222222");
    assertNotNull(found);
    assertEquals(delivery.getId(), found.getId());
  }

  @Test
  void findByDeliveryUuidReturnsNullForAnUnknownUuid() {
    assertNull(WebhookDeliveryRepository.findByDeliveryUuid("does-not-exist"));
  }

  @Test
  void findBySubscriptionIdReturnsOnlyThatSubscriptionsDeliveries() {
    long subscriptionA = seedSubscription();
    long subscriptionB = seedSubscription();
    seedDelivery(subscriptionA, "33333333-3333-3333-3333-333333333333");
    seedDelivery(subscriptionA, "44444444-4444-4444-4444-444444444444");
    seedDelivery(subscriptionB, "55555555-5555-5555-5555-555555555555");

    List<WebhookDelivery> deliveries = WebhookDeliveryRepository.findBySubscriptionId(subscriptionA);
    assertEquals(2, deliveries.size());
  }

  @Test
  void recordAttemptPersistsAttemptCountStatusAndResponse() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "66666666-6666-6666-6666-666666666666");

    delivery.setAttemptCount(1);
    delivery.setStatus(WebhookDelivery.FAILED);
    delivery.setLastAttemptedAt(new Timestamp(System.currentTimeMillis()));
    Timestamp nextRetry = new Timestamp(System.currentTimeMillis() + 5000);
    delivery.setNextRetryAt(nextRetry);
    delivery.setResponseCode(503);
    delivery.setResponseSnippet("Service Unavailable");

    assertTrue(WebhookDeliveryRepository.recordAttempt(delivery, 0));

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(1, found.getAttemptCount());
    assertEquals(WebhookDelivery.FAILED, found.getStatus());
    assertNotNull(found.getLastAttemptedAt());
    assertNotNull(found.getNextRetryAt());
    assertEquals(503, found.getResponseCode());
    assertEquals("Service Unavailable", found.getResponseSnippet());
  }

  @Test
  void recordAttemptCanTransitionToExhaustedWithNoFurtherRetry() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "77777777-7777-7777-7777-777777777777");

    delivery.setAttemptCount(5);
    delivery.setStatus(WebhookDelivery.EXHAUSTED);
    delivery.setNextRetryAt(null);
    WebhookDeliveryRepository.recordAttempt(delivery, 0);

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(WebhookDelivery.EXHAUSTED, found.getStatus());
    assertNull(found.getNextRetryAt());
  }

  @Test
  void recordAttemptFailsAndLeavesTheRowUntouchedWhenTheExpectedAttemptCountIsStale() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "88888888-8888-8888-8888-888888888888");

    delivery.setAttemptCount(1);
    delivery.setStatus(WebhookDelivery.FAILED);
    delivery.setNextRetryAt(new Timestamp(System.currentTimeMillis() + 5000));

    // The row's real attempt_count in the database is still 0 (freshly seeded, untouched).
    // Passing an expected value of 1 simulates a concurrent execution that read the row after
    // some other execution had already advanced it -- the optimistic-lock guard must reject
    // this write rather than silently overwrite whatever the other execution recorded.
    assertFalse(WebhookDeliveryRepository.recordAttempt(delivery, 1));

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(0, found.getAttemptCount(), "a rejected write must leave the row untouched");
    assertEquals(WebhookDelivery.PENDING, found.getStatus());
  }

  @Test
  void recordAttemptSucceedsAndPersistsWhenTheExpectedAttemptCountStillMatches() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "89898989-8989-8989-8989-898989898989");

    delivery.setAttemptCount(1);
    delivery.setStatus(WebhookDelivery.FAILED);
    delivery.setNextRetryAt(new Timestamp(System.currentTimeMillis() + 5000));

    assertTrue(WebhookDeliveryRepository.recordAttempt(delivery, 0));

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(1, found.getAttemptCount());
    assertEquals(WebhookDelivery.FAILED, found.getStatus());
  }

  @Test
  void recordAttemptClearsAPreviouslyPersistedResponseCodeWhenTheNewRecordHasNone() {
    long subscriptionId = seedSubscription();
    WebhookDelivery delivery = seedDelivery(subscriptionId, "99999999-9999-9999-9999-999999999999");

    delivery.setAttemptCount(1);
    delivery.setStatus(WebhookDelivery.FAILED);
    delivery.setResponseCode(500);
    delivery.setResponseSnippet("server error");
    assertTrue(WebhookDeliveryRepository.recordAttempt(delivery, 0));
    assertEquals(500, WebhookDeliveryRepository.findById(delivery.getId()).getResponseCode());

    // A second attempt that never actually got a response (e.g. SSRF-blocked or a timeout) must
    // clear response_code, not leave the first attempt's real HTTP status looking current.
    delivery.setAttemptCount(2);
    delivery.setResponseCode(null);
    delivery.setResponseSnippet("blocked by the SSRF guard");
    assertTrue(WebhookDeliveryRepository.recordAttempt(delivery, 1));

    assertNull(WebhookDeliveryRepository.findById(delivery.getId()).getResponseCode());
  }

  private long seedSubscription() {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl("https://example.com/hooks");
    subscription.setEventTypes("web-page-published");
    subscription.setSecret("secret");
    subscription.setEnabled(true);
    return WebhookSubscriptionRepository.add(subscription).getId();
  }

  private WebhookDelivery seedDelivery(long subscriptionId, String deliveryUuid) {
    WebhookDelivery delivery = new WebhookDelivery();
    delivery.setWebhookSubscriptionId(subscriptionId);
    delivery.setEventType("web-page-published");
    delivery.setDeliveryUuid(deliveryUuid);
    delivery.setPayload("{}");
    delivery.setStatus(WebhookDelivery.PENDING);
    return WebhookDeliveryRepository.add(delivery);
  }

  private static void createSchema() {
    try (Connection connection = DB.getConnection();
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE webhook_subscription ("
          + "webhook_subscription_id BIGSERIAL PRIMARY KEY, "
          + "url TEXT NOT NULL, "
          + "event_types VARCHAR(2000) NOT NULL, "
          + "secret VARCHAR(255) NOT NULL, "
          + "enabled BOOLEAN DEFAULT true, "
          + "integration_id VARCHAR(100), "
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
