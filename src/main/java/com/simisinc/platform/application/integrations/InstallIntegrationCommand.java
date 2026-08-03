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
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.webhooks.SaveWebhookSubscriptionCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;
import com.simisinc.platform.domain.model.webhooks.WebhookSubscription;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.webhooks.WebhookSubscriptionRepository;

/**
 * Installs an {@link IntegrationDefinition} from the gallery's install form (issue #455).
 *
 * <p>
 * {@code API_KEY} integrations save their credential fields to the {@code site_properties} rows
 * their existing per-vendor command already reads -- e.g. installing ZeroBounce writes exactly the
 * {@code mailing-list.zerobounce.apiKey} row {@code ZeroBounceApiClientCommand} has always read, so
 * installing from the gallery and configuring from that integration's own settings page are two
 * doors onto the same value. This deliberately only ever updates a pre-seeded row (mirroring
 * {@code SitePropertiesEditorWidget}'s own contract) rather than inserting a new one --
 * {@link SitePropertyRepository#save} has no insert path, by design, so every real property this
 * codebase supports already has a seeded row from an install/upgrade migration.
 * </p>
 *
 * <p>
 * {@code WEBHOOK_URL} integrations (e.g. Slack) have no {@code site_properties} row at all -- their
 * one credential field IS a {@code webhook_subscription}'s url, so installing delegates to
 * {@link SaveWebhookSubscriptionCommand}, tagging the new row with this integration's id so
 * {@link UninstallIntegrationCommand} can find it again.
 * </p>
 */
public class InstallIntegrationCommand {

  private InstallIntegrationCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param definition the integration being installed
   * @param credentialValues form field name -&gt; submitted value, keyed by {@link CredentialField#getName()}
   * @param eventTypeIds {@code WEBHOOK_URL} only: the event types to subscribe to; falls back to
   *        {@link IntegrationDefinition#getDefaultEventTypeIds()} when empty
   * @param userId the acting admin, recorded as the credential/subscription's modifier
   * @throws DataException when a required field is missing/invalid
   */
  public static void install(IntegrationDefinition definition, Map<String, String> credentialValues,
      List<String> eventTypeIds, long userId) throws DataException {
    switch (definition.getAuthType()) {
      case API_KEY -> installApiKey(definition, credentialValues, userId);
      case WEBHOOK_URL -> installWebhook(definition, credentialValues, eventTypeIds, userId);
      case OAUTH -> throw new DataException(definition.getName() + " cannot be installed yet (OAuth is not supported).");
    }
  }

  private static void installApiKey(IntegrationDefinition definition, Map<String, String> credentialValues,
      long userId) throws DataException {
    StringBuilder errorMessages = new StringBuilder();
    for (CredentialField field : definition.getCredentialFields()) {
      if (StringUtils.isBlank(credentialValues.get(field.getName()))) {
        errorMessages.append(field.getLabel()).append(" is required. ");
      }
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages);
    }

    List<SiteProperty> properties = new ArrayList<>();
    Set<String> changedPropertyNames = new HashSet<>();
    for (CredentialField field : definition.getCredentialFields()) {
      String propertyName = definition.getSitePropertyPrefix() + "." + field.getName();
      SiteProperty property = SitePropertyRepository.findByName(propertyName);
      if (property == null) {
        // Every credential field this registry currently declares has a pre-seeded row (see class
        // javadoc) -- reaching this means a registry entry was added without seeding its property.
        throw new DataException(
            definition.getName() + " is missing its " + field.getLabel() + " configuration and cannot be "
                + "installed. Contact an administrator.");
      }
      property.setValue(credentialValues.get(field.getName()).trim());
      properties.add(property);
      changedPropertyNames.add(propertyName);
    }
    // saveAll(), not save() per-property: it's the one SitePropertyRepository entry point that
    // also invalidates CacheManager's SYSTEM_PROPERTY_PREFIX_CACHE for this prefix (issue #455
    // review) -- calling save() directly, as an earlier version of this method did, left a stale
    // cached value in place (that cache has no configured TTL) even though the DB row and this
    // integration's "Installed" badge both correctly showed the new value.
    if (!SitePropertyRepository.saveAll(definition.getSitePropertyPrefix(), properties, userId, changedPropertyNames)) {
      throw new DataException("Your information could not be saved due to a system error. Please try again.");
    }
  }

  private static void installWebhook(IntegrationDefinition definition, Map<String, String> credentialValues,
      List<String> eventTypeIds, long userId) throws DataException {
    CredentialField urlField = definition.getCredentialFields().get(0);
    String url = credentialValues.get(urlField.getName());

    List<String> requestedEventTypeIds =
        eventTypeIds == null || eventTypeIds.isEmpty() ? definition.getDefaultEventTypeIds() : eventTypeIds;
    List<String> eventTypes =
        requestedEventTypeIds.stream().filter(definition.getSupportedEventTypeIds()::contains).toList();
    if (eventTypes.isEmpty()) {
      throw new DataException("At least one event type must be selected.");
    }

    // Issue #455 review: reuse an already-tagged subscription (e.g. one an admin paused via the
    // standalone webhook-subscription admin form's Disable toggle, rather than uninstalling this
    // integration) instead of always inserting a new one -- otherwise re-installing after a manual
    // disable creates a second, independent tagged row, and a matching site event gets delivered
    // twice once both happen to be enabled at once.
    List<WebhookSubscription> existing = WebhookSubscriptionRepository.findByIntegrationId(definition.getId());
    WebhookSubscription bean = existing.isEmpty() ? new WebhookSubscription() : existing.get(0);
    bean.setUrl(url == null ? null : url.trim());
    bean.setEventTypeList(eventTypes);
    bean.setEnabled(true);
    bean.setIntegrationId(definition.getId());
    if (existing.isEmpty()) {
      bean.setCreatedBy(userId);
    }
    bean.setModifiedBy(userId);
    // Validates the url, generates the subscription's signing secret on create, and persists --
    // same command the standalone webhook-subscription admin form uses.
    SaveWebhookSubscriptionCommand.save(bean);
  }
}
