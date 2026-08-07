/*
 * Copyright 2022 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.application.admin.AnalyticsTrackingIdCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.application.cms.ColorCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.mailinglists.MailChimpCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.SqlTimestampConverter;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import jakarta.servlet.jsp.jstl.core.Config;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/18/18 4:20 PM
 */
public class SitePropertiesEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/site-properties-editor.jsp";

  static String PREFIX_PREFERENCE = "prefix";

  // Prefixes controlling security-sensitive behaviour; changes require a recent step-up (IA-2 / AC-6).
  private static final Set<String> SECURITY_PREFIXES =
      new HashSet<>(Arrays.asList("mfa", "content.review", "security"));

  public WidgetContext execute(WidgetContext context) {

    String prefix = context.getPreferences().get(PREFIX_PREFERENCE);

    // Check the request for POST errors
    List<SiteProperty> siteProperties = (List) context.getRequestObject();
    if (siteProperties == null) {
      // Load the properties
      siteProperties = new ArrayList<>();
      String[] prefixList = prefix.split(",");
      for (String thisPrefix : prefixList) {
        List<SiteProperty> sitePropertiesList = SitePropertyRepository.findAllByPrefix(thisPrefix);
        if (sitePropertiesList != null) {
          siteProperties.addAll(sitePropertiesList);
        }
      }
    }
    context.getRequest().setAttribute("sitePropertyList", siteProperties);
    context.getRequest().setAttribute("secretPropertyNames", SecretSitePropertiesCommand.getSecretPropertyNames());

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    // Lets the JSP special-case content (e.g. help text) for one exact settings page without
    // guessing from the display title, and without affecting the other pages that share this widget.
    context.getRequest().setAttribute("prefix", prefix);

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Gate security-sensitive prefix changes behind a recent step-up (IA-2 / AC-6).
    String prefix = context.getPreferences().get(PREFIX_PREFERENCE);
    if (isSecuritySensitivePrefix(prefix) && !StepUpAuthCommand.isValid(context.getUserSession())) {
      context.setRedirect("/step-up-auth?return=" + context.getUri());
      return context;
    }

    // One-off action, specific to the mailing-list settings page only (issue #523) -- a read-only
    // credential check, not a properties save, so it's handled before (and instead of) the generic
    // save logic below. This widget stays generic for every other settings page; only the JSP and
    // this one action branch know about mailing-list specifically.
    if ("testMailChimpConnection".equals(context.getParameter("action"))) {
      return handleTestMailChimpConnection(context, prefix);
    }

    // Load the properties
    List<SiteProperty> siteProperties = new ArrayList<>();
    String[] prefixList = prefix.split(",");
    for (String thisPrefix : prefixList) {
      List<SiteProperty> sitePropertiesList = SitePropertyRepository.findAllByPrefix(thisPrefix);
      if (sitePropertiesList != null) {
        siteProperties.addAll(sitePropertiesList);
      }
    }

    // Track which secret properties actually received a new value, to audit the rotation by name only
    // (never the value). Non-secret names are audited as a count; no secret value is ever recorded.
    List<String> secretsRotated = new ArrayList<>();
    // issue #454 review: modified/modified_by must only be stamped for a property whose value
    // actually changed -- every property on a saved page reaches SitePropertyRepository.save(),
    // including ones the admin never touched, so this can't be inferred from "was it saved."
    Set<String> changedPropertyNames = new HashSet<>();

    // Populate the entries from the request and Validate the values
    for (SiteProperty siteProperty : siteProperties) {

      String originalValue = siteProperty.getValue();

      // Determine the value
      String newValue = context.getParameter(siteProperty.getName());
      if (newValue == null) {
        newValue = "";
      } else {
        newValue = newValue.trim();
      }

      // Secret values are rendered as empty masked fields; a blank submission means unchanged,
      // so keep the stored value instead of wiping it
      if (SecretSitePropertiesCommand.isSecret(siteProperty.getName())) {
        // issue #454: an optional expiry date travels alongside the value, submitted every time
        // (not just on rotation) so it can be cleared or changed independently of the secret value
        String expiresAtParam = context.getParameter(siteProperty.getName() + "__expiresAt");
        if (StringUtils.isNotBlank(expiresAtParam)) {
          try {
            siteProperty.setExpiresAt(Timestamp.valueOf(LocalDate.parse(expiresAtParam).atStartOfDay()));
          } catch (DateTimeParseException e) {
            context.setErrorMessage(siteProperty.getLabel() + " has an invalid expiration date");
          }
        } else {
          siteProperty.setExpiresAt(null);
        }
        if (StringUtils.isBlank(newValue)) {
          continue;
        }
        // A non-blank secret submission is a real rotation -- record the name, never the value
        secretsRotated.add(siteProperty.getName());
      }

      // Handle types
      if ("boolean".equals(siteProperty.getType())) {
        if (!"true".equals(newValue)) {
          newValue = "false";
        }
      } else if ("web-page".equals(siteProperty.getType())) {
        if (StringUtils.isNotBlank(newValue) && !newValue.startsWith("/")) {
          newValue = "/" + newValue;
        }
      }

      // Validate the values based on type
      siteProperty.setValue(newValue);
      if (!java.util.Objects.equals(originalValue, newValue)) {
        changedPropertyNames.add(siteProperty.getName());
      }
      if (StringUtils.isBlank(newValue)) {
        continue;
      }
      if ("url".equals(siteProperty.getType())) {
        if (newValue.startsWith("http://localhost") || newValue.startsWith("https://localhost")) {
          continue;
        }
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (!urlValidator.isValid(newValue)) {
          context.setErrorMessage(siteProperty.getLabel() + " has an invalid URL");
        }
      } else if ("color".equals(siteProperty.getType())) {
        if (!ColorCommand.isHexColor(newValue)) {
          context.setErrorMessage(siteProperty.getLabel() + " needs hex formatting value");
        }
      } else if (AnalyticsTrackingIdCommand.isTrackingIdProperty(siteProperty.getName())) {
        if (!AnalyticsTrackingIdCommand.isValid(newValue)) {
          context.setErrorMessage(siteProperty.getLabel() + " is not a valid tracking id");
        }
      }
    }

    // If there's an error, pass the form values back
    if (context.getErrorMessage() != null) {
      context.setRequestObject(siteProperties);
      return context;
    }

    // Save the entries
    boolean saved = SitePropertyRepository.saveAll(prefix, siteProperties, context.getUserId(), changedPropertyNames);

    // Record the settings change -- property names and any rotated secret names only, never values
    String settingDetails = "properties=" + siteProperties.size()
        + (secretsRotated.isEmpty() ? "" : "; secretsRotated=" + secretsRotated);
    AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "setting.update",
        saved ? AuditEventCommand.SUCCESS : AuditEventCommand.FAILURE,
        "site_property", prefix, prefix, settingDetails);

    // issue #454: a dedicated, per-secret audit event -- lets the Integrations hub (and anyone
    // auditing) find "when was THIS secret last rotated" directly, instead of parsing the
    // whole-page setting.update details string above
    if (saved) {
      for (String rotatedName : secretsRotated) {
        AuditEventCommand.record(context, AuditEventCommand.CONFIGURATION, "secret.rotate",
            AuditEventCommand.SUCCESS, "site_property", rotatedName, rotatedName, null);
      }
    }

    if (saved) {
      // Update global cached settings
      String timezone = LoadSitePropertyCommand.loadByName("site.timezone");
      if (StringUtils.isNotBlank(timezone)) {
        // The format users see
        Config.set(context.getRequest().getServletContext(), Config.FMT_TIME_ZONE, timezone);
        // Replace the default converter
        SqlTimestampConverter converter = (SqlTimestampConverter) ConvertUtils.lookup(Timestamp.class);
        converter.setTimeZone(TimeZone.getTimeZone(ZoneId.of(timezone)));
        ConvertUtils.register(converter, Timestamp.class);
      }
      // The rendered Footer object is cached indefinitely (see WebContainerLayoutCommand), so a
      // change to which named layout it's built from needs an explicit invalidation to take effect
      for (SiteProperty siteProperty : siteProperties) {
        if ("theme.footer.layout".equals(siteProperty.getName())) {
          CacheManager.invalidateObjectCacheKey(CacheManager.WEBSITE_FOOTER);
          break;
        }
      }
      // Determine the page to return to
      context.setSuccessMessage("Values were saved");
    } else {
      context.setErrorMessage("Values could not be saved");
    }
    return context;
  }

  /** Re-loads the current (saved, not submitted) properties and re-renders the editor with the
   * MailChimp connection test result attached, mirroring what execute() shows on a normal page load. */
  private WidgetContext handleTestMailChimpConnection(WidgetContext context, String prefix) {
    List<SiteProperty> siteProperties = new ArrayList<>();
    String[] prefixList = prefix.split(",");
    for (String thisPrefix : prefixList) {
      List<SiteProperty> sitePropertiesList = SitePropertyRepository.findAllByPrefix(thisPrefix);
      if (sitePropertiesList != null) {
        siteProperties.addAll(sitePropertiesList);
      }
    }
    context.getRequest().setAttribute("sitePropertyList", siteProperties);
    context.getRequest().setAttribute("secretPropertyNames", SecretSitePropertiesCommand.getSecretPropertyNames());
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("prefix", prefix);

    context.getRequest().setAttribute("mailChimpTestResult", MailChimpCommand.testConnection());

    context.setJsp(JSP);
    return context;
  }

  private static boolean isSecuritySensitivePrefix(String prefix) {
    if (StringUtils.isBlank(prefix)) {
      return false;
    }
    for (String p : prefix.split(",")) {
      if (SECURITY_PREFIXES.contains(p.trim())) {
        return true;
      }
    }
    return false;
  }
}
