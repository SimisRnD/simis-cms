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

package com.simisinc.platform.presentation.widgets.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.application.admin.SitePropertySettingsPageCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Read-only overview of every integration credential the platform knows how to encrypt (issue
 * #454) -- a single Settings &gt; Integrations hub, since today these are scattered one-per-page
 * across ~14 separate settings screens (BI, e-learning, mailing list, captcha, etc.) with no way
 * to see at a glance what's set, who last rotated it, or whether it's about to expire.
 *
 * <p>This widget does not itself add/update/rotate a secret -- that already happens on each
 * integration's own settings page via {@link SitePropertiesEditorWidget}, which is where the
 * AES-256 encryption ({@code SecretCryptoCommand}) and masked-field handling already live. The
 * hub links out to those pages rather than re-implementing per-integration edit forms, except for
 * the payment secrets that are deliberately {@code property_type=disabled} (provisioned directly
 * in the database, not editable via any admin form -- see {@code
 * V20260719_1004__reencrypt_secret_properties}'s javadoc) and {@code oauth.clientSecret}, which
 * has no admin editor at all yet; both are surfaced as unmanaged rather than linked.
 *
 * @author SimIS Inc.
 */
public class IntegrationsHubWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/integrations-hub.jsp";

  /** How many days out an expiry counts as "expiring soon" rather than just "ok". */
  static final long EXPIRING_SOON_DAYS = 30;

  public static class SecretStatus {
    private final String name;
    private final String label;
    private final String prefix;
    private final boolean set;
    private final boolean disabled;
    private final String pageUrl;
    private final Timestamp modified;
    private final String modifiedByName;
    private final Timestamp expiresAt;
    private final String expiryStatus;

    SecretStatus(String name, String label, String prefix, boolean set, boolean disabled, String pageUrl,
        Timestamp modified, String modifiedByName, Timestamp expiresAt, String expiryStatus) {
      this.name = name;
      this.label = label;
      this.prefix = prefix;
      this.set = set;
      this.disabled = disabled;
      this.pageUrl = pageUrl;
      this.modified = modified;
      this.modifiedByName = modifiedByName;
      this.expiresAt = expiresAt;
      this.expiryStatus = expiryStatus;
    }

    public String getName() {
      return name;
    }

    public String getLabel() {
      return label;
    }

    public String getPrefix() {
      return prefix;
    }

    public boolean isSet() {
      return set;
    }

    public boolean isDisabled() {
      return disabled;
    }

    /** @return the settings page that owns this secret, or null when there is no admin editor for it */
    public String getPageUrl() {
      return pageUrl;
    }

    public Timestamp getModified() {
      return modified;
    }

    /** @return the display name of the last user to rotate this secret through the admin UI, or null */
    public String getModifiedByName() {
      return modifiedByName;
    }

    public Timestamp getExpiresAt() {
      return expiresAt;
    }

    /** @return "none", "ok", "expiring-soon", or "expired" */
    public String getExpiryStatus() {
      return expiryStatus;
    }
  }

  public WidgetContext execute(WidgetContext context) {
    List<SecretStatus> secretStatusList = new ArrayList<>();
    for (String name : SecretSitePropertiesCommand.getSecretPropertyNames()) {
      SiteProperty siteProperty = SitePropertyRepository.findByName(name);
      secretStatusList.add(toSecretStatus(name, siteProperty));
    }
    secretStatusList.sort((a, b) -> a.getName().compareTo(b.getName()));

    context.getRequest().setAttribute("secretStatusList", secretStatusList);
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  private SecretStatus toSecretStatus(String name, SiteProperty siteProperty) {
    String prefix = SitePropertySettingsPageCommand.rootPrefixOf(name);
    String label = siteProperty != null && siteProperty.getLabel() != null ? siteProperty.getLabel() : name;
    boolean set = siteProperty != null && siteProperty.getValue() != null && !siteProperty.getValue().isEmpty();
    boolean disabled = siteProperty != null && "disabled".equals(siteProperty.getType());
    String pageUrl = disabled ? null : SitePropertySettingsPageCommand.findPageUrl(prefix);

    String modifiedByName = null;
    long modifiedBy = siteProperty != null ? siteProperty.getModifiedBy() : -1;
    if (modifiedBy > -1) {
      User modifier = UserRepository.findByUserId(modifiedBy);
      if (modifier != null) {
        modifiedByName = modifier.getFullName();
      }
    }

    Timestamp expiresAt = siteProperty != null ? siteProperty.getExpiresAt() : null;
    String expiryStatus = expiryStatusOf(expiresAt);

    return new SecretStatus(name, label, prefix, set, disabled, pageUrl,
        siteProperty != null ? siteProperty.getModified() : null, modifiedByName, expiresAt, expiryStatus);
  }

  private String expiryStatusOf(Timestamp expiresAt) {
    if (expiresAt == null) {
      return "none";
    }
    Instant now = Instant.now();
    if (expiresAt.toInstant().isBefore(now)) {
      return "expired";
    }
    if (ChronoUnit.DAYS.between(now, expiresAt.toInstant()) <= EXPIRING_SOON_DAYS) {
      return "expiring-soon";
    }
    return "ok";
  }
}
