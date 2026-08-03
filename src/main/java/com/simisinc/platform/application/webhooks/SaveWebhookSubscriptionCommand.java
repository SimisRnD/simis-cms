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

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.UrlCommand;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Validates and saves a {@code webhook_subscription} (issue #453). Mirrors {@code
 * SaveProductCategoryCommand}'s shape: the incoming form bean is validated, then copied field by
 * field onto either a freshly loaded copy of the existing persisted record (update) or a new one
 * (create) -- never persisted directly -- so a field the form never submits (here, {@code
 * secret}) can never be blanked or tampered with by what the client sends.
 *
 * <p>
 * A new subscription is issued a fresh secret via {@link GenerateWebhookSecretCommand}; the
 * plaintext is returned on the saved record so the caller (the form widget) can show it to the
 * admin once. An update never touches the existing secret -- use {@link
 * RotateWebhookSecretCommand} to replace it.
 * </p>
 *
 * @author SimIS Inc.
 */
public class SaveWebhookSubscriptionCommand {

  private static final Log LOG = LogFactory.getLog(SaveWebhookSubscriptionCommand.class);

  private SaveWebhookSubscriptionCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param bean form-submitted values: id (-1 for a new record), url, eventTypeList, enabled,
   *        createdBy/modifiedBy -- never secret, which this command manages itself
   * @return the saved record; for a new subscription, {@link WebhookSubscription#getSecret()} is
   *         the plaintext secret to show the admin once
   * @throws DataException when required fields are missing/invalid, or the referenced existing
   *         record cannot be found
   */
  public static WebhookSubscription save(WebhookSubscription bean) throws DataException {
    StringBuilder errorMessages = new StringBuilder();

    if (bean.getCreatedBy() == -1 || bean.getModifiedBy() == -1) {
      errorMessages.append("The user saving this subscription was not set. ");
    }
    if (StringUtils.isBlank(bean.getUrl()) || !UrlCommand.isUrlValid(bean.getUrl().trim())) {
      errorMessages.append("A valid http(s) URL is required. ");
    }
    List<String> eventTypeList = bean.getEventTypeList();
    if (eventTypeList.isEmpty()) {
      errorMessages.append("At least one event type must be selected. ");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages);
    }

    WebhookSubscription subscription;
    boolean isNew = bean.getId() == null || bean.getId() <= -1;
    if (isNew) {
      LOG.debug("Saving a new webhook subscription...");
      subscription = new WebhookSubscription();
      subscription.setSecret(GenerateWebhookSecretCommand.generate());
      subscription.setCreatedBy(bean.getCreatedBy());
      // Issue #455: only meaningful at creation, e.g. when the integration registry's one-click
      // install builds this bean. A bean from the standalone webhook-subscription admin form never
      // sets this, so it's simply null there, same as always.
      subscription.setIntegrationId(bean.getIntegrationId());
    } else {
      LOG.debug("Saving an existing webhook subscription...");
      subscription = WebhookSubscriptionRepository.findById(bean.getId());
      if (subscription == null) {
        throw new DataException("The existing subscription could not be found");
      }
      // Deliberately not touching subscription.secret or subscription.integrationId -- see
      // RotateWebhookSecretCommand, and see issue #455: an admin editing a registry-installed
      // subscription's url/events through the generic form must not silently un-tag it.
    }

    subscription.setUrl(bean.getUrl().trim());
    subscription.setEventTypeList(eventTypeList);
    subscription.setEnabled(bean.getEnabled());
    subscription.setModifiedBy(bean.getModifiedBy());

    WebhookSubscription saved = WebhookSubscriptionRepository.save(subscription);
    if (saved == null) {
      throw new DataException("Your information could not be saved due to a system error. Please try again.");
    }
    return saved;
  }
}
