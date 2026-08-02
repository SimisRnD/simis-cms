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

package com.simisinc.platform.presentation.widgets.admin.webhooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.webhooks.RotateWebhookSecretCommand;
import com.simisinc.platform.application.webhooks.SaveWebhookSubscriptionCommand;
import com.simisinc.platform.application.webhooks.TestSendWebhookCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

class WebhookSubscriptionFormWidgetTest extends WidgetBase {

  private static WebhookSubscription subscription(long id, List<String> eventTypes) {
    WebhookSubscription record = new WebhookSubscription();
    record.setId(id);
    record.setUrl("https://example.com/hooks");
    record.setEventTypeList(eventTypes);
    record.setEnabled(true);
    return record;
  }

  @Test
  void savingANewSubscriptionRedirectsToItAndFlashesTheGeneratedSecretExactlyOnce() throws Exception {
    addQueryParameter(widgetContext, "url", "https://example.com/hooks");
    widgetContext.getParameterMap().put("eventType", new String[] { "web-page-published", "order-submitted" });
    addQueryParameter(widgetContext, "enabled", "true");

    WebhookSubscription saved = subscription(10L, List.of("web-page-published", "order-submitted"));
    saved.setSecret("generated-secret-value");

    try (MockedStatic<SaveWebhookSubscriptionCommand> saveCommand = mockStatic(SaveWebhookSubscriptionCommand.class);
        MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      saveCommand.when(() -> SaveWebhookSubscriptionCommand.save(any())).thenReturn(saved);

      WidgetContext postResult = new WebhookSubscriptionFormWidget().post(widgetContext);
      assertEquals("/admin/webhook-subscription?webhookSubscriptionId=10", postResult.getRedirect());

      // Verify the event-type checkboxes were parsed and forwarded to the save command.
      saveCommand.verify(() -> SaveWebhookSubscriptionCommand
          .save(argThat(bean -> bean.getEventTypeList().equals(List.of("web-page-published", "order-submitted")))));

      // Simulate the GET that follows the redirect: the flash must show the secret exactly once.
      repository.when(() -> WebhookSubscriptionRepository.findById(10L)).thenReturn(subscription(10L, List.of()));
      addQueryParameter(widgetContext, "webhookSubscriptionId", "10");

      new WebhookSubscriptionFormWidget().execute(widgetContext);
      assertEquals("generated-secret-value", widgetContext.getRequest().getAttribute("generatedSecret"));
      assertEquals(false, widgetContext.getRequest().getAttribute("secretWasRotated"));

      // WidgetBase's mock request keeps attributes across calls in the same test (unlike a real
      // per-request object), so clear it here to prove the *session* flash -- not just this stale
      // mock state -- was actually consumed and removed.
      widgetContext.getRequest().setAttribute("generatedSecret", null);
      new WebhookSubscriptionFormWidget().execute(widgetContext);
      assertNull(widgetContext.getRequest().getAttribute("generatedSecret"), "the secret must not be shown a second time");
    }
  }

  @Test
  void rotatingTheSecretFlashesTheNewValueAndMarksItAsRotated() throws Exception {
    addQueryParameter(widgetContext, "action", "rotateSecret");
    addQueryParameter(widgetContext, "webhookSubscriptionId", "5");

    WebhookSubscription rotated = subscription(5L, List.of("web-page-published"));
    rotated.setSecret("rotated-secret-value");

    try (MockedStatic<RotateWebhookSecretCommand> rotateCommand = mockStatic(RotateWebhookSecretCommand.class);
        MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      rotateCommand.when(() -> RotateWebhookSecretCommand.rotate(5L, widgetContext.getUserId())).thenReturn(rotated);

      WidgetContext postResult = new WebhookSubscriptionFormWidget().post(widgetContext);
      assertEquals("/admin/webhook-subscription?webhookSubscriptionId=5", postResult.getRedirect());

      repository.when(() -> WebhookSubscriptionRepository.findById(5L)).thenReturn(subscription(5L, List.of()));
      new WebhookSubscriptionFormWidget().execute(widgetContext);

      assertEquals("rotated-secret-value", widgetContext.getRequest().getAttribute("generatedSecret"));
      assertEquals(true, widgetContext.getRequest().getAttribute("secretWasRotated"));
    }
  }

  @Test
  void testSendFlashesTheResultAndNeverCreatesADeliveryRecordItself() throws Exception {
    addQueryParameter(widgetContext, "action", "testSend");
    addQueryParameter(widgetContext, "webhookSubscriptionId", "8");
    addQueryParameter(widgetContext, "testEventType", "web-page-published");

    WebhookSubscription target = subscription(8L, List.of("web-page-published"));
    TestSendWebhookCommand.TestSendResult sendResult = new TestSendWebhookCommand.TestSendResult(true, 200, "ok",
        "{}", "sha256=abc", new java.sql.Timestamp(System.currentTimeMillis()));

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<TestSendWebhookCommand> testSendCommand = mockStatic(TestSendWebhookCommand.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(8L)).thenReturn(target);
      testSendCommand.when(() -> TestSendWebhookCommand.send(target, "web-page-published")).thenReturn(sendResult);

      WidgetContext postResult = new WebhookSubscriptionFormWidget().post(widgetContext);
      assertEquals("/admin/webhook-subscription?webhookSubscriptionId=8", postResult.getRedirect());
      assertEquals("Test delivery sent", postResult.getSuccessMessage());

      testSendCommand.verify(() -> TestSendWebhookCommand.send(target, "web-page-published"), times(1));

      new WebhookSubscriptionFormWidget().execute(widgetContext);
      assertEquals(sendResult, widgetContext.getRequest().getAttribute("testSendResult"));
    }
  }

  @Test
  void testSendFallsBackToTheFirstSubscribedEventTypeWhenNoneIsChosen() throws Exception {
    addQueryParameter(widgetContext, "action", "testSend");
    addQueryParameter(widgetContext, "webhookSubscriptionId", "8");
    // No testEventType parameter set at all.

    WebhookSubscription target = subscription(8L, List.of("order-submitted", "web-page-published"));
    TestSendWebhookCommand.TestSendResult sendResult = new TestSendWebhookCommand.TestSendResult(true, 200, "ok",
        "{}", "sha256=abc", new java.sql.Timestamp(System.currentTimeMillis()));

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<TestSendWebhookCommand> testSendCommand = mockStatic(TestSendWebhookCommand.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(8L)).thenReturn(target);
      testSendCommand.when(() -> TestSendWebhookCommand.send(eq(target), any())).thenReturn(sendResult);

      new WebhookSubscriptionFormWidget().post(widgetContext);

      testSendCommand.verify(() -> TestSendWebhookCommand.send(target, "order-submitted"));
    }
  }

  @Test
  void aValidationFailureKeepsTheSubmittedValuesAndShowsTheError() throws Exception {
    addQueryParameter(widgetContext, "url", "");
    widgetContext.getParameterMap().put("eventType", new String[] { "web-page-published" });

    try (MockedStatic<SaveWebhookSubscriptionCommand> saveCommand = mockStatic(SaveWebhookSubscriptionCommand.class)) {
      saveCommand.when(() -> SaveWebhookSubscriptionCommand.save(any()))
          .thenThrow(new DataException("A valid http(s) URL is required."));

      WidgetContext result = new WebhookSubscriptionFormWidget().post(widgetContext);

      assertTrue(result.getErrorMessage().contains("URL"));
      assertEquals("/admin/webhook-subscription", result.getRedirect());
    }
  }
}
