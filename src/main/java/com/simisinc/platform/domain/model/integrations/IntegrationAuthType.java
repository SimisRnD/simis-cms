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
 * How an {@link IntegrationDefinition} authenticates, and therefore how installing it stores its
 * credential (issue #455).
 */
public enum IntegrationAuthType {

  /** One or more secret/plain fields saved as {@code site_properties} under the integration's prefix. */
  API_KEY,

  /**
   * A single URL that is itself the bearer credential (e.g. a Slack incoming webhook) -- installing
   * creates a {@code webhook_subscription} row rather than a site property.
   */
  WEBHOOK_URL,

  /**
   * A 3-legged OAuth token exchange. No {@link IntegrationDefinition} in this codebase uses this yet
   * (issue #455's own audit found no current integration needs it) -- the value exists so the
   * registry's data model and gallery UI don't need reshaping the day one does.
   */
  OAUTH
}
