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

import java.util.List;

import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Lists {@code webhook_subscription} records for the admin panel (issue #453): create/edit
 * links, a quick enable/disable toggle, and delete. Mirrors {@code ProductCategoriesListWidget}'s
 * shape.
 *
 * @author SimIS Inc.
 */
public class WebhookSubscriptionListWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/webhook-subscriptions-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Load the subscriptions
    List<WebhookSubscription> webhookSubscriptionList = WebhookSubscriptionRepository.findAll();
    context.getRequest().setAttribute("webhookSubscriptionList", webhookSubscriptionList);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    if (!context.hasRole("admin")) {
      return context;
    }
    if ("toggleEnabled".equals(context.getParameter("action"))) {
      return toggleEnabled(context);
    }
    return context;
  }

  public WidgetContext delete(WidgetContext context) {
    long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
    WebhookSubscription record = null;
    if (context.hasRole("admin")) {
      record = WebhookSubscriptionRepository.findById(webhookSubscriptionId);
    }
    if (record == null) {
      LOG.warn("Webhook subscription does not exist or no access: " + webhookSubscriptionId);
      context.setErrorMessage("Error. No access to remove this webhook subscription.");
      return context;
    }

    try {
      boolean removed = WebhookSubscriptionRepository.remove(record);
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "webhook_subscription.remove",
          removed ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
          "webhook_subscription", String.valueOf(record.getId()), record.getUrl(), null);
      if (removed) {
        context.setSuccessMessage("Webhook subscription deleted");
      } else {
        context.setErrorMessage("Error. Webhook subscription could not be deleted.");
      }
    } catch (Exception e) {
      LOG.error("Webhook subscription delete failed", e);
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "webhook_subscription.remove",
          AuditEventCommand.FAILURE, "webhook_subscription", String.valueOf(record.getId()), record.getUrl(),
          e.getMessage());
      context.setErrorMessage("Error. Webhook subscription could not be deleted.");
    }
    return context;
  }

  private WidgetContext toggleEnabled(WidgetContext context) {
    long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
    WebhookSubscription record = WebhookSubscriptionRepository.findById(webhookSubscriptionId);
    if (record == null) {
      context.setErrorMessage("Error. Webhook subscription was not found.");
      return context;
    }
    boolean newValue = !record.getEnabled();
    record.setEnabled(newValue);
    record.setModifiedBy(context.getUserId());
    WebhookSubscription saved = WebhookSubscriptionRepository.update(record);
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
        newValue ? "webhook_subscription.enable" : "webhook_subscription.disable",
        saved != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "webhook_subscription", String.valueOf(record.getId()), record.getUrl(), null);
    if (saved == null) {
      context.setErrorMessage("Error. Webhook subscription could not be updated.");
      return context;
    }
    context.setSuccessMessage(newValue ? "Webhook subscription enabled" : "Webhook subscription disabled");
    return context;
  }
}
