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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.webhooks.RotateWebhookSecretCommand;
import com.simisinc.platform.application.webhooks.SaveWebhookSubscriptionCommand;
import com.simisinc.platform.application.webhooks.TestSendWebhookCommand;
import com.simisinc.platform.application.webhooks.WebhookEventTypeCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Creates and edits a {@code webhook_subscription} (issue #453): url, event-type checkboxes, and
 * enabled. Mirrors {@code ProductCategoryFormWidget}'s execute()/post() shape. Also hosts two
 * actions scoped to an existing subscription: rotating its signing secret, and firing a
 * synchronous test send ({@code TestSendWebhookCommand}) -- both real POSTs discriminated by a
 * hidden {@code action} field, same convention as {@code ContentWidget}/{@code
 * MyMfaSettingsWidget}.
 *
 * <p>
 * The signing secret is never a form field: {@link SaveWebhookSubscriptionCommand} manages it
 * entirely server-side and it is never rendered back to the browser after the request that
 * generated/rotated it. That one-time value is handed to the browser via a session-scoped flash
 * (see {@link #flashSecret}), matching {@code MyMfaSettingsWidget}'s recovery-codes "generate
 * once, show once" convention -- the flash is read and cleared on the very next render.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookSubscriptionFormWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/webhook-subscription-form.jsp";

  private static final String SECRET_FLASH = "webhookSubscriptionSecretFlash";
  private static final String TEST_SEND_FLASH = "webhookSubscriptionTestSendFlash";

  private static final class SecretFlash {
    final long subscriptionId;
    final String secret;
    final boolean rotated;

    SecretFlash(long subscriptionId, String secret, boolean rotated) {
      this.subscriptionId = subscriptionId;
      this.secret = secret;
      this.rotated = rotated;
    }
  }

  private static final class TestSendFlash {
    final long subscriptionId;
    final TestSendWebhookCommand.TestSendResult result;

    TestSendFlash(long subscriptionId, TestSendWebhookCommand.TestSendResult result) {
      this.subscriptionId = subscriptionId;
      this.result = result;
    }
  }

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("eventTypeList", WebhookEventTypeCommand.getAll());

    WebhookSubscription webhookSubscription;
    if (context.getRequestObject() != null) {
      webhookSubscription = (WebhookSubscription) context.getRequestObject();
    } else {
      long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
      if (webhookSubscriptionId > -1) {
        webhookSubscription = WebhookSubscriptionRepository.findById(webhookSubscriptionId);
        if (webhookSubscription == null) {
          context.setErrorMessage("The webhook subscription was not found");
          webhookSubscription = new WebhookSubscription();
        }
      } else {
        webhookSubscription = new WebhookSubscription();
      }
    }
    context.getRequest().setAttribute("webhookSubscription", webhookSubscription);

    // Show a freshly generated/rotated secret exactly once (right after create or rotate)
    Object secretFlashObj = context.getRequest().getSession().getAttribute(SECRET_FLASH);
    if (secretFlashObj instanceof SecretFlash secretFlash
        && webhookSubscription.getId() != null && secretFlash.subscriptionId == webhookSubscription.getId()) {
      context.getRequest().setAttribute("generatedSecret", secretFlash.secret);
      context.getRequest().setAttribute("secretWasRotated", secretFlash.rotated);
      context.getRequest().getSession().removeAttribute(SECRET_FLASH);
    }

    // Show the result of a test send exactly once (it is never persisted, so this is its only display)
    Object testSendFlashObj = context.getRequest().getSession().getAttribute(TEST_SEND_FLASH);
    if (testSendFlashObj instanceof TestSendFlash testSendFlash
        && webhookSubscription.getId() != null && testSendFlash.subscriptionId == webhookSubscription.getId()) {
      context.getRequest().setAttribute("testSendResult", testSendFlash.result);
      context.getRequest().getSession().removeAttribute(TEST_SEND_FLASH);
    }

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {
    String action = context.getParameter("action");
    if ("rotateSecret".equals(action)) {
      return rotateSecret(context);
    }
    if ("testSend".equals(action)) {
      return testSend(context);
    }
    return save(context);
  }

  private WidgetContext save(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    WebhookSubscription bean = new WebhookSubscription();
    BeanUtils.populate(bean, context.getParameterMap());
    // Checkboxes are handled by hand -- BeanUtils has no bean property named "eventType" to bind
    // the multi-valued parameter to (see role-capabilities-form.jsp for the same convention).
    String[] eventTypeValues = context.getParameterMap().get("eventType");
    bean.setEventTypeList(eventTypeValues == null ? new ArrayList<>() : Arrays.asList(eventTypeValues));
    bean.setEnabled(context.getParameterAsBoolean("enabled"));
    bean.setCreatedBy(context.getUserId());
    bean.setModifiedBy(context.getUserId());

    boolean isNew = bean.getId() == null || bean.getId() <= -1;

    WebhookSubscription saved;
    try {
      saved = SaveWebhookSubscriptionCommand.save(bean);
    } catch (DataException e) {
      AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
          isNew ? "webhook_subscription.add" : "webhook_subscription.update", AuditEventCommand.FAILURE,
          "webhook_subscription", bean.getId() != null ? String.valueOf(bean.getId()) : null, bean.getUrl(),
          e.getMessage());
      context.setErrorMessage(e.getMessage());
      context.setRequestObject(bean);
      context.setRedirect("/admin/webhook-subscription" + (isNew ? "" : "?webhookSubscriptionId=" + bean.getId()));
      return context;
    }

    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION,
        isNew ? "webhook_subscription.add" : "webhook_subscription.update", AuditEventCommand.SUCCESS,
        "webhook_subscription", String.valueOf(saved.getId()), saved.getUrl(), null);

    if (isNew) {
      flashSecret(context, saved.getId(), saved.getSecret(), false);
    }

    context.setSuccessMessage("Webhook subscription saved");
    context.setRedirect("/admin/webhook-subscription?webhookSubscriptionId=" + saved.getId());
    return context;
  }

  private WidgetContext rotateSecret(WidgetContext context) {
    long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
    context.setRedirect("/admin/webhook-subscription?webhookSubscriptionId=" + webhookSubscriptionId);
    if (webhookSubscriptionId <= -1) {
      return context;
    }
    WebhookSubscription saved = RotateWebhookSecretCommand.rotate(webhookSubscriptionId, context.getUserId());
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "webhook_subscription.rotate_secret",
        saved != null ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "webhook_subscription", String.valueOf(webhookSubscriptionId), saved != null ? saved.getUrl() : null, null);
    if (saved == null) {
      context.setErrorMessage("Error. The webhook subscription was not found.");
      return context;
    }
    flashSecret(context, saved.getId(), saved.getSecret(), true);
    context.setSuccessMessage("The webhook secret was rotated. The previous secret no longer works.");
    return context;
  }

  private WidgetContext testSend(WidgetContext context) {
    long webhookSubscriptionId = context.getParameterAsLong("webhookSubscriptionId", -1);
    context.setRedirect("/admin/webhook-subscription?webhookSubscriptionId=" + webhookSubscriptionId);
    WebhookSubscription subscription = WebhookSubscriptionRepository.findById(webhookSubscriptionId);
    if (subscription == null) {
      context.setErrorMessage("Error. The webhook subscription was not found.");
      return context;
    }
    String eventType = context.getParameter("testEventType");
    List<String> subscribed = subscription.getEventTypeList();
    if (StringUtils.isBlank(eventType) || !subscribed.contains(eventType)) {
      eventType = subscribed.isEmpty() ? "web-page-published" : subscribed.get(0);
    }

    TestSendWebhookCommand.TestSendResult result = TestSendWebhookCommand.send(subscription, eventType);
    context.getRequest().getSession().setAttribute(TEST_SEND_FLASH,
        new TestSendFlash(subscription.getId(), result));
    context.setSuccessMessage("Test delivery sent");
    return context;
  }

  private void flashSecret(WidgetContext context, long subscriptionId, String secret, boolean rotated) {
    context.getRequest().getSession().setAttribute(SECRET_FLASH, new SecretFlash(subscriptionId, secret, rotated));
  }
}
