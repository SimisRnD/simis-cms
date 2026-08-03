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

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Whether an {@link IntegrationDefinition} is installed (issue #455) -- never stored directly:
 * computed from whatever backs its auth type, so the gallery's status badge can never drift from
 * the credential/subscription it actually reflects.
 */
public class IntegrationStatusCommand {

  private IntegrationStatusCommand() {
    // Static utility, not instantiated
  }

  /** @return true when every credential this integration needs is actually configured */
  public static boolean isInstalled(IntegrationDefinition definition) {
    return switch (definition.getAuthType()) {
      case API_KEY -> isApiKeyInstalled(definition);
      case WEBHOOK_URL -> isWebhookInstalled(definition);
      case OAUTH -> false;
    };
  }

  private static boolean isApiKeyInstalled(IntegrationDefinition definition) {
    if (definition.getCredentialFields().isEmpty()) {
      return false;
    }
    for (CredentialField field : definition.getCredentialFields()) {
      String propertyName = definition.getSitePropertyPrefix() + "." + field.getName();
      SiteProperty property = SitePropertyRepository.findByName(propertyName);
      if (property == null || StringUtils.isBlank(property.getValue())) {
        return false;
      }
    }
    return true;
  }

  private static boolean isWebhookInstalled(IntegrationDefinition definition) {
    return WebhookSubscriptionRepository.findByIntegrationId(definition.getId()).stream()
        .anyMatch(subscription -> subscription.getEnabled());
  }
}
