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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

class WebhookSubscriptionListWidgetTest extends WidgetBase {

  private static WebhookSubscription subscription(long id, boolean enabled) {
    WebhookSubscription record = new WebhookSubscription();
    record.setId(id);
    record.setUrl("https://example.com/hooks");
    record.setEnabled(enabled);
    return record;
  }

  @Test
  void deleteActuallyDeletesTheRecordAndRecordsAnAuditEvent() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webhookSubscriptionId", "4");

    WebhookSubscription target = subscription(4L, true);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(4L)).thenReturn(target);
      repository.when(() -> WebhookSubscriptionRepository.remove(target)).thenReturn(true);

      WidgetContext result = new WebhookSubscriptionListWidget().delete(widgetContext);

      repository.verify(() -> WebhookSubscriptionRepository.remove(target));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION),
          eq("webhook_subscription.remove"), eq(AuditEventCommand.SUCCESS), eq("webhook_subscription"), eq("4"),
          eq("https://example.com/hooks"), any()));
      assertEquals("Webhook subscription deleted", result.getSuccessMessage());
    }
  }

  @Test
  void deleteWithoutAdminRoleDoesNotRemoveAnything() {
    // WidgetBase's default login() has no roles granted -- mirrors an unauthorized request.
    addQueryParameter(widgetContext, "webhookSubscriptionId", "4");

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      new WebhookSubscriptionListWidget().delete(widgetContext);

      repository.verify(() -> WebhookSubscriptionRepository.findById(4L), never());
      repository.verify(() -> WebhookSubscriptionRepository.remove(any()), never());
    }
  }

  @Test
  void toggleEnabledFlipsAnEnabledSubscriptionToDisabled() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "toggleEnabled");
    addQueryParameter(widgetContext, "webhookSubscriptionId", "6");

    WebhookSubscription target = subscription(6L, true);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(6L)).thenReturn(target);
      repository.when(() -> WebhookSubscriptionRepository.update(target)).thenAnswer(i -> i.getArgument(0));

      WidgetContext result = new WebhookSubscriptionListWidget().post(widgetContext);

      assertFalse(target.getEnabled(), "toggling an enabled subscription must disable it");
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONFIGURATION),
          eq("webhook_subscription.disable"), eq(AuditEventCommand.SUCCESS), any(), any(), any(), any()));
      assertEquals("Webhook subscription disabled", result.getSuccessMessage());
    }
  }

  @Test
  void toggleEnabledFlipsADisabledSubscriptionToEnabled() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "action", "toggleEnabled");
    addQueryParameter(widgetContext, "webhookSubscriptionId", "6");

    WebhookSubscription target = subscription(6L, false);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findById(6L)).thenReturn(target);
      repository.when(() -> WebhookSubscriptionRepository.update(target)).thenAnswer(i -> i.getArgument(0));

      WidgetContext result = new WebhookSubscriptionListWidget().post(widgetContext);

      assertTrue(target.getEnabled(), "toggling a disabled subscription must enable it");
      assertEquals("Webhook subscription enabled", result.getSuccessMessage());
    }
  }

  @Test
  void postWithoutAnyKnownActionIsANoOp() {
    setRoles(widgetContext, ADMIN);
    addQueryParameter(widgetContext, "webhookSubscriptionId", "6");

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      new WebhookSubscriptionListWidget().post(widgetContext);

      repository.verify(() -> WebhookSubscriptionRepository.findById(anyLong()), never());
      repository.verify(() -> WebhookSubscriptionRepository.update(any()), never());
    }
  }
}
