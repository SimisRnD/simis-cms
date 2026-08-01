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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.events.cms.BlogPostPublishedEvent;
import com.simisinc.platform.domain.events.cms.CalendarEventRemovedEvent;
import com.simisinc.platform.domain.events.cms.CalendarEventRescheduledEvent;
import com.simisinc.platform.domain.events.cms.CalendarEventScheduledEvent;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.events.cms.UnsuspendRequestedEvent;
import com.simisinc.platform.domain.events.cms.UserAccountRestoredEvent;
import com.simisinc.platform.domain.events.cms.UserInvitedEvent;
import com.simisinc.platform.domain.events.cms.UserPasswordResetEvent;
import com.simisinc.platform.domain.events.cms.UserRegisteredEvent;
import com.simisinc.platform.domain.events.cms.UserSignedUpEvent;
import com.simisinc.platform.domain.events.cms.WebPagePublishedEvent;
import com.simisinc.platform.domain.events.cms.WebPageUpdatedEvent;
import com.simisinc.platform.domain.events.ecommerce.OrderSubmittedEvent;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.CalendarEvent;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * Builds the outbound webhook JSON payload for a domain event (issue #418): {@code {event,
 * occurredOn, deliveryId, data}}, where {@code event} is {@link Event#getDomainEventType()},
 * {@code occurredOn} is {@link Event#getOccurred()}, {@code deliveryId} is the stable UUID
 * identifying this delivery (see below), and {@code data} is a per-event-type map of that
 * event's own fields.
 *
 * <p>
 * {@code deliveryId} extends the literal 3-field {@code {event, occurredOn, data}} shape #418
 * specifies -- it exists to fold in #456's idempotency requirement ("prevent duplicate
 * processing if webhook is retried"): the same delivery is retried with the exact same payload
 * bytes (same {@code deliveryId}, so a signature computed once by the sender stays valid on
 * every retry), letting a receiver recognize a retried delivery as the same logical event rather
 * than a new one. It is present on every event type, so the payload shape is still uniform.
 * </p>
 *
 * <p>
 * {@code data} is built from an explicit, hand-picked field list per event type rather than by
 * reflecting over the event object -- an {@code Event} subclass's getters include DB-backed
 * lookups that return full domain model objects (e.g. {@code User}, which carries a password
 * hash, MFA secret, and account tokens), so serializing an event wholesale would leak far more
 * than a third-party integration should ever receive. Each branch below only exposes the fields
 * a receiver like Zapier/HubSpot/Salesforce actually needs.
 * </p>
 *
 * @author SimIS Inc.
 */
public class BuildWebhookPayloadCommand {

  private static final Log LOG = LogFactory.getLog(BuildWebhookPayloadCommand.class);

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  /** The full {@code {event, occurredOn, deliveryId, data}} JSON payload for the given event. */
  public static String build(Event event, String deliveryId) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("event", event.getDomainEventType());
    root.put("occurredOn", event.getOccurred());
    root.put("deliveryId", deliveryId);
    root.put("data", buildData(event));
    try {
      return OBJECT_MAPPER.writeValueAsString(root);
    } catch (Exception e) {
      LOG.error("Could not serialize webhook payload for event: " + event.getDomainEventType(), e);
      return null;
    }
  }

  /** Just the {@code data} portion, exposed separately so tests can assert on field shape directly. */
  public static Map<String, Object> buildData(Event event) {
    Map<String, Object> data = new LinkedHashMap<>();
    if (event instanceof BlogPostPublishedEvent blogPostPublishedEvent) {
      data.put("blogPostId", blogPostPublishedEvent.getBlogPostId());
      if (blogPostPublishedEvent.getBlogPost() != null) {
        data.put("title", blogPostPublishedEvent.getBlogPost().getTitle());
        data.put("summary", blogPostPublishedEvent.getBlogPost().getSummary());
      }
      data.put("user", userSummary(blogPostPublishedEvent.getUser()));
    } else if (event instanceof CalendarEventScheduledEvent calendarEventScheduledEvent) {
      putCalendarEvent(data, calendarEventScheduledEvent.getCalendarEvent());
      data.put("user", userSummary(calendarEventScheduledEvent.getUser()));
    } else if (event instanceof CalendarEventRescheduledEvent calendarEventRescheduledEvent) {
      putCalendarEvent(data, calendarEventRescheduledEvent.getCalendarEvent());
      data.put("user", userSummary(calendarEventRescheduledEvent.getUser()));
    } else if (event instanceof CalendarEventRemovedEvent calendarEventRemovedEvent) {
      putCalendarEvent(data, calendarEventRemovedEvent.getCalendarEvent());
      data.put("user", userSummary(calendarEventRemovedEvent.getUser()));
    } else if (event instanceof FormSubmittedEvent formSubmittedEvent) {
      data.put("formId", formSubmittedEvent.getFormId());
      data.put("generatedId", formSubmittedEvent.getGeneratedId());
      data.put("location", formSubmittedEvent.getLocation());
      FormData formData = formSubmittedEvent.getFormData();
      if (formData != null) {
        data.put("formUniqueId", formData.getFormUniqueId());
        data.put("fields", formFields(formData));
      }
    } else if (event instanceof OrderSubmittedEvent orderSubmittedEvent) {
      data.put("orderId", orderSubmittedEvent.getOrder() != null ? orderSubmittedEvent.getOrder().getId() : null);
      if (orderSubmittedEvent.getOrder() != null) {
        data.put("uniqueId", orderSubmittedEvent.getOrder().getUniqueId());
        data.put("email", orderSubmittedEvent.getOrder().getEmail());
        data.put("totalItems", orderSubmittedEvent.getOrder().getTotalItems());
        data.put("subtotalAmount", orderSubmittedEvent.getOrder().getSubtotalAmount());
        data.put("currency", orderSubmittedEvent.getOrder().getCurrency());
      }
      data.put("location", orderSubmittedEvent.getLocation());
    } else if (event instanceof WebPagePublishedEvent webPagePublishedEvent) {
      putWebPage(data, webPagePublishedEvent.getWebPage(), webPagePublishedEvent.getTitle());
      data.put("user", userSummary(webPagePublishedEvent.getUser()));
    } else if (event instanceof WebPageUpdatedEvent webPageUpdatedEvent) {
      putWebPage(data, webPageUpdatedEvent.getWebPage(), webPageUpdatedEvent.getTitle());
      data.put("user", userSummary(webPageUpdatedEvent.getUser()));
    } else if (event instanceof UserSignedUpEvent userSignedUpEvent) {
      data.put("user", userSummary(userSignedUpEvent.getUser()));
    } else if (event instanceof UserPasswordResetEvent userPasswordResetEvent) {
      data.put("user", userSummary(userPasswordResetEvent.getUser()));
      data.put("resetBy", userSummary(userPasswordResetEvent.getResetBy()));
    } else if (event instanceof UserRegisteredEvent userRegisteredEvent) {
      data.put("user", userSummary(userRegisteredEvent.getUser()));
      data.put("ipAddress", userRegisteredEvent.getIpAddress());
      data.put("location", userRegisteredEvent.getLocation());
    } else if (event instanceof UserInvitedEvent userInvitedEvent) {
      data.put("user", userSummary(userInvitedEvent.getUser()));
      data.put("invitedBy", userSummary(userInvitedEvent.getInvitedBy()));
    } else if (event instanceof UnsuspendRequestedEvent unsuspendRequestedEvent) {
      data.put("target", userSummary(unsuspendRequestedEvent.getTarget()));
      data.put("requestedBy", userSummary(unsuspendRequestedEvent.getRequestedBy()));
      data.put("reason", unsuspendRequestedEvent.getReason());
    } else if (event instanceof UserAccountRestoredEvent userAccountRestoredEvent) {
      data.put("user", userSummary(userAccountRestoredEvent.getUser()));
      data.put("approvedBy", userSummary(userAccountRestoredEvent.getApprovedBy()));
    } else {
      LOG.warn("No webhook payload mapping for event type: " + event.getClass().getName()
          + " -- delivering with an empty data object rather than failing the dispatch");
    }
    return data;
  }

  private static void putCalendarEvent(Map<String, Object> data, CalendarEvent calendarEvent) {
    if (calendarEvent == null) {
      return;
    }
    data.put("calendarEventId", calendarEvent.getId());
    data.put("title", calendarEvent.getTitle());
    data.put("startDate", calendarEvent.getStartDate());
    data.put("endDate", calendarEvent.getEndDate());
    data.put("location", calendarEvent.getLocation());
  }

  private static void putWebPage(Map<String, Object> data,
      com.simisinc.platform.domain.model.cms.WebPage webPage, String title) {
    if (webPage == null) {
      return;
    }
    data.put("webPageId", webPage.getId());
    data.put("link", webPage.getLink());
    data.put("title", title);
  }

  private static java.util.List<Map<String, String>> formFields(FormData formData) {
    java.util.List<Map<String, String>> fields = new java.util.ArrayList<>();
    if (formData.getFormFieldList() == null) {
      return fields;
    }
    for (FormField field : formData.getFormFieldList()) {
      Map<String, String> fieldMap = new LinkedHashMap<>();
      fieldMap.put("label", field.getLabel());
      fieldMap.put("name", field.getName());
      fieldMap.put("value", field.getUserValue());
      fields.add(fieldMap);
    }
    return fields;
  }

  private static Map<String, Object> userSummary(User user) {
    if (user == null) {
      return null;
    }
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("id", user.getId());
    summary.put("username", user.getUsername());
    summary.put("email", user.getEmail());
    summary.put("fullName", user.getFullName());
    return summary;
  }
}
