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

package com.simisinc.platform.application.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Verifies {@link AttemptWebhookDeliveryCommand}'s retry/backoff schedule and idempotency
 * (issue #418 / #456): a failed attempt is rescheduled at roughly the right backoff interval
 * (10m, 50m, 3h, 20h -- cumulative offsets of 10m, 1h, 4h, 24h from the first attempt), and the
 * 5th failed attempt is marked {@code exhausted} rather than retried forever.
 * {@link AttemptWebhookDeliveryCommand#scheduleRetry} is swapped out so this never touches the
 * real JobRunr scheduler (which is not configured in a plain unit test).
 */
class AttemptWebhookDeliveryCommandTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";
  private static final String SECRET_KEY_PROPERTY = "cms.secret.key";

  private static GenericContainer<?> postgres;

  private BiConsumer<Instant, Long> originalScheduleRetry;
  private final List<Instant> scheduledRetryInstants = new ArrayList<>();

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook delivery attempt test");

    // seedSubscription() below writes a webhook_subscription row (issue #453's
    // WebhookSubscriptionRepository now encrypts secret on write) -- configure a key so that
    // encryption doesn't fail closed; this test only cares about the attempt/backoff behavior.
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
  void setUp() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping webhook delivery attempt test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE webhook_delivery, webhook_subscription RESTART IDENTITY CASCADE");
    }
    originalScheduleRetry = AttemptWebhookDeliveryCommand.scheduleRetry;
    scheduledRetryInstants.clear();
    AttemptWebhookDeliveryCommand.scheduleRetry = (instant, deliveryId) -> scheduledRetryInstants.add(instant);
  }

  @AfterEach
  void tearDown() {
    AttemptWebhookDeliveryCommand.scheduleRetry = originalScheduleRetry;
  }

  @Test
  void aSuccessfulAttemptMarksTheDeliveryDeliveredAndNeverSchedulesARetry() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      AttemptWebhookDeliveryCommand.attempt(delivery.getId());
    }

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(WebhookDelivery.DELIVERED, found.getStatus());
    assertEquals(1, found.getAttemptCount());
    assertNull(found.getNextRetryAt());
    assertEquals(200, found.getResponseCode());
    assertTrue(scheduledRetryInstants.isEmpty(), "a successful delivery must never schedule a retry");
  }

  @Test
  void theHmacSignatureHeaderIsSetCorrectlyOnTheGuardedPostCall() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(eq("https://example.com/hooks"),
          headersCaptor.capture(), eq(delivery.getPayload()), eq(HttpPostCommand.POST)))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      AttemptWebhookDeliveryCommand.attempt(delivery.getId());

      // Prove the SSRF-guarded path is what's actually used, not the unguarded execute() family.
      httpPostCommand.verify(
          () -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()), times(1));
      httpPostCommand.verify(() -> HttpPostCommand.execute(anyString(), any(Map.class), anyString(), anyInt()), never());
    }

    String expectedSignature = SignWebhookPayloadCommand.signatureHeaderValue(delivery.getPayload(), "secret-abc");
    assertEquals(expectedSignature, headersCaptor.getValue().get(SignWebhookPayloadCommand.HEADER_NAME));
  }

  @Test
  void aFailedAttemptIsRescheduledAtRoughlyTheFirstBackoffInterval() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    attemptWithStatus(delivery.getId(), 500, "server error");

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(WebhookDelivery.FAILED, found.getStatus());
    assertEquals(1, found.getAttemptCount());
    assertEquals(1, scheduledRetryInstants.size());
    assertWithinTolerance(scheduledRetryInstants.get(0), Duration.ofMinutes(10));
  }

  @Test
  void backoffGrowsAcrossAttemptsAndTheFifthFailureIsExhaustedNotRetried() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    // Attempt 1 -> failed, retry in ~10m
    attemptWithStatus(delivery.getId(), 500, "err");
    assertWithinTolerance(scheduledRetryInstants.get(0), Duration.ofMinutes(10));

    // Attempt 2 -> failed, retry in ~50m
    attemptWithStatus(delivery.getId(), 500, "err");
    assertWithinTolerance(scheduledRetryInstants.get(1), Duration.ofMinutes(50));

    // Attempt 3 -> failed, retry in ~3h
    attemptWithStatus(delivery.getId(), 500, "err");
    assertWithinTolerance(scheduledRetryInstants.get(2), Duration.ofHours(3));

    // Attempt 4 -> failed, retry in ~20h -- the final scheduled retry lands ~24h after attempt 1
    attemptWithStatus(delivery.getId(), 500, "err");
    assertWithinTolerance(scheduledRetryInstants.get(3), Duration.ofHours(20));

    // Attempt 5 -> failed, exhausted -- no 6th retry scheduled
    attemptWithStatus(delivery.getId(), 500, "err");
    assertEquals(4, scheduledRetryInstants.size(), "no retry may be scheduled after the final attempt");

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(WebhookDelivery.EXHAUSTED, found.getStatus());
    assertEquals(5, found.getAttemptCount());
    assertNull(found.getNextRetryAt());

    // Idempotency: attempting an already-exhausted (terminal) delivery again must be a no-op,
    // not a 6th attempt (issue #456).
    attemptWithStatus(delivery.getId(), 500, "err");
    WebhookDelivery afterExtraAttempt = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(5, afterExtraAttempt.getAttemptCount(), "a terminal delivery must never be attempted again");
    assertEquals(4, scheduledRetryInstants.size());
  }

  @Test
  void aDisabledSubscriptionExhaustsTheDeliveryWithoutAttemptingHttp() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookSubscription subscription = WebhookSubscriptionRepository.findById(subscriptionId);
    subscription.setEnabled(false);
    WebhookSubscriptionRepository.update(subscription);

    WebhookDelivery delivery = seedDelivery(subscriptionId);

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      AttemptWebhookDeliveryCommand.attempt(delivery.getId());
      httpPostCommand.verify(
          () -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()), never());
    }

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(WebhookDelivery.EXHAUSTED, found.getStatus());
    assertTrue(scheduledRetryInstants.isEmpty());
  }

  @Test
  void anUnknownDeliveryIdIsANoOp() {
    // Must not throw -- a duplicate/late-firing job for a delivery that was somehow removed.
    AttemptWebhookDeliveryCommand.attempt(999999L);
    assertTrue(scheduledRetryInstants.isEmpty());
  }

  @Test
  void aConnectionFailureClearsAResponseCodeLeftOverFromAPriorRealHttpResponse() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    // Attempt 1: a real HTTP 500 is recorded.
    attemptWithStatus(delivery.getId(), 500, "server error");
    assertEquals(500, WebhookDeliveryRepository.findById(delivery.getId()).getResponseCode());

    // Attempt 2: the endpoint never actually responds (simulates an SSRF-guard block or any
    // other network failure) -- sendAttempt() catches the exception and treats it as no result.
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenThrow(new RuntimeException("blocked by SSRF guard"));
      AttemptWebhookDeliveryCommand.attempt(delivery.getId());
    }

    WebhookDelivery found = WebhookDeliveryRepository.findById(delivery.getId());
    assertEquals(2, found.getAttemptCount());
    assertNull(found.getResponseCode(),
        "a response_code from a prior attempt must not survive an attempt that never connected");
  }

  @Test
  void losingAnOptimisticConcurrencyRaceDoesNotScheduleADuplicateRetry() {
    long subscriptionId = seedSubscription("https://example.com/hooks", "secret-abc");
    WebhookDelivery delivery = seedDelivery(subscriptionId);

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class);
        MockedStatic<WebhookDeliveryRepository> webhookDeliveryRepository = mockStatic(WebhookDeliveryRepository.class,
            CALLS_REAL_METHODS)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(500, "err"));
      // Simulate a concurrent execution that already recorded its own outcome for this row
      // between our findById() and our own write -- findById() and every other repository
      // method still run for real (CALLS_REAL_METHODS), only recordAttempt is overridden.
      webhookDeliveryRepository.when(() -> WebhookDeliveryRepository.recordAttempt(any(WebhookDelivery.class), anyInt()))
          .thenReturn(false);

      AttemptWebhookDeliveryCommand.attempt(delivery.getId());
    }

    assertTrue(scheduledRetryInstants.isEmpty(), "losing the concurrency race must not schedule a retry");
  }

  private void attemptWithStatus(long deliveryId, int statusCode, String body) {
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(statusCode, body));
      AttemptWebhookDeliveryCommand.attempt(deliveryId);
    }
  }

  private static void assertWithinTolerance(Instant actual, Duration expectedDelay) {
    Instant lowerBound = Instant.now().plus(expectedDelay).minusSeconds(10);
    Instant upperBound = Instant.now().plus(expectedDelay).plusSeconds(10);
    assertFalse(actual.isBefore(lowerBound), "scheduled retry " + actual + " is earlier than expected " + expectedDelay + " backoff");
    assertFalse(actual.isAfter(upperBound), "scheduled retry " + actual + " is later than expected " + expectedDelay + " backoff");
  }

  private long seedSubscription(String url, String secret) {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl(url);
    subscription.setEventTypes("web-page-published");
    subscription.setSecret(secret);
    subscription.setEnabled(true);
    return WebhookSubscriptionRepository.add(subscription).getId();
  }

  private WebhookDelivery seedDelivery(long subscriptionId) {
    WebhookDelivery delivery = new WebhookDelivery();
    delivery.setWebhookSubscriptionId(subscriptionId);
    delivery.setEventType("web-page-published");
    delivery.setDeliveryUuid(java.util.UUID.randomUUID().toString());
    delivery.setPayload("{\"event\":\"web-page-published\",\"occurredOn\":1,\"deliveryId\":\"x\",\"data\":{}}");
    delivery.setStatus(WebhookDelivery.PENDING);
    return WebhookDeliveryRepository.add(delivery);
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
