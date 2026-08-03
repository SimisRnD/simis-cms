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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

class UninstallIntegrationCommandTest {

  private static IntegrationDefinition zerobounce() {
    return IntegrationRegistryCommand.findById("zerobounce").orElseThrow();
  }

  private static IntegrationDefinition slack() {
    return IntegrationRegistryCommand.findById("slack").orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private static List<SiteProperty> capturedSavedProperties(MockedStatic<SitePropertyRepository> repository) {
    ArgumentCaptor<List<SiteProperty>> captor = ArgumentCaptor.forClass(List.class);
    repository.verify(() -> SitePropertyRepository.saveAll(anyString(), captor.capture(), anyLong(), anySet()));
    return captor.getValue();
  }

  @Test
  void uninstallingAnApiKeyIntegrationBlanksItsSitePropertyAndReportsSuccess() {
    SiteProperty existing = new SiteProperty();
    existing.setId(1);
    existing.setValue("a-real-key");
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(existing);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), any(), eq(42L), anySet())).thenReturn(true);

      boolean success = UninstallIntegrationCommand.uninstall(zerobounce(), 42L);

      assertTrue(success);
      assertEquals("", capturedSavedProperties(repository).get(0).getValue());
    }
  }

  @Test
  void uninstallingAnApiKeyIntegrationReportsFailureWhenTheUnderlyingSaveFails() {
    // Issue #455 review: a failed clear must not be reported to the caller as a success.
    SiteProperty existing = new SiteProperty();
    existing.setId(1);
    existing.setValue("a-real-key");
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("mailing-list.zerobounce.apiKey"))
          .thenReturn(existing);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), any(), eq(42L), anySet())).thenReturn(false);

      boolean success = UninstallIntegrationCommand.uninstall(zerobounce(), 42L);

      assertFalse(success);
    }
  }

  @Test
  void uninstallingAnApiKeyIntegrationThatWasNeverInstalledDoesNothingAndReportsSuccess() {
    SiteProperty blank = new SiteProperty();
    blank.setId(1);
    blank.setValue("");
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("mailing-list.zerobounce.apiKey")).thenReturn(blank);

      boolean success = UninstallIntegrationCommand.uninstall(zerobounce(), 42L);

      assertTrue(success, "nothing to clear is not a failure");
      repository.verify(() -> SitePropertyRepository.saveAll(anyString(), any(), anyLong(), anySet()), never());
    }
  }

  @Test
  void uninstallingAWebhookIntegrationRemovesOnlyItsTaggedSubscriptionsAndReportsSuccess() {
    WebhookSubscription slackSubscription = new WebhookSubscription();
    slackSubscription.setId(1L);
    slackSubscription.setIntegrationId("slack");
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack"))
          .thenReturn(List.of(slackSubscription));
      repository.when(() -> WebhookSubscriptionRepository.remove(slackSubscription)).thenReturn(true);

      boolean success = UninstallIntegrationCommand.uninstall(slack(), 42L);

      assertTrue(success);
      repository.verify(() -> WebhookSubscriptionRepository.remove(slackSubscription));
      repository.verify(() -> WebhookSubscriptionRepository.remove(any()), times(1));
    }
  }

  @Test
  void uninstallingAWebhookIntegrationReportsFailureWhenARemoveFails() {
    // Issue #455 review: a failed delete must not be reported to the caller as a success.
    WebhookSubscription slackSubscription = new WebhookSubscription();
    slackSubscription.setId(1L);
    slackSubscription.setIntegrationId("slack");
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack"))
          .thenReturn(List.of(slackSubscription));
      repository.when(() -> WebhookSubscriptionRepository.remove(slackSubscription)).thenReturn(false);

      boolean success = UninstallIntegrationCommand.uninstall(slack(), 42L);

      assertFalse(success);
    }
  }

  @Test
  void uninstallingAWebhookIntegrationThatWasNeverInstalledRemovesNothingAndReportsSuccess() {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());

      boolean success = UninstallIntegrationCommand.uninstall(slack(), 42L);

      assertTrue(success, "nothing to remove is not a failure");
      repository.verify(() -> WebhookSubscriptionRepository.remove(any()), never());
    }
  }

  @Test
  void uninstallingAnOauthIntegrationReportsSuccessWithNothingToDo() {
    IntegrationDefinition oauthIntegration = new IntegrationDefinition("some-oauth-vendor", "Some OAuth Vendor",
        "desc", "fa-plug", "https://example.com", null,
        com.simisinc.platform.domain.model.integrations.IntegrationAuthType.OAUTH, List.of(), null, null, null);

    assertTrue(UninstallIntegrationCommand.uninstall(oauthIntegration, 42L));
  }
}
