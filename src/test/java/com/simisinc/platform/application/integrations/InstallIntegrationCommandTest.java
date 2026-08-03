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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationAuthType;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

class InstallIntegrationCommandTest {

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
  void installingAnApiKeyIntegrationSavesEachCredentialFieldToItsSitePropertyRow() throws DataException {
    SiteProperty existing = new SiteProperty();
    existing.setId(1);
    existing.setName("mailing-list.zerobounce.apiKey");
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("mailing-list.zerobounce.apiKey")).thenReturn(existing);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), any(), eq(42L), anySet())).thenReturn(true);

      InstallIntegrationCommand.install(zerobounce(), Map.of("apiKey", "a-real-key"), List.of(), 42L);

      List<SiteProperty> saved = capturedSavedProperties(repository);
      assertEquals(1, saved.size());
      assertEquals("a-real-key", saved.get(0).getValue());
    }
  }

  @Test
  void installingAnApiKeyIntegrationMarksThePropertyAsChangedSoItsRotationTimestampIsStamped() throws DataException {
    SiteProperty existing = new SiteProperty();
    existing.setId(1);
    existing.setName("mailing-list.zerobounce.apiKey");
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("mailing-list.zerobounce.apiKey")).thenReturn(existing);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), any(), eq(42L), anySet())).thenReturn(true);

      InstallIntegrationCommand.install(zerobounce(), Map.of("apiKey", "a-real-key"), List.of(), 42L);

      ArgumentCaptor<Set<String>> changedCaptor = ArgumentCaptor.forClass(Set.class);
      repository.verify(() -> SitePropertyRepository.saveAll(eq("mailing-list.zerobounce"), any(), eq(42L),
          changedCaptor.capture()));
      assertEquals(Set.of("mailing-list.zerobounce.apiKey"), changedCaptor.getValue());
    }
  }

  @Test
  void installingAnApiKeyIntegrationTrimsTheSubmittedValue() throws DataException {
    SiteProperty existing = new SiteProperty();
    existing.setId(1);
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName(anyString())).thenReturn(existing);
      repository.when(() -> SitePropertyRepository.saveAll(anyString(), any(), eq(42L), anySet())).thenReturn(true);

      InstallIntegrationCommand.install(zerobounce(), Map.of("apiKey", "  a-real-key  "), List.of(), 42L);

      assertEquals("a-real-key", capturedSavedProperties(repository).get(0).getValue());
    }
  }

  @Test
  void installingAnApiKeyIntegrationWithABlankRequiredFieldThrows() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      DataException e = assertThrows(DataException.class,
          () -> InstallIntegrationCommand.install(zerobounce(), Map.of("apiKey", ""), List.of(), 42L));
      assertTrue(e.getMessage().contains("API Key"));
      repository.verify(() -> SitePropertyRepository.saveAll(anyString(), any(), anyLong(), anySet()), never());
    }
  }

  @Test
  void installingAnApiKeyIntegrationWithAWhitespaceOnlyRequiredFieldThrows() {
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      DataException e = assertThrows(DataException.class,
          () -> InstallIntegrationCommand.install(zerobounce(), Map.of("apiKey", "   "), List.of(), 42L));
      assertTrue(e.getMessage().contains("API Key"));
      repository.verify(() -> SitePropertyRepository.saveAll(anyString(), any(), anyLong(), anySet()), never());
    }
  }

  @Test
  void installingAnApiKeyIntegrationWhoseSitePropertyRowDoesNotExistThrows() {
    // A synthetic definition, not a real registry entry: every real API_KEY entry's row is
    // pre-seeded (see InstallIntegrationCommand's class javadoc), so this exercises the defensive
    // branch for a hypothetical registry entry added without seeding its property.
    IntegrationDefinition unseeded = new IntegrationDefinition("unseeded", "Unseeded Integration", "desc", "fa-plug",
        "https://example.com", null, IntegrationAuthType.API_KEY,
        List.of(new CredentialField("apiKey", "API Key", true, null)), "unseeded.vendor", null, null);
    try (MockedStatic<SitePropertyRepository> repository = mockStatic(SitePropertyRepository.class)) {
      repository.when(() -> SitePropertyRepository.findByName("unseeded.vendor.apiKey")).thenReturn(null);

      DataException e = assertThrows(DataException.class,
          () -> InstallIntegrationCommand.install(unseeded, Map.of("apiKey", "a-key"), List.of(), 42L));
      assertTrue(e.getMessage().contains("Unseeded Integration"));
    }
  }

  @Test
  void installingAnOauthIntegrationThrows() {
    IntegrationDefinition oauthIntegration = new IntegrationDefinition("some-oauth-vendor", "Some OAuth Vendor",
        "desc", "fa-plug", "https://example.com", null, IntegrationAuthType.OAUTH, List.of(), null, null, null);

    DataException e = assertThrows(DataException.class,
        () -> InstallIntegrationCommand.install(oauthIntegration, Map.of(), List.of(), 42L));
    assertTrue(e.getMessage().contains("Some OAuth Vendor"));
  }

  @Test
  void installingAWebhookIntegrationCreatesATaggedSubscriptionWithTheDefaultEventTypesWhenNoneAreSelected()
      throws DataException {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      InstallIntegrationCommand.install(slack(), Map.of("webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz"),
          List.of(), 42L);

      ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
      repository.verify(() -> WebhookSubscriptionRepository.save(captor.capture()));
      WebhookSubscription saved = captor.getValue();
      assertEquals("https://hooks.slack.com/services/T00/B00/xyz", saved.getUrl());
      assertEquals("slack", saved.getIntegrationId());
      assertEquals(slack().getDefaultEventTypeIds(), saved.getEventTypeList());
      assertTrue(saved.getEnabled());
    }
  }

  @Test
  void installingAWebhookIntegrationHonorsExplicitlySelectedSupportedEventTypes() throws DataException {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      InstallIntegrationCommand.install(slack(), Map.of("webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz"),
          List.of("mailing-list-member-created"), 42L);

      ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
      repository.verify(() -> WebhookSubscriptionRepository.save(captor.capture()));
      assertEquals(List.of("mailing-list-member-created"), captor.getValue().getEventTypeList());
    }
  }

  @Test
  void installingAWebhookIntegrationFiltersOutAnEventTypeItDoesNotSupport() throws DataException {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      InstallIntegrationCommand.install(slack(), Map.of("webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz"),
          List.of("mailing-list-member-created", "not-a-real-event-type"), 42L);

      ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
      repository.verify(() -> WebhookSubscriptionRepository.save(captor.capture()));
      assertEquals(List.of("mailing-list-member-created"), captor.getValue().getEventTypeList());
    }
  }

  @Test
  void installingAWebhookIntegrationWithOnlyUnsupportedEventTypesThrows() {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());

      assertThrows(DataException.class,
          () -> InstallIntegrationCommand.install(slack(),
              Map.of("webhookUrl", "https://hooks.slack.com/services/T00/B00/xyz"), List.of("not-a-real-event-type"),
              42L));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }

  @Test
  void installingAWebhookIntegrationWithAnInvalidUrlThrows() {
    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of());

      assertThrows(DataException.class,
          () -> InstallIntegrationCommand.install(slack(), Map.of("webhookUrl", "not-a-url"), List.of(), 42L));
      repository.verify(() -> WebhookSubscriptionRepository.save(any()), never());
    }
  }

  @Test
  void reinstallingAWebhookIntegrationReusesAnExistingTaggedSubscriptionRatherThanCreatingASecondOne()
      throws DataException {
    // Issue #455 review: an admin can pause a registry-installed subscription via the standalone
    // webhook-subscription admin form's Disable toggle rather than uninstalling through the
    // gallery -- IntegrationStatusCommand would then report "not installed" again, and clicking
    // Install must reactivate/update that same row, not create a second tagged one.
    WebhookSubscription disabled = new WebhookSubscription();
    disabled.setId(7L);
    disabled.setUrl("https://hooks.slack.com/services/OLD");
    disabled.setEventTypeList(List.of("form-submitted"));
    disabled.setSecret("existing-secret");
    disabled.setEnabled(false);
    disabled.setIntegrationId("slack");
    disabled.setCreatedBy(1L);

    try (MockedStatic<WebhookSubscriptionRepository> repository = mockStatic(WebhookSubscriptionRepository.class)) {
      repository.when(() -> WebhookSubscriptionRepository.findByIntegrationId("slack")).thenReturn(List.of(disabled));
      repository.when(() -> WebhookSubscriptionRepository.findById(7L)).thenReturn(disabled);
      repository.when(() -> WebhookSubscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

      InstallIntegrationCommand.install(slack(), Map.of("webhookUrl", "https://hooks.slack.com/services/NEW"),
          List.of(), 42L);

      // SaveWebhookSubscriptionCommand.save() treats a non-null, non-negative id as an update: it
      // re-fetches the persisted row via findById(id) and copies fields onto THAT object rather
      // than building a fresh one, so id=7 surviving end to end is the "reused, not recreated" signal.
      repository.verify(() -> WebhookSubscriptionRepository.findById(7L));
      ArgumentCaptor<WebhookSubscription> captor = ArgumentCaptor.forClass(WebhookSubscription.class);
      repository.verify(() -> WebhookSubscriptionRepository.save(captor.capture()));
      WebhookSubscription saved = captor.getValue();
      assertEquals(7L, saved.getId());
      assertEquals("https://hooks.slack.com/services/NEW", saved.getUrl());
      assertTrue(saved.getEnabled());
      assertEquals("existing-secret", saved.getSecret(), "the existing signing secret must survive reactivation");
    }
  }
}
