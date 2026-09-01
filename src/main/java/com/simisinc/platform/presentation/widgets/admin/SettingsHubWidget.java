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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * A grouped landing page for the admin settings screens (issue #1765).
 *
 * <p>The Settings section of the admin menu had grown to 18 links -- the largest of eight sections
 * and the last one, so reaching it meant scrolling past everything else. But the count was the
 * symptom. A flat menu gives a name and nothing else, and several of these names do not tell an
 * admin what is behind them: given "Site Settings", "Security", "Feature Flags" and "Captcha
 * Settings", there is no way to know from the menu which one holds a particular option, so you open
 * them in turn. Shortening the list does not fix that; only somewhere with room for a description
 * does.
 *
 * <p><b>Module settings are listed even when their module is switched off</b>, marked rather than
 * hidden. That is deliberate and is the whole reason this page unblocks the rest of #1763: e-learning
 * and BI settings pages each render their own {@code enabled} checkbox, so hiding them from the nav
 * when the module is off would leave an admin no route back to switch it on. A stable page that
 * always lists them is that route. Hiding them here too would recreate the trap one level up.
 *
 * @author SimIS Inc.
 */
public class SettingsHubWidget extends GenericWidget {

  static final long serialVersionUID = 1L;

  static String JSP = "/admin/settings-hub.jsp";

  /** One settings destination. */
  public static class SettingsEntry {
    private final String label;
    private final String link;
    private final String icon;
    private final String description;
    private final String enabledProperty;
    private boolean moduleEnabled = true;

    SettingsEntry(String label, String link, String icon, String description, String enabledProperty) {
      this.label = label;
      this.link = link;
      this.icon = icon;
      this.description = description;
      this.enabledProperty = enabledProperty;
    }

    public String getLabel() {
      return label;
    }

    public String getLink() {
      return link;
    }

    public String getIcon() {
      return icon;
    }

    public String getDescription() {
      return description;
    }

    public String getEnabledProperty() {
      return enabledProperty;
    }

    /** False only for a module entry whose module is switched off; always true for everything else. */
    public boolean getModuleEnabled() {
      return moduleEnabled;
    }

    /** True when this entry belongs to a module that can be switched off. */
    public boolean getBelongsToModule() {
      return enabledProperty != null;
    }
  }

  /** A titled group of destinations. */
  public static class SettingsGroup {
    private final String title;
    private final String description;
    private final List<SettingsEntry> entryList;

    SettingsGroup(String title, String description, List<SettingsEntry> entryList) {
      this.title = title;
      this.description = description;
      this.entryList = entryList;
    }

    public String getTitle() {
      return title;
    }

    public String getDescription() {
      return description;
    }

    public List<SettingsEntry> getEntryList() {
      return entryList;
    }
  }

  private static SettingsEntry entry(String label, String link, String icon, String description) {
    return new SettingsEntry(label, link, icon, description, null);
  }

  private static SettingsEntry moduleEntry(String label, String link, String icon, String description,
      String enabledProperty) {
    return new SettingsEntry(label, link, icon, description, enabledProperty);
  }

  /**
   * Every destination the Settings menu used to list, grouped.
   *
   * <p>Kept here rather than in the layout XML because a card needs a description, and the
   * descriptions are the reason this page exists -- a name alone is what the menu already had.
   * The trade-off is that adding a settings page means adding it in two places, this list and
   * {@code main.jsp}; {@code SettingsHubWidgetTest} asserts the two agree so the drift is caught
   * rather than discovered.
   */
  static final List<SettingsGroup> SETTINGS_GROUPS = Collections.unmodifiableList(Arrays.asList(
      new SettingsGroup("Appearance & Site", "How the site looks and identifies itself", Arrays.asList(
          entry("Theme", "/admin/theme-properties", "fa-palette",
              "Colours, logo, fonts, and the light/dark mode visitors get."),
          entry("Site Settings", "/admin/site-properties", "fa-rocket",
              "Site name, URL, timezone, whether the site is online, and registration and login rules."),
          entry("Social Media", "/admin/social-media-settings", "fa-thumbs-up",
              "The social profile links shown in the header and footer."))),

      new SettingsGroup("Access & Security", "Who can get in, and how", Arrays.asList(
          entry("Security", "/admin/security-properties", "fa-shield",
              "Password rules, session lifetime, and the security headers sent with every response."),
          entry("MFA Settings", "/admin/mfa-properties", "fa-lock",
              "Whether multi-factor authentication is required, and for which roles."),
          entry("Single Sign-On", "/admin/sso-properties", "fa-id-badge",
              "Sign-in through an external identity provider, and how its accounts map to roles."),
          entry("Captcha Settings", "/admin/captcha-properties", "fa-key",
              "Which captcha guards public forms, and the keys it uses."))),

      new SettingsGroup("Analytics & Monitoring", "What the site measures about itself", Arrays.asList(
          entry("Analytics Settings", "/admin/configure-analytics", "fa-chart-line",
              "The analytics service and tracking ids used on public pages."),
          entry("Feature Flags", "/admin/feature-flags", "fa-flag",
              "Turn individual platform features on or off without a deploy."))),

      new SettingsGroup("Communications", "Mail the site sends", Arrays.asList(
          entry("Email Settings", "/admin/mail-properties", "fa-cogs",
              "The SMTP relay, sender address, and TLS settings used for every outgoing email."),
          entry("Mailing List Settings", "/admin/mailing-list-properties", "fa-envelope",
              "Subscription confirmation, unsubscribe handling, and newsletter defaults."))),

      new SettingsGroup("Integrations & Data", "Connections to other systems", Arrays.asList(
          entry("Integrations", "/admin/integrations", "fa-puzzle-piece",
              "Every stored integration credential, when it was last rotated, and what is unset."),
          entry("Webhooks", "/admin/webhooks", "fa-plug",
              "Outbound HTTP callbacks fired when things change on the site."),
          entry("BI Settings", "/admin/bi-properties", "fa-table-columns",
              "The Superset connection used for embedded dashboards."),
          entry("Maps Settings", "/admin/maps-properties", "fa-map",
              "The map tile provider and geocoding service used by map widgets."))),

      new SettingsGroup("Modules", "Optional features, each switched on or off in its own settings",
          Arrays.asList(
              moduleEntry("E-commerce Settings", "/admin/ecommerce-properties", "fa-shopping-cart",
                  "Payment processing, tax, and shipping for the store.", "ecommerce.enabled"),
              moduleEntry("E-learning Settings", "/admin/elearning-properties", "fa-chalkboard-teacher",
                  "The learning record store and course platform connection.", "elearning.enabled"),
              moduleEntry("xAPI Statements", "/admin/elearning-statements", "fa-list",
                  "The learning activity records received from the LRS.", "elearning.enabled")))));

  public WidgetContext execute(WidgetContext context) {

    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));

    // Cache-backed (CacheManager), so these are map builds rather than queries
    Map<String, String> ecommercePropertyMap = LoadSitePropertyCommand.loadAsMap("ecommerce");
    Map<String, String> elearningPropertyMap = LoadSitePropertyCommand.loadAsMap("elearning");

    List<SettingsGroup> groupList = new ArrayList<>();
    for (SettingsGroup group : SETTINGS_GROUPS) {
      List<SettingsEntry> entryList = new ArrayList<>();
      for (SettingsEntry entry : group.getEntryList()) {
        // Copy, because SETTINGS_GROUPS is shared across every request and moduleEnabled is per-site
        SettingsEntry thisEntry = new SettingsEntry(entry.getLabel(), entry.getLink(), entry.getIcon(),
            entry.getDescription(), entry.getEnabledProperty());
        thisEntry.moduleEnabled = isModuleEnabled(entry.getEnabledProperty(), ecommercePropertyMap,
            elearningPropertyMap);
        entryList.add(thisEntry);
      }
      groupList.add(new SettingsGroup(group.getTitle(), group.getDescription(), entryList));
    }
    context.getRequest().setAttribute("settingsGroupList", groupList);

    context.setJsp(JSP);
    return context;
  }

  /**
   * Mirrors the menu's own gates exactly (issue #1763), so a card cannot say "on" while the menu
   * behaves as "off". The two forms differ on purpose there: the e-commerce test is byte-identical
   * to the section gate it has to agree with, and fails closed; the e-learning test has no section
   * gate to match and fails open, so a site missing the property row keeps today's behaviour.
   */
  static boolean isModuleEnabled(String enabledProperty, Map<String, String> ecommercePropertyMap,
      Map<String, String> elearningPropertyMap) {
    if (enabledProperty == null) {
      return true;
    }
    if ("ecommerce.enabled".equals(enabledProperty)) {
      return "true".equals(ecommercePropertyMap.get(enabledProperty));
    }
    if ("elearning.enabled".equals(enabledProperty)) {
      String value = elearningPropertyMap.get(enabledProperty);
      return StringUtils.isBlank(value) || "true".equals(value);
    }
    return true;
  }
}
