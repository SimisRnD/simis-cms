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

import java.util.ArrayList;
import java.util.List;

import com.simisinc.platform.domain.model.webhooks.WebhookDelivery;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookDeliveryRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Read-only delivery history for one {@code webhook_subscription} (issue #453's requirement #4):
 * timestamp, status, attempt count, response code, and a truncated response snippet for every
 * {@code webhook_delivery} row belonging to it. Uses {@code
 * WebhookDeliveryRepository#findBySubscriptionId}, already exposed by the #418 delivery engine.
 *
 * <p>
 * Never shows a test-send result (see {@code TestSendWebhookCommand}) -- test sends do not create
 * a {@code webhook_delivery} row at all, so this list is exclusively genuine production
 * deliveries.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookDeliveryListWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/webhook-deliveries-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
    WebhookSubscription webhookSubscription = webhookSubscriptionId > -1
        ? WebhookSubscriptionRepository.findById(webhookSubscriptionId)
        : null;
    context.getRequest().setAttribute("webhookSubscription", webhookSubscription);

    List<WebhookDelivery> webhookDeliveryList = webhookSubscription != null
        ? WebhookDeliveryRepository.findBySubscriptionId(webhookSubscriptionId)
        : new ArrayList<>();
    context.getRequest().setAttribute("webhookDeliveryList", webhookDeliveryList);

    context.setJsp(JSP);
    return context;
  }
}
