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

package com.simisinc.platform.application.integrations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Uninstalls an {@link IntegrationDefinition} with cleanup (issue #455): clears its credential(s)
 * for an {@code API_KEY} integration, or removes the {@code webhook_subscription} row(s) it
 * created for a {@code WEBHOOK_URL} integration -- only the rows this registry entry created
 * ({@link WebhookSubscription#getIntegrationId()} tagged), never a manually-created subscription
 * to the same or a different url.
 *
 * <p>
 * {@link #uninstall} returns whether every underlying write actually succeeded, rather than
 * silently swallowing a failed one -- a caller (the gallery widget) that ignored this and always
 * reported success would tell an admin a compromised credential/subscription was revoked when it
 * might still be live.
 * </p>
 */
public class UninstallIntegrationCommand {

  private UninstallIntegrationCommand() {
    // Static utility, not instantiated
  }

  /** @return true when every credential/subscription this integration owns was fully cleared */
  public static boolean uninstall(IntegrationDefinition definition, long userId) {
    return switch (definition.getAuthType()) {
      case API_KEY -> uninstallApiKey(definition, userId);
      case WEBHOOK_URL -> uninstallWebhook(definition);
      case OAUTH -> true; // Nothing to clean up: OAuth integrations cannot be installed yet.
    };
  }

  private static boolean uninstallApiKey(IntegrationDefinition definition, long userId) {
    List<SiteProperty> toClear = new ArrayList<>();
    Set<String> changedPropertyNames = new HashSet<>();
    for (CredentialField field : definition.getCredentialFields()) {
      String propertyName = definition.getSitePropertyPrefix() + "." + field.getName();
      SiteProperty property = SitePropertyRepository.findByName(propertyName);
      if (property != null && StringUtils.isNotBlank(property.getValue())) {
        property.setValue("");
        toClear.add(property);
        changedPropertyNames.add(propertyName);
      }
    }
    if (toClear.isEmpty()) {
      return true;
    }
    // saveAll(), not save() per-property -- see InstallIntegrationCommand.installApiKey's
    // identical note: it's the only SitePropertyRepository entry point that also invalidates
    // CacheManager's SYSTEM_PROPERTY_PREFIX_CACHE, which has no configured TTL for this prefix.
    return SitePropertyRepository.saveAll(definition.getSitePropertyPrefix(), toClear, userId, changedPropertyNames);
  }

  private static boolean uninstallWebhook(IntegrationDefinition definition) {
    boolean allRemoved = true;
    for (WebhookSubscription subscription : WebhookSubscriptionRepository.findByIntegrationId(definition.getId())) {
      if (!WebhookSubscriptionRepository.remove(subscription)) {
        allRemoved = false;
      }
    }
    return allRemoved;
  }
}
