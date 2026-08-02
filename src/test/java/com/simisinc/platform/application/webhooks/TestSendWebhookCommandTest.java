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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.application.http.HttpPostCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;

/**
 * Verifies {@link TestSendWebhookCommand}'s "dry-run" contract (issue #453): a genuine signed
 * HTTP POST using the same primitives production delivery uses, but with none of production
 * delivery's side effects -- no {@code webhook_delivery} row, no retry.
 */
class TestSendWebhookCommandTest {

  private static WebhookSubscription subscription(String url, String secret) {
    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setId(7L);
    subscription.setUrl(url);
    subscription.setSecret(secret);
    subscription.setEventTypeList(java.util.List.of("web-page-published"));
    subscription.setEnabled(true);
    return subscription;
  }

  @Test
  void aSuccessfulSendReturnsTheRealStatusCodeAndBody() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, "web-page-published");

      assertTrue(result.isRequestSent());
      assertEquals(200, result.getStatusCode());
      assertEquals("ok", result.getResponseSnippet());
    }
  }

  @Test
  void aNonTwoHundredStatusIsStillReportedNotTreatedAsFailureToSend() {
    // Same contract as AttemptWebhookDeliveryCommand: a real non-2xx response is still a real
    // response, and the admin needs to see it (e.g. a 401 means "reachable, but rejects our
    // signature/secret").
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(401, "unauthorized"));

      TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, "web-page-published");

      assertTrue(result.isRequestSent());
      assertEquals(401, result.getStatusCode());
      assertEquals("unauthorized", result.getResponseSnippet());
    }
  }

  @Test
  void aBlockedOrUnreachableUrlReportsNoResponseRatherThanThrowing() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(null);

      TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, "web-page-published");

      assertFalse(result.isRequestSent());
      assertNull(result.getStatusCode());
      assertNull(result.getResponseSnippet());
    }
  }

  @Test
  void anExceptionFromTheHttpCallIsCaughtAndReportedAsNoResponse() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenThrow(new RuntimeException("connection reset"));

      TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, "web-page-published");

      assertFalse(result.isRequestSent());
    }
  }

  @Test
  void theSignatureHeaderMatchesSignWebhookPayloadCommand() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(eq("https://example.com/hooks"),
          headersCaptor.capture(), payloadCaptor.capture(), eq(HttpPostCommand.POST)))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      TestSendWebhookCommand.send(subscription, "web-page-published");
    }

    String expectedSignature = SignWebhookPayloadCommand.signatureHeaderValue(payloadCaptor.getValue(), "secret-abc");
    assertEquals(expectedSignature, headersCaptor.getValue().get(SignWebhookPayloadCommand.HEADER_NAME));
    assertEquals("application/json", headersCaptor.getValue().get("Content-Type"));
  }

  @Test
  void usesTheSsrfGuardedCallNotTheUnguardedOne() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      TestSendWebhookCommand.send(subscription, "web-page-published");

      httpPostCommand.verify(
          () -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()), times(1));
      httpPostCommand.verify(() -> HttpPostCommand.execute(anyString(), any(Map.class), anyString(), anyInt()), never());
    }
  }

  @Test
  void theSamplePayloadMatchesTheRealEventOccurredOnDeliveryIdDataShape() throws Exception {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(),
          payloadCaptor.capture(), anyInt())).thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      TestSendWebhookCommand.send(subscription, "web-page-published");
    }

    JsonNode root = new ObjectMapper().readTree(payloadCaptor.getValue());
    assertEquals("web-page-published", root.get("event").asText());
    assertTrue(root.has("occurredOn"));
    assertTrue(root.has("deliveryId"));
    assertTrue(root.get("data").get("test").asBoolean());
    assertEquals(7, root.get("data").get("subscriptionId").asInt());
  }

  @Test
  void aLongResponseBodyIsTruncatedToFiveHundredCharacters() {
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");
    String longBody = "x".repeat(900);

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, longBody));

      TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, "web-page-published");

      assertEquals(500, result.getResponseSnippet().length());
    }
  }

  @Test
  void neverCreatesAWebhookDeliveryRowOrTouchesTheDeliveryRepositoryAtAll() {
    // The strongest proof this is not AttemptWebhookDeliveryCommand's persistence path: the
    // delivery repository is mocked with zero stubs, so any interaction at all would either
    // return null/throw or, as asserted here, simply never happen.
    WebhookSubscription subscription = subscription("https://example.com/hooks", "secret-abc");

    try (MockedStatic<HttpPostCommand> httpPostCommand = mockStatic(HttpPostCommand.class);
        MockedStatic<WebhookDeliveryRepository> deliveryRepository = mockStatic(WebhookDeliveryRepository.class)) {
      httpPostCommand.when(() -> HttpPostCommand.executeUserUrlWithResponse(anyString(), anyMap(), anyString(), anyInt()))
          .thenReturn(new HttpPostCommand.HttpPostResult(200, "ok"));

      TestSendWebhookCommand.send(subscription, "web-page-published");

      deliveryRepository.verifyNoInteractions();
    }
  }
}
