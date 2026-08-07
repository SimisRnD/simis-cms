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

package com.simisinc.platform.application.webhooks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The hand-maintained list of domain event type ids a {@code webhook_subscription} can subscribe
 * to (issue #453), with a human-readable label for each -- there is no {@code Event} description
 * field to source labels from, and no authoritative enum of event type ids exists (each {@code
 * Event} subclass just declares its own {@code public static final String ID}). This list was
 * built by enumerating every class under {@code com.simisinc.platform.domain.events.**} and
 * cross-checked against {@code BuildWebhookPayloadCommand}'s {@code instanceof} chain, which
 * handles the identical 18 types (any addition/removal there means this list is stale too).
 *
 * <p>
 * Deliberately excludes {@code item-file-uploaded}: it is referenced in {@code
 * WEB-INF/workflows/item-workflows.yml} but has no matching {@code Event} subclass anywhere in
 * {@code src/main/java}, so it can never actually fire -- including it here would be a checkbox
 * that silently never delivers.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookEventTypeCommand {

  /** One subscribable event type: its {@code Event#getDomainEventType()} id and an admin-facing label. */
  public static final class WebhookEventType {
    private final String id;
    private final String label;

    WebhookEventType(String id, String label) {
      this.id = id;
      this.label = label;
    }

    public String getId() {
      return id;
    }

    public String getLabel() {
      return label;
    }
  }

  private static final List<WebhookEventType> EVENT_TYPES = Collections.unmodifiableList(new ArrayList<>(List.of(
      new WebhookEventType("web-page-published", "Web page published"),
      new WebhookEventType("web-page-updated", "Web page updated"),
      new WebhookEventType("blog-post-published", "Blog post published"),
      new WebhookEventType("calendar-event-scheduled", "Calendar event scheduled"),
      new WebhookEventType("calendar-event-rescheduled", "Calendar event rescheduled"),
      new WebhookEventType("calendar-event-removed", "Calendar event removed"),
      new WebhookEventType("form-submitted", "Form submitted"),
      new WebhookEventType("order-submitted", "E-commerce order submitted"),
      new WebhookEventType("user-signed-up", "User signed up (self-registration)"),
      new WebhookEventType("user-registered", "User registered"),
      new WebhookEventType("user-invited", "User invited by an admin"),
      new WebhookEventType("user-password-reset", "User password reset"),
      new WebhookEventType("unsuspend-requested", "Account unsuspend requested"),
      new WebhookEventType("user-account-restored", "User account restored"),
      new WebhookEventType("mailing-list-member-created", "Mailing list member subscribed"),
      new WebhookEventType("mailing-list-member-updated", "Mailing list member subscription changed"),
      new WebhookEventType("mailing-list-member-deleted", "Mailing list member removed"),
      new WebhookEventType("mailing-list-member-confirmation-requested", "Mailing list member confirmation requested"))));

  private WebhookEventTypeCommand() {
    // Static utility, not instantiated
  }

  /** @return every subscribable event type, in a stable display order */
  public static List<WebhookEventType> getAll() {
    return EVENT_TYPES;
  }

  /** @return the admin-facing label for an event type id, or the id itself if it is unrecognized */
  public static String getLabel(String eventTypeId) {
    for (WebhookEventType type : EVENT_TYPES) {
      if (type.getId().equals(eventTypeId)) {
        return type.getLabel();
      }
    }
    return eventTypeId;
  }
}
