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

package com.simisinc.platform.domain.model.integrations;

import java.util.Collections;
import java.util.List;

/**
 * One entry in the third-party integration registry (issue #455): a discoverable, installable
 * integration shown as a card in the Settings &gt; Integrations gallery. This is a hand-maintained
 * catalog entry, not a persisted record -- see {@code IntegrationRegistryCommand} -- because
 * adding a real integration always requires real Java code (an API client, a webhook target) that
 * cannot be supplied purely by configuration.
 *
 * <p>Whether an integration is "installed" is never stored on this class: it is computed from
 * whatever backs its {@link IntegrationAuthType} (site property values for {@code API_KEY}, a
 * tagged {@code webhook_subscription} row for {@code WEBHOOK_URL}) -- see {@code
 * IntegrationStatusCommand}.
 */
public class IntegrationDefinition {

  private final String id;
  private final String name;
  private final String description;
  private final String iconClass;
  private final String websiteUrl;
  private final String docsUrl;
  private final IntegrationAuthType authType;
  private final List<CredentialField> credentialFields;

  /** {@code API_KEY} only: the {@code site_properties} prefix each credential field's name is appended to. */
  private final String sitePropertyPrefix;

  /** {@code WEBHOOK_URL} only: which {@code WebhookEventTypeCommand} ids this integration can be notified of. */
  private final List<String> supportedEventTypeIds;

  /** {@code WEBHOOK_URL} only: the subset of {@link #supportedEventTypeIds} pre-checked on install. */
  private final List<String> defaultEventTypeIds;

  public IntegrationDefinition(String id, String name, String description, String iconClass, String websiteUrl,
      String docsUrl, IntegrationAuthType authType, List<CredentialField> credentialFields,
      String sitePropertyPrefix, List<String> supportedEventTypeIds, List<String> defaultEventTypeIds) {
    this.id = id;
    this.name = name;
    this.description = description;
    this.iconClass = iconClass;
    this.websiteUrl = websiteUrl;
    this.docsUrl = docsUrl;
    this.authType = authType;
    this.credentialFields = credentialFields == null ? List.of() : Collections.unmodifiableList(credentialFields);
    this.sitePropertyPrefix = sitePropertyPrefix;
    this.supportedEventTypeIds =
        supportedEventTypeIds == null ? List.of() : Collections.unmodifiableList(supportedEventTypeIds);
    this.defaultEventTypeIds =
        defaultEventTypeIds == null ? List.of() : Collections.unmodifiableList(defaultEventTypeIds);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getIconClass() {
    return iconClass;
  }

  public String getWebsiteUrl() {
    return websiteUrl;
  }

  public String getDocsUrl() {
    return docsUrl;
  }

  public IntegrationAuthType getAuthType() {
    return authType;
  }

  public List<CredentialField> getCredentialFields() {
    return credentialFields;
  }

  public String getSitePropertyPrefix() {
    return sitePropertyPrefix;
  }

  public List<String> getSupportedEventTypeIds() {
    return supportedEventTypeIds;
  }

  public List<String> getDefaultEventTypeIds() {
    return defaultEventTypeIds;
  }
}
