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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.simisinc.platform.application.webhooks.WebhookEventTypeCommand;
import com.simisinc.platform.domain.model.integrations.CredentialField;
import com.simisinc.platform.domain.model.integrations.IntegrationAuthType;
import com.simisinc.platform.domain.model.integrations.IntegrationDefinition;

/**
 * The hand-maintained catalog of integrations shown in the Settings &gt; Integrations gallery
 * (issue #455), mirroring {@link WebhookEventTypeCommand}'s pattern for the same reason: there is
 * no way to add a real integration purely through data, so the registry itself is a list in code,
 * not a database table.
 *
 * <p>
 * ZeroBounce was already shipped (issue #574) before this registry existed -- its entry here
 * points at the exact same {@code mailing-list.zerobounce.apiKey} site property that integration
 * already reads, so installing it from the gallery and configuring it from the existing Mailing
 * List settings page are two doors onto the same value, not competing storage.
 * </p>
 */
public class IntegrationRegistryCommand {

  private static final List<IntegrationDefinition> INTEGRATIONS = Collections.unmodifiableList(new ArrayList<>(
      List.of(
          new IntegrationDefinition(
              "zerobounce",
              "ZeroBounce",
              "Validates email addresses before they're added to a mailing list, catching typos and disposable/risky addresses.",
              "fa-envelope-circle-check",
              "https://www.zerobounce.net",
              "https://www.zerobounce.net/docs/email-validation-api-quickstart/",
              IntegrationAuthType.API_KEY,
              List.of(new CredentialField("apiKey", "API Key", true,
                  "Found under Settings on your ZeroBounce dashboard.")),
              "mailing-list.zerobounce",
              null,
              null),
          new IntegrationDefinition(
              "slack",
              "Slack",
              "Posts a message to a Slack channel when selected site events happen, using an incoming webhook.",
              "fa-slack",
              "https://slack.com",
              "https://api.slack.com/messaging/webhooks",
              IntegrationAuthType.WEBHOOK_URL,
              List.of(new CredentialField("webhookUrl", "Incoming Webhook URL", true,
                  "Create one at https://api.slack.com/messaging/webhooks for the channel you want notified. "
                      + "This URL is itself the credential -- anyone with it can post to that channel.")),
              null,
              WebhookEventTypeCommand.getAll().stream().map(WebhookEventTypeCommand.WebhookEventType::getId).toList(),
              List.of("form-submitted", "order-submitted", "user-signed-up")))));

  private IntegrationRegistryCommand() {
    // Static utility, not instantiated
  }

  /** @return every registered integration, in a stable display order */
  public static List<IntegrationDefinition> getAll() {
    return INTEGRATIONS;
  }

  public static Optional<IntegrationDefinition> findById(String id) {
    return INTEGRATIONS.stream().filter(def -> def.getId().equals(id)).findFirst();
  }
}
