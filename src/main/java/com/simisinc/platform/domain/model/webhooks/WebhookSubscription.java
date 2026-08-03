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

package com.simisinc.platform.domain.model.webhooks;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.Entity;

/**
 * An external HTTP endpoint subscribed to one or more domain events (issue #418). Delivery is
 * this class's own concern (see {@code WebhookDelivery}); creating/editing subscriptions through
 * an admin UI is issue #453 -- this model only carries what the delivery engine needs.
 *
 * <p>
 * {@code eventTypes} is a comma-separated list of {@code Event.getDomainEventType()} ids (e.g.
 * {@code "web-page-published,blog-post-published"}) rather than a separate join table --
 * subscriptions are expected to be admin-managed and few, so matching is done in Java
 * ({@link #matchesEventType(String)}) rather than in SQL.
 * </p>
 *
 * @author SimIS Inc.
 */
public class WebhookSubscription extends Entity {

  private Long id = -1L;
  private String url = null;
  private String eventTypes = null;
  private String secret = null;
  private boolean enabled = true;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;
  // Issue #455: set when this subscription was created by the integration registry's one-click
  // install (e.g. "slack") rather than the standalone webhook-subscription admin form, so
  // uninstalling that integration can find and remove exactly the rows it created and no others.
  private String integrationId = null;

  public WebhookSubscription() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getEventTypes() {
    return eventTypes;
  }

  public void setEventTypes(String eventTypes) {
    this.eventTypes = eventTypes;
  }

  /** Convenience form of {@link #setEventTypes(String)} for callers building a subscription from a list. */
  public void setEventTypeList(List<String> eventTypeList) {
    this.eventTypes = eventTypeList == null ? null : String.join(",", eventTypeList);
  }

  /** The subscribed event type ids, trimmed and with blanks dropped. Never null. */
  public List<String> getEventTypeList() {
    List<String> result = new ArrayList<>();
    if (StringUtils.isBlank(eventTypes)) {
      return result;
    }
    for (String type : eventTypes.split(",")) {
      String trimmed = type.trim();
      if (StringUtils.isNotBlank(trimmed)) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /** True when this subscription's event type list contains {@code eventType} exactly. */
  public boolean matchesEventType(String eventType) {
    if (StringUtils.isBlank(eventType)) {
      return false;
    }
    for (String type : getEventTypeList()) {
      if (type.equalsIgnoreCase(eventType)) {
        return true;
      }
    }
    return false;
  }

  public String getSecret() {
    return secret;
  }

  public void setSecret(String secret) {
    this.secret = secret;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }

  /** @return the registry integration id that created this subscription (e.g. "slack"), or null for a manual one */
  public String getIntegrationId() {
    return integrationId;
  }

  public void setIntegrationId(String integrationId) {
    this.integrationId = integrationId;
  }
}
