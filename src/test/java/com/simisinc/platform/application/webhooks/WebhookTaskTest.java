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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.binary.Hex;
import org.jeasy.flows.work.TaskContext;
import org.jeasy.flows.work.WorkContext;
import org.jeasy.flows.work.WorkReport;
import org.jeasy.flows.work.WorkStatus;
import org.junit.jupiter.api.AfterAll;
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
import com.simisinc.platform.domain.events.cms.UserSignedUpEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataSource;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.infrastructure.workflow.WebhookTask;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;

/**
 * End-to-end coverage of {@link WebhookTask} (issue #418) through the real dispatch/attempt
 * pipeline -- {@link DispatchWebhookDeliveriesCommand} and {@link AttemptWebhookDeliveryCommand}
 * both run for real, including real subscription matching, real {@code webhook_delivery} row
 * creation, and real HMAC signing. Only the actual outbound HTTP call is stubbed (via a static
 * mock of {@link HttpPostCommand}), since the SSRF-guarded connection mechanics themselves --
 * DNS validation, connect-time pinning, a real blocked url never connecting -- are already
 * covered end to end against a real HTTP server in {@code HttpPostCommandExecuteUserUrlTest}.
 * What this test proves instead is what is specific to {@code WebhookTask}: a subscription
 * matching the event's type (and only that one) gets a delivery attempt, the correct HMAC
 * signature is computed over the exact payload sent, and the call goes through
 * {@code HttpPostCommand}'s SSRF-guarded {@code executeUserUrlWithResponse}, never the unguarded
 * {@code execute(...)} family.
 *
 * <p>
 * {@link DispatchWebhookDeliveriesCommand#enqueueFirstAttempt} is substituted to run
 * {@link AttemptWebhookDeliveryCommand#attempt(long)} synchronously in-process rather than via
 * the real JobRunr scheduler, which is not configured in a plain unit test.
 * </p>
 */
class WebhookTaskTest {

  private static final String DEFAULT_IMAGE = "postgres:15-alpine";
  private static final int POSTGRES_PORT = 5432;
  private static final String DB_NAME = "simis_cms_test";
  private static final String DB_USER = "simis";
  private static final String DB_PASSWORD = "simis";

  private static GenericContainer<?> postgres;

  @BeforeAll
  static void startDatabase() {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping WebhookTask integration test");

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
  void setUp() throws SQLException {
    Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping WebhookTask integration test");
    try (Connection connection = DB.getConnection(); Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE TABLE webhook_delivery, webhook_subscription RESTART IDENTITY CASCADE");
    }
    // Run the first attempt synchronously in-process instead of via the real JobRunr scheduler.
    DispatchWebhookDeliveriesCommand.enqueueFirstAttempt = AttemptWebhookDeliveryCommand::attempt;
  }

  @Test
  void matchingEnabledSubscriptionGetsARealSignedDeliveryDisabledAndNonMatchingDoNot() throws Exception {
    long matching = seedSubscription("https://hooks.example.com/simis", "trap-secret", "user-signed-up", true);
    long nonMatching = seedSubscription("https://hooks.example.com/other", "trap-secret", "order-submitted", true);
    long disabledMatch = seedSubscription("https://hooks.example.com/disabled", "trap-secret", "user-signed-up", false);

    User user = new User();
    user.setId(9L);
    user.setUsername("newuser");
    user.setEmail("newuser@example.com");
    UserSignedUpEvent event = new UserSignedUpEvent(user);

    WorkContext workContext = new WorkContext();
    workContext.put(WorkflowManager.EVENT_OBJECT, event);
    TaskContext taskContext = new TaskContext(new WebhookTask());

    ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);

    WorkReport report;
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(
          urlCaptor.capture(), headersCaptor.capture(), bodyCaptor.capture(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      report = new WebhookTask().execute(workContext, taskContext);

      // Exactly one guarded POST was made -- to the matching subscription's url -- and the
      // unguarded execute() family was never touched.
      httpPostCommand.verify(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()));
      httpPostCommand.verify(() -> HttpPostCommand.execute(anyString(), any(Map.class), anyString(), anyInt()), never());
    }

    assertEquals(WorkStatus.COMPLETED, report.getStatus());
    assertEquals("https://hooks.example.com/simis", urlCaptor.getValue());

    // The matching, enabled subscription got exactly one delivered delivery.
    List<WebhookDelivery> matchingDeliveries = WebhookDeliveryRepository.findBySubscriptionId(matching);
    assertEquals(1, matchingDeliveries.size());
    assertEquals(WebhookDelivery.DELIVERED, matchingDeliveries.get(0).getStatus());

    // The non-matching and disabled subscriptions got none at all.
    assertTrue(WebhookDeliveryRepository.findBySubscriptionId(nonMatching).isEmpty());
    assertTrue(WebhookDeliveryRepository.findBySubscriptionId(disabledMatch).isEmpty());

    // The HMAC signature sent is independently verifiable against the exact body sent, using the
    // matching subscription's secret.
    String signatureHeader = headersCaptor.getValue().get(SignWebhookPayloadCommand.HEADER_NAME);
    assertTrue(signatureHeader.startsWith("sha256="));
    byte[] expected = independentHmacSha256(bodyCaptor.getValue(), "trap-secret");
    assertTrue(MessageDigest.isEqual(expected, Hex.decodeHex(signatureHeader.substring("sha256=".length()))));

    // And the body sent is exactly the delivery's stored payload snapshot.
    assertEquals(matchingDeliveries.get(0).getPayload(), bodyCaptor.getValue());
  }

  private static byte[] independentHmacSha256(String payload, String secret) throws Exception {
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
    return mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private long seedSubscription(String url, String secret, String eventTypes, boolean enabled) {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setUrl(url);
    subscription.setEventTypes(eventTypes);
    subscription.setSecret(secret);
    subscription.setEnabled(enabled);
    return WebhookSubscriptionRepository.add(subscription).getId();
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
