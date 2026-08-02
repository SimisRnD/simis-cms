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

import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Replaces a {@code webhook_subscription}'s signing secret with a freshly generated one (issue
 * #453). The old secret stops working immediately -- any deliveries already queued for retry
 * (see {@code AttemptWebhookDeliveryCommand}) will sign their next attempt with the new secret,
 * same as {@code SignWebhookPayloadCommand} always signing with whatever secret is currently on
 * the subscription record.
 *
 * @author SimIS Inc.
 */
public class RotateWebhookSecretCommand {

  private RotateWebhookSecretCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param subscriptionId the subscription to rotate
   * @param modifiedBy the acting admin's user id
   * @return the saved record with the new plaintext secret set, to show the admin once; null if
   *         the subscription does not exist or the save failed
   */
  public static WebhookSubscription rotate(long subscriptionId, long modifiedBy) {
    WebhookSubscription subscription = WebhookSubscriptionRepository.findById(subscriptionId);
    if (subscription == null) {
      return null;
    }
    subscription.setSecret(GenerateWebhookSecretCommand.generate());
    subscription.setModifiedBy(modifiedBy);
    return WebhookSubscriptionRepository.update(subscription);
  }
}
