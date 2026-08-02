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
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

class WebhookDeliveryListWidgetTest extends WidgetBase {

  @Test
  void listsDeliveriesForTheGivenSubscription() {
    addQueryParameter(widgetContext, "webhookSubscriptionId", "12");

    WebhookSubscription subscription = new WebhookSubscription();
    subscription.setId(12L);
    subscription.setUrl("https://example.com/hooks");

    WebhookDelivery delivered = new WebhookDelivery();
    delivered.setId(1L);
    delivered.setWebhookSubscriptionId(12L);
    delivered.setEventType("web-page-published");
    delivered.setStatus(WebhookDelivery.DELIVERED);
    delivered.setAttemptCount(1);
    delivered.setResponseCode(200);
    delivered.setResponseSnippet("ok");

    try (MockedStatic<WebhookSubscriptionRepository> subscriptionRepository = mockStatic(WebhookSubscriptionRepository.class);
        MockedStatic<WebhookDeliveryRepository> deliveryRepository = mockStatic(WebhookDeliveryRepository.class)) {
      subscriptionRepository.when(() -> WebhookSubscriptionRepository.findById(12L)).thenReturn(subscription);
      deliveryRepository.when(() -> WebhookDeliveryRepository.findBySubscriptionId(12L)).thenReturn(List.of(delivered));

      new WebhookDeliveryListWidget().execute(widgetContext);

      assertEquals(subscription, widgetContext.getRequest().getAttribute("webhookSubscription"));
      @SuppressWarnings("unchecked")
      List<WebhookDelivery> shown = (List<WebhookDelivery>) widgetContext.getRequest().getAttribute("webhookDeliveryList");
      assertEquals(1, shown.size());
      assertEquals(delivered, shown.get(0));
    }
  }

  @Test
  void aMissingSubscriptionIdYieldsAnEmptyListAndNoSubscription() {
    // No webhookSubscriptionId parameter at all.
    try (MockedStatic<WebhookDeliveryRepository> deliveryRepository = mockStatic(WebhookDeliveryRepository.class)) {
      new WebhookDeliveryListWidget().execute(widgetContext);

      assertNull(widgetContext.getRequest().getAttribute("webhookSubscription"));
      List<?> shown = (List<?>) widgetContext.getRequest().getAttribute("webhookDeliveryList");
      assertTrue(shown.isEmpty());
      deliveryRepository.verifyNoInteractions();
    }
  }
}
