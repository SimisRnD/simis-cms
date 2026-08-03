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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationAuthType;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

class IntegrationStatusCommandTest {

  private static SiteProperty withValue(String value) {
    SiteProperty property = new SiteProperty();
    property.setValue(value);
    return property;
  }

  @Test
  void anApiKeyIntegrationIsInstalledWhenItsPropertyHasAValue() {
    IntegrationDefinition zerobounce = IntegrationRegistryCommand.findById("zerobounce").orElseThrow();
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(eq("mailing-list.zerobounce.apiKey")))
          .thenReturn(withValue("a-real-key"));

      assertTrue(IntegrationStatusCommand.isInstalled(zerobounce));
    }
  }

  @Test
  void anApiKeyIntegrationIsNotInstalledWhenThePropertyIsBlank() {
    IntegrationDefinition zerobounce = IntegrationRegistryCommand.findById("zerobounce").orElseThrow();
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(eq("mailing-list.zerobounce.apiKey")))
          .thenReturn(withValue(""));

      assertFalse(IntegrationStatusCommand.isInstalled(zerobounce));
    }
  }

  @Test
  void anApiKeyIntegrationIsNotInstalledWhenThePropertyRowDoesNotExist() {
    IntegrationDefinition zerobounce = IntegrationRegistryCommand.findById("zerobounce").orElseThrow();
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(null);

      assertFalse(IntegrationStatusCommand.isInstalled(zerobounce));
    }
  }

  @Test
  void aWebhookIntegrationIsInstalledWhenAnEnabledTaggedSubscriptionExists() {
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();
    WebhookSubscription enabled = new WebhookSubscription();
    enabled.setEnabled(true);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId(eq("slack")))
          .thenReturn(List.of(enabled));

      assertTrue(IntegrationStatusCommand.isInstalled(slack));
    }
  }

  @Test
  void aWebhookIntegrationIsNotInstalledWhenNoTaggedSubscriptionExists() {
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId(eq("slack"))).thenReturn(List.of());

      assertFalse(IntegrationStatusCommand.isInstalled(slack));
    }
  }

  @Test
  void aWebhookIntegrationIsNotInstalledWhenTheOnlyTaggedSubscriptionIsDisabled() {
    IntegrationDefinition slack = IntegrationRegistryCommand.findById("slack").orElseThrow();
    WebhookSubscription disabled = new WebhookSubscription();
    disabled.setEnabled(false);
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId(eq("slack")))
          .thenReturn(List.of(disabled));

      assertFalse(IntegrationStatusCommand.isInstalled(slack));
    }
  }

  @Test
  void anOauthIntegrationIsNeverReportedAsInstalled() {
    IntegrationDefinition oauthIntegration = new IntegrationDefinition("some-oauth-vendor", "Some OAuth Vendor",
        "desc", "fa-plug", "https://example.com", null, IntegrationAuthType.OAUTH,
        List.of(new CredentialField("clientId", "Client ID", false, null)), null, null, null);

    assertFalse(IntegrationStatusCommand.isInstalled(oauthIntegration));
  }
}
