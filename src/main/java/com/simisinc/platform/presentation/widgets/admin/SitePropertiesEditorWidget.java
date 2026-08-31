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

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.admin.AnalyticsTrackingIdCommand;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.admin.SecretSitePropertiesCommand;
import com.simisinc.platform.application.cms.ColorCommand;
import com.simisinc.platform.application.cms.InternalPageAccessCommand;
import com.simisinc.platform.application.login.MfaEnforcementCommand;
import com.simisinc.platform.application.login.MfaEnrollmentPageCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.application.mailinglists.MailChimpCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
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
      // oauth included for the same reason as mfa: turning on an external identity provider, or
      // repointing oauth.serviceUrl at a different one, decides who can get into the site.
      new HashSet<>(Arrays.asList("mfa", "content.review", "security", "oauth"));

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
    attachGroupList(context, siteProperties);

    // The visual logo-color picker needs real thumbnails for Full color / All white / Mixed --
    // findAllByPrefix("theme") above only returns theme.* rows, not the site.logo* values that
    // actually hold the uploaded image URLs.
    if ("theme".equals(prefix)) {
      context.getRequest().setAttribute("logoUrl", LoadSitePropertyCommand.loadByName("site.logo"));
      context.getRequest().setAttribute("logoWhiteUrl", LoadSitePropertyCommand.loadByName("site.logo.white"));
      context.getRequest().setAttribute("logoMixedUrl", LoadSitePropertyCommand.loadByName("site.logo.mixed"));
    }

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    String prefix = context.getPreferences().get(PREFIX_PREFERENCE);

    // Gate security-sensitive prefix changes behind a recent step-up (IA-2 / AC-6). Uses the same
    // inline re-authentication pattern as UserFormWidget/MyMfaSettingsWidget (a "stepUpRequired"
    // shared value the JSP renders as a password/code prompt on the same form) rather than a page
    // redirect -- a prior version redirected to /step-up-auth, a page that was never actually
    // merged into the app, which made Save on these prefixes silently do nothing.
    if (isSecuritySensitivePrefix(prefix) && !StepUpAuthCommand.isValid(context.getUserSession())) {
      String stepUpCredential = context.getParameter("stepUpCredential");
      boolean verified = false;
      if (StringUtils.isNotBlank(stepUpCredential)) {
        User actingUser = LoadUserCommand.loadUser(context.getUserId());
        verified = StepUpAuthCommand.verify(context.getUserSession(), actingUser, stepUpCredential);
        if (!verified) {
          context.setErrorMessage("Re-authentication failed. Enter your password or authenticator code.");
        }
      }
      if (!verified) {
        context.addSharedRequestValue("stepUpRequired", "true");
        redisplayEditor(context, prefix);
        return context;
      }
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

    // Refuse an MFA enforcement policy that would lock everyone out. Enforcement redirects every
    // non-exempt request to the enrollment page and exempts only that page, so naming roles while
    // the enrollment page cannot actually enroll anyone leaves no way back in -- not even to this
    // screen to undo it. Validated against the values being submitted, so it also catches changing
    // the enrollment URL to a broken page while enforcement is already on.
    if (context.getErrorMessage() == null) {
      validateMfaEnforcement(context, siteProperties);
    }

    // Refuse an internal-page group that does not resolve. Unlike the MFA check above this one can
    // only ever be an inconvenience rather than a lockout -- the content-editor tier always passes
    // InternalPageAccessCommand -- but a typo would silently restrict every internal page to nobody,
    // and the operator would see no symptom because they are in the tier that bypasses it.
    if (context.getErrorMessage() == null) {
      validateInternalPageGroup(context, siteProperties);
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
    attachGroupList(context, siteProperties);

    context.getRequest().setAttribute("mailChimpTestResult", MailChimpCommand.testConnection());

    context.setJsp(JSP);
    return context;
  }

  /** Rejects a submitted {@code mfa.required.roles} value when the submitted
   * {@code mfa.enrollment.url} does not resolve to a page carrying the enrollment widget. Only a
   * non-blank role list is checked -- clearing the roles is how enforcement is turned off, and must
   * always be allowed through even when the enrollment page is broken. */
  private void validateMfaEnforcement(WidgetContext context, List<SiteProperty> siteProperties) {
    String requiredRoles = null;
    String enrollmentUrl = null;
    for (SiteProperty siteProperty : siteProperties) {
      if (MfaEnforcementCommand.PROPERTY_REQUIRED_ROLES.equals(siteProperty.getName())) {
        requiredRoles = siteProperty.getValue();
      } else if (MfaEnforcementCommand.PROPERTY_ENROLLMENT_URL.equals(siteProperty.getName())) {
        enrollmentUrl = siteProperty.getValue();
      }
    }
    if (StringUtils.isBlank(requiredRoles)) {
      return;
    }
    if (StringUtils.isBlank(enrollmentUrl)) {
      enrollmentUrl = MfaEnforcementCommand.DEFAULT_ENROLLMENT_URL;
    }
    if (!MfaEnrollmentPageCommand.isUsableEnrollmentPage(enrollmentUrl)) {
      context.setErrorMessage("MFA enforcement was not enabled: the enrollment page " + enrollmentUrl
          + " does not exist or does not contain the two-factor authentication widget, so anyone "
          + "required to enroll would be locked out with no way to reach it. Create that page with "
          + "the \"Two-Factor Authentication\" widget first, then set the roles.");
    }
  }

  /** Re-loads the current (saved, not submitted) properties and re-renders the editor -- used when
   * a step-up re-authentication prompt needs to interrupt post() before any save is attempted. */
  private void redisplayEditor(WidgetContext context, String prefix) {
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
    attachGroupList(context, siteProperties);
    context.setJsp(JSP);
  }

  /** Supplies the group picker for any {@code group}-typed property (issue #1688) with the groups it
   * can offer. Called from every path that renders the editor JSP -- a fresh load, the MailChimp
   * connection test, and the step-up re-authentication prompt -- because a picker whose option list
   * is missing renders as empty, and saving an empty select would silently clear the restriction. */
  private void attachGroupList(WidgetContext context, List<SiteProperty> siteProperties) {
    for (SiteProperty siteProperty : siteProperties) {
      if ("group".equals(siteProperty.getType())) {
        context.getRequest().setAttribute("groupList", GroupRepository.findAll());
        return;
      }
    }
  }

  /** Rejects a submitted {@code security.internalPages.group} that names no existing group. Blank is
   * always accepted: blank is the off switch, and it has to stay reachable even from a state where
   * the named group has since been deleted. A group with no members saves with a warning rather than
   * an error -- it is a legitimate intermediate step when setting up staff access. */
  private void validateInternalPageGroup(WidgetContext context, List<SiteProperty> siteProperties) {
    String uniqueId = null;
    for (SiteProperty siteProperty : siteProperties) {
      if (InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP.equals(siteProperty.getName())) {
        uniqueId = StringUtils.trimToNull(siteProperty.getValue());
      }
    }
    if (uniqueId == null) {
      return;
    }
    Group group = GroupRepository.findByUniqueId(uniqueId);
    if (group == null) {
      context.setErrorMessage("Internal page access was not changed: there is no group \"" + uniqueId
          + "\". Pick a group from the list, or leave it blank to keep \"Internal\" as a label only.");
      return;
    }
    if (group.getUserCount() == 0) {
      context.setWarningMessage("Internal pages are now restricted to \"" + group.getName()
          + "\", which has no members yet. Until someone is added, only content editors can view them.");
    }
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
