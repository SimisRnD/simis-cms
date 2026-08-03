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

/**
 * One field an {@link IntegrationDefinition}'s install form collects (issue #455). {@code name} is
 * the form field name; for an {@link IntegrationAuthType#API_KEY} integration it is also the
 * {@code site_properties} suffix appended to {@link IntegrationDefinition#getSitePropertyPrefix()}.
 */
public class CredentialField {

  private final String name;
  private final String label;
  private final boolean secret;
  private final String helpText;

  public CredentialField(String name, String label, boolean secret, String helpText) {
    this.name = name;
    this.label = label;
    this.secret = secret;
    this.helpText = helpText;
  }

  public String getName() {
    return name;
  }

  public String getLabel() {
    return label;
  }

  /** @return true when this field's value should be masked in the UI and encrypted at rest */
  public boolean isSecret() {
    return secret;
  }

  /** @return short guidance shown under the field (e.g. where to find this credential), or null */
  public String getHelpText() {
    return helpText;
  }
}
