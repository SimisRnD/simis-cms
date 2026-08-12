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

package com.simisinc.platform.presentation.widgets.cms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sanctionco.jmail.JMail;
import com.simisinc.platform.application.RateLimitCommand;
import com.simisinc.platform.application.cms.CaptchaCommand;
import com.simisinc.platform.application.cms.FormCommand;
import com.simisinc.platform.application.cms.FormFieldCommand;
import com.simisinc.platform.application.cms.FunnelEventCommand;
import com.simisinc.platform.domain.events.cms.FormSubmittedEvent;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormDefinitionRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormFieldRepository;
import com.simisinc.platform.infrastructure.persistence.cms.FormSubmissionFailureRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 6/1/18 11:11 AM
 */
public class FormWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  protected static Log LOG = LogFactory.getLog(FormWidget.class);

  static String JSP = "/cms/form.jsp";
  static String SUCCESS_JSP = "/cms/form-success.jsp";
  static String RATE_LIMITED_JSP = "/cms/error-rate-limited.jsp";

  // Anti-bot honeypot (issue #1153): form.jsp renders a real, visually-hidden input under this exact
  // name/id -- a genuine visitor never sees or fills it in, so a non-blank value here is a strong bot
  // signal. Underscore-prefixed so it can never collide with an admin-defined field's own slugified
  // name (FormFieldCommand#generateHtmlName only ever produces lowercase alphanumerics and hyphens).
  static final String HONEYPOT_FIELD_NAME = "_hpWebsite";

  public WidgetContext execute(WidgetContext context) {

    // No need to show widget when rate limiting is triggered
    if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), false)) {
      context.setJsp(RATE_LIMITED_JSP);
      return context;
    }

    // Resolve the database-backed form definition, if any (issue #409) -- a formId configured but
    // not resolvable is a hard failure (matching loadFormFieldList's pre-existing "not found"
    // contract below), and a resolved-but-disabled form is not shown to the public. Resolved before
    // the success-page branch below too, since a database-backed form's own successTitle/
    // successMessage (issue #409 follow-up) must be authoritative there as well.
    String formIdPref = context.getPreferences().get("formId");
    FormDefinition formDefinition = resolveFormDefinition(context, formIdPref);
    if (StringUtils.isNotBlank(formIdPref) && formDefinition == null) {
      return null;
    }
    if (formDefinition != null && !formDefinition.getEnabled()
        && !(context.hasRole("admin") || context.hasRole("community-manager"))) {
      return null;
    }

    String isSuccess = context.getSharedRequestValue(context.getUniqueId() + "formWidgetSuccess");
    if ("true".equals(isSuccess)) {
      context.getRequest().setAttribute("successTitle",
          formDefinition != null ? formDefinition.getSuccessTitle() : context.getPreferences().get("successTitle"));
      context.getRequest().setAttribute("successMessage", resolveSuccessMessage(context, formDefinition));
      context.setJsp(SUCCESS_JSP);
      return context;
    }
    context.setJsp(JSP);

    // Preferences -- a database-backed form's own "Button Label" (issue #409 follow-up) is
    // authoritative when formId is configured
    context.getRequest().setAttribute("buttonName", resolveButtonName(context, formDefinition));

    // Standard request items -- a database-backed form's own title/subtitle (issue #409 follow-up)
    // are authoritative when formId is configured; icon has no database-backed equivalent
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", formDefinition != null ? formDefinition.getTitle() : context.getPreferences().get("title"));
    context.getRequest().setAttribute("subtitle", formDefinition != null ? formDefinition.getSubtitle() : context.getPreferences().get("subtitle"));

    // Privacy notice (issue #1155) -- database-backed forms only (no XML-preference equivalent);
    // set unconditionally (true/false, never left unset) so form.jsp's check is a simple boolean read
    context.getRequest().setAttribute("showPrivacyNotice", formDefinition != null && formDefinition.getShowPrivacyNotice());

    // Determine the captcha service -- a database-backed form's own "Use Captcha?" setting is
    // authoritative when formId is configured; only the XML-preference path (formDefinition null)
    // still reads this from the widget placement's own preferences
    boolean useCaptcha = resolveUseCaptcha(context, formDefinition);
    if (useCaptcha) {
      CaptchaCommand.populateWidgetAttributes(context);
    }

    // Previous post had error
    FormData formData = (FormData) context.getRequestObject();
    if (formData != null) {
      if (formData.getQueryParameters() != null) {
        context.getRequest().setAttribute("queryString", formData.getQueryParameters());
      }
      context.getRequest().setAttribute("formFieldList", formData.getFormFieldList());
      return context;
    }

    // Use the fields preference (or a database-backed form definition, issue #409) to determine
    // the item properties to be shown
    List<FormField> formFieldList = loadFormFieldList(context, formDefinition);
    if (formFieldList == null) {
      return null;
    }
    context.getRequest().setAttribute("formFieldList", formFieldList);

    // Check for any query parameters to save with the form data
    if (StringUtils.isNotBlank(context.getRequest().getQueryString())) {
      context.getRequest().setAttribute("queryString", context.getRequest().getQueryString());
    }

    // Show the JSP
    return context;
  }

  public WidgetContext post(WidgetContext context) {
    // Determine the fields
    boolean isValid = true;
    int blankValues = 0;
    // The first rejection reason encountered wins (issue #563) -- fields are checked in a loop that
    // doesn't short-circuit on the first invalid one, so this must not be overwritten by a later field's
    // failure in the same submission.
    String rejectionReason = null;
    // issue #1154: the submitter's own address, if this form has an email-type field and the answer
    // is syntactically valid -- there's no other way to know who to reply to. First such field wins.
    String submitterEmail = null;

    // Resolve the database-backed form definition, if any (issue #409) -- see execute() for why a
    // formId configured but not resolvable, or resolved-but-disabled, must not proceed. Checking
    // this again here (not just in execute()) matters: a direct POST is not required to have gone
    // through execute() first.
    String formIdPref = context.getPreferences().get("formId");
    FormDefinition formDefinition = resolveFormDefinition(context, formIdPref);

    // A database-backed form's own generated uniqueId is authoritative when formId is configured
    // (issue #409 follow-up) -- form_data submissions must be keyed by the collision-checked value
    // SaveFormDefinitionCommand.generateUniqueId() produced, not a separately hand-typed preference
    // that could collide with an unrelated form's formUniqueId. Resolved here, ahead of the
    // early-return checks below (issue #563 follow-up), so each of them can record which form a
    // rejected direct POST was aimed at.
    String formUniqueId = formDefinition != null ? formDefinition.getUniqueId() : context.getPreferences().get("formUniqueId");

    if (StringUtils.isNotBlank(formIdPref) && formDefinition == null) {
      // A direct POST naming a formId that no longer resolves (issue #563 follow-up) -- previously
      // silent, unlike every other rejection path in this method
      recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE);
      return null;
    }
    if (formDefinition != null && !formDefinition.getEnabled()
        && !(context.hasRole("admin") || context.hasRole("community-manager"))) {
      // A direct POST to a form an admin has since disabled (issue #563 follow-up) -- previously silent
      recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE);
      return null;
    }

    // Honeypot (issue #1153): a real visitor never sees or fills in this field, so a non-blank value
    // is treated as spam and dropped exactly like a genuine success -- returning null here (the same
    // no-message redirect a real successful submission takes at the end of this method) gives a bot
    // nothing to distinguish "caught" from "accepted", so it has no signal to adapt on.
    if (StringUtils.isNotBlank(context.getParameter(context.getUniqueId() + HONEYPOT_FIELD_NAME))) {
      recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_HONEYPOT);
      return null;
    }

    List<FormField> formFieldList = loadFormFieldList(context, formDefinition);
    if (formFieldList == null) {
      // The form itself has no fields configured/resolvable (issue #563 follow-up) -- a configuration
      // problem, not the submitter leaving a required field blank, so this must not reuse
      // REASON_MISSING_FIELD (see the per-field loop below). Previously silent.
      recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_FORM_UNAVAILABLE);
      return null;
    }
    for (FormField formField : formFieldList) {
      // Determine the user's value. A checkbox-group field (type == checkbox with options) can
      // have several boxes checked, which the browser submits as repeated same-named parameters --
      // getParameter() only ever returns the first one, silently dropping the rest.
      // Must mirror form.jsp's !empty check (JSTL's !empty treats a non-null empty Map as empty)
      // exactly, not just null-check listOfOptions -- a non-null-but-empty map is reachable via a
      // malformed XML fields preference (e.g. list=","), and disagreeing here makes form.jsp render
      // a single-toggle checkbox while this method takes the checkbox-group branch, whose value
      // resolution can never find a match against zero options -- silently discarding every
      // submission and making a required field permanently unsatisfiable.
      boolean isCheckboxGroup = "checkbox".equals(formField.getType())
          && formField.getListOfOptions() != null
          && !formField.getListOfOptions().isEmpty();
      String parameterValue = isCheckboxGroup
          ? resolveCheckboxGroupValue(context, formField)
          : context.getParameter(context.getUniqueId() + formField.getName());
      if ("checkbox".equals(formField.getType()) && !isCheckboxGroup && !"true".equals(StringUtils.trim(parameterValue))) {
        // A single-toggle checkbox only ever submits the literal "true" when checked -- form.jsp's own
        // rendering (value="true") and redisplay logic (userValue eq 'true') both depend on this. A
        // direct POST that skips the rendered HTML (bots routinely do this) could otherwise satisfy a
        // required checkbox/consent field with any non-blank value, which would then be persisted to
        // form_data and emailed/displayed verbatim. Treat anything but "true" as unchecked/absent.
        parameterValue = null;
      }
      if (StringUtils.isBlank(parameterValue)) {
        // Check if the field is required
        if (formField.isRequired()) {
          isValid = false;
          context.setWarningMessage(formField.getLabel() + " is required");
          if (rejectionReason == null) {
            rejectionReason = FormSubmissionFailureRepository.REASON_MISSING_FIELD;
          }
        }
        ++blankValues;
        continue;
      }
      parameterValue = parameterValue.trim();
      if (isCheckboxGroup) {
        formField.setUserValue(parameterValue);
      } else if (formField.getListOfOptions() != null && !formField.getListOfOptions().isEmpty()) {
        // Same isEmpty() guard as isCheckboxGroup above -- otherwise a checkbox field with a
        // non-null-but-empty listOfOptions (single-toggle per form.jsp) would wrongly take the
        // select-field label-lookup path here and always resolve to null against zero options.
        formField.setUserValue(formField.getListOfOptions().get(parameterValue));
      } else {
        formField.setUserValue(parameterValue);
      }
      if ("email".equals(formField.getType())) {
        if (!JMail.isValid(parameterValue)) {
          isValid = false;
          context.setWarningMessage("Check the email address and try again");
          if (rejectionReason == null) {
            rejectionReason = FormSubmissionFailureRepository.REASON_INVALID_EMAIL;
          }
        } else if (submitterEmail == null) {
          submitterEmail = parameterValue;
        }
      }
      LOG.debug("Set userValue " + formField.getName() + "=" + formField.getUserValue());
    }
    if (isValid && blankValues == formFieldList.size()) {
      isValid = false;
      context.setWarningMessage("Check the form and try again");
      rejectionReason = FormSubmissionFailureRepository.REASON_BLANK;
    }

    // Validate the captcha
    boolean useCaptcha = resolveUseCaptcha(context, formDefinition);
    if (useCaptcha) {
      boolean captchaSuccess = CaptchaCommand.validateRequest(context);
      if (!captchaSuccess) {
        isValid = false;
        context.setWarningMessage("The form could not be validated");
        if (rejectionReason == null) {
          rejectionReason = FormSubmissionFailureRepository.REASON_CAPTCHA_FAILED;
        }
      }
    }

    // If the user is not logged in, then limit the number of form posts here by IP
    if (!context.getUserSession().isLoggedIn()) {
      if (!RateLimitCommand.isIpAllowedRightNow(context.getRequest().getRemoteAddr(), true)) {
        context.setErrorMessage(RateLimitCommand.INVALID_ATTEMPTS);
        recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_RATE_LIMITED);
        return context;
      }
    }

    // Prepare the object and handle any errors
    FormData formData = new FormData();
    formData.setFormUniqueId(formUniqueId);
    formData.setSessionId(context.getUserSession().getSessionId());
    if (context.getUserSession().isLoggedIn()) {
      formData.setCreatedBy(context.getUserId());
    }
    formData.setFormFieldList(formFieldList);
    formData.setIpAddress(context.getRequest().getRemoteAddr());
    formData.setUrl(resolvePageUrl(context));
    if (context.getParameter("queryString") != null) {
      formData.setQueryParameters(context.getParameter("queryString"));
    }
    if (!isValid) {
      recordFailureQuietly(context, formUniqueId, rejectionReason);
      context.setRequestObject(formData);
      return context;
    }

    // A database-backed form's own "Check for spam?" setting is authoritative when formId is
    // configured (issue #409 follow-up); only the XML-preference path still reads this from the
    // widget placement's own preferences
    boolean checkForSpam = formDefinition != null
        ? formDefinition.getCheckForSpam()
        : Boolean.parseBoolean(context.getPreferences().getOrDefault("checkForSpam", "true"));
    if (checkForSpam) {
      FormCommand.checkNotificationRules(formData);
    }

    // Store in the database
    if (FormDataRepository.save(formData) == null) {
      // A genuine DB save failure (issue #563 follow-up) -- previously the submitter saw an error but
      // nothing was recorded in the rejection-tracking system at all
      context.setErrorMessage("The form was not saved... try again?");
      recordFailureQuietly(context, formUniqueId, FormSubmissionFailureRepository.REASON_SYSTEM_ERROR);
      context.setRequestObject(formData);
      return context;
    }

    // Conversion funnel tracking (issue #565, phase 1) -- a no-op unless this formUniqueId is the
    // site's admin-configured contact form
    FunnelEventCommand.recordContactFormSubmitted(formUniqueId, formData.getSessionId());

    // Send an alert based on the preferences (or transform for another system) -- a database-backed
    // form's own "Email submissions to" setting is authoritative when formId is configured (issue
    // #409 follow-up); only the XML-preference path still reads this from the widget placement's
    // own preferences
    String emailAddresses = formDefinition != null ? formDefinition.getEmailTo() : context.getPreferences().get("emailTo");
    // A submitter confirmation email (issue #1154) is only offered by a database-backed form -- the
    // XML-preference path has no equivalent setting, matching how useCaptcha/checkForSpam/emailTo
    // already work above
    boolean sendConfirmation = formDefinition != null && formDefinition.getSendConfirmationToSubmitter();
    String confirmationSubject = formDefinition != null ? formDefinition.getConfirmationSubject() : null;
    String confirmationMessage = formDefinition != null ? formDefinition.getConfirmationMessage() : null;
    WorkflowManager.triggerWorkflowForEvent(new FormSubmittedEvent(
        formData, emailAddresses, submitterEmail, sendConfirmation, confirmationSubject, confirmationMessage));

    // Redirect back so the message can be displayed
    context.addSharedRequestValue(context.getUniqueId() + "formWidgetSuccess", "true");

    return null;
  }

  /**
   * A checkbox-group field submits one request parameter per checked option, all sharing the same
   * name. context.getParameter() (a thin wrapper the rest of this loop uses) only ever returns the
   * first of several same-named values, silently dropping the rest -- read the full array from the
   * parameter map instead, the same way other multi-checkbox inputs in this codebase already do
   * (e.g. the "tagId" checkbox group in EditItemFormWidget/CreateAnItemWidget). Values are joined
   * into a single comma-separated string of display labels -- matching how a single-select field's
   * chosen option is already translated to its display label via listOfOptions -- so it fits the
   * existing single-String FormField.userValue / form_data JSON "value" shape without a schema
   * change. Option order (not submission order) is used so the stored value doesn't depend on
   * checkbox click order, and duplicate submitted values are de-duplicated.
   *
   * <p>As a side effect, also records the matched option KEYS on {@code formField} (same option-order,
   * de-duplicated set as the label join above) via {@link FormField#setCheckedOptionKeys}, regardless
   * of whether this field or the overall submission ends up valid -- form.jsp needs the keys, not the
   * labels, to redisplay the right boxes checked after a same-request validation-error round trip, and
   * the labels alone aren't a safe reverse lookup if two options ever share one.
   */
  private static String resolveCheckboxGroupValue(WidgetContext context, FormField formField) {
    String[] values = context.getParameterMap().get(context.getUniqueId() + formField.getName());
    if (values == null || values.length == 0) {
      formField.setCheckedOptionKeys(Collections.emptyList());
      return null;
    }
    Set<String> checkedKeys = new HashSet<>(Arrays.asList(values));
    List<String> matchedKeys = new ArrayList<>();
    StringJoiner joiner = new StringJoiner(",");
    for (Map.Entry<String, String> option : formField.getListOfOptions().entrySet()) {
      if (checkedKeys.contains(option.getKey())) {
        joiner.add(option.getValue());
        matchedKeys.add(option.getKey());
      }
    }
    formField.setCheckedOptionKeys(matchedKeys);
    return joiner.length() == 0 ? null : joiner.toString();
  }

  /**
   * Resolves the database-backed form definition for this widget placement (issue #409), or null
   * when {@code formId} is blank -- the pre-existing, still-default XML-configured case. Logs and
   * returns null (distinguishable from the "not configured" case only by {@code formIdPref} itself
   * being non-blank) when {@code formId} is set but does not resolve to a real row, so callers can
   * hard-fail rather than silently falling back to the XML {@code fields} preference.
   *
   * <p>Callers that need to know whether {@code formId} was configured at all (to decide between a
   * hard failure and the XML fallback) must check {@code formIdPref} themselves; this method alone
   * cannot distinguish "not configured" from "configured but not found" since both return null.
   */
  private FormDefinition resolveFormDefinition(WidgetContext context, String formIdPref) {
    if (StringUtils.isBlank(formIdPref)) {
      return null;
    }
    long formId = NumberUtils.toLong(formIdPref, -1);
    FormDefinition formDefinition = FormDefinitionRepository.findById(formId);
    if (formDefinition == null) {
      LOG.warn("Form definition was not found for formId: " + formIdPref);
    }
    return formDefinition;
  }

  /**
   * Whether captcha should be shown/validated for this request (issue #409 follow-up). A
   * database-backed form's own "Use Captcha?" setting (configured at /admin/forms-editor) is
   * authoritative once a form has been resolved; only the still-default XML-preference path
   * (formDefinition null) reads this from the widget placement's own preferences, exactly as before
   * this feature existed.
   */
  private boolean resolveUseCaptcha(WidgetContext context, FormDefinition formDefinition) {
    if (formDefinition != null) {
      return formDefinition.getUseCaptcha();
    }
    return "true".equals(context.getPreferences().getOrDefault("useCaptcha", "false"));
  }

  /**
   * The submit button's label (issue #409 follow-up). A database-backed form's own "Button Label"
   * (configured at /admin/forms-editor) is authoritative once a form has been resolved, falling back
   * to "Submit" when left blank -- the same default the still-supported XML-preference path already
   * applied via getOrDefault().
   */
  private String resolveButtonName(WidgetContext context, FormDefinition formDefinition) {
    String buttonName = formDefinition != null ? formDefinition.getButtonName() : context.getPreferences().get("buttonName");
    return StringUtils.defaultIfBlank(buttonName, "Submit");
  }

  /**
   * The message shown on the success page after a valid submission (issue #409 follow-up). A
   * database-backed form's own "Success Message" is authoritative once a form has been resolved,
   * falling back to the same default the still-supported XML-preference path already applied via
   * getOrDefault() when left blank.
   */
  private String resolveSuccessMessage(WidgetContext context, FormDefinition formDefinition) {
    String successMessage = formDefinition != null ? formDefinition.getSuccessMessage() : context.getPreferences().get("successMessage");
    return StringUtils.defaultIfBlank(successMessage, "Your information has been submitted.");
  }

  /**
   * Resolves this form's field list (issue #409), given the {@code formDefinition} execute()/post()
   * already resolved via {@link #resolveFormDefinition}. When non-null, fields come from the
   * database -- FormFieldRepository, already ordered by field_order -- as the admin-managed
   * alternative to the XML {@code fields} preference. When null (the pre-existing, still-default
   * configuration, or a {@code formId} that failed to resolve -- callers must have already handled
   * that case before reaching here), this falls through to exactly the original XML-preference
   * parsing, unchanged, so a page that has never been touched by the form builder renders and
   * validates identically to before this feature existed.
   *
   * <p>Returns null (after logging a warning) when no fields could be resolved from either source,
   * matching the pre-existing "not configured" contract both execute() and post() already relied
   * on -- callers should treat a null return exactly as they did the old inline checks.
   */
  private List<FormField> loadFormFieldList(WidgetContext context, FormDefinition formDefinition) {
    if (formDefinition != null) {
      List<FormField> formFieldList = FormFieldRepository.findAllByFormDefinitionId(formDefinition.getId());
      if (formFieldList.isEmpty()) {
        LOG.warn("No fields were found for formId: " + formDefinition.getId());
        return null;
      }
      return formFieldList;
    }

    // Original XML-preference path (unchanged)
    PreferenceEntriesList fieldsEntriesList = context.getPreferenceAsDataList("fields");
    if (fieldsEntriesList.isEmpty()) {
      LOG.warn("Fields preference was not found");
      return null;
    }
    String formUniqueId = context.getPreferences().get("formUniqueId");
    List<FormField> formFieldList = FormFieldCommand.parseFieldContent(formUniqueId, fieldsEntriesList);
    if (formFieldList.isEmpty()) {
      LOG.warn("No fields were found");
      return null;
    }
    return formFieldList;
  }

  /**
   * The page URL, without duplicating the context path. context.getUrl() already includes it (scheme +
   * host + contextPath), and context.getUri() (the servlet request URI) already includes it too per the
   * servlet spec -- concatenating them as-is doubles it on any deployment where the app isn't mounted at
   * the root context. Stripping it from the URI side before concatenating, matching how PageServlet computes
   * a bare page path elsewhere in this codebase.
   */
  private static String resolvePageUrl(WidgetContext context) {
    return context.getUrl() + context.getUri().substring(context.getContextPath().length());
  }

  /**
   * Records a submission rejection for the analytics dashboard (issue #563). Never allowed to affect the
   * rejection the user actually sees -- a recording failure here must not become a second, unrelated
   * failure for the person submitting the form.
   */
  private void recordFailureQuietly(WidgetContext context, String formUniqueId, String reason) {
    try {
      FormSubmissionFailureRepository.record(
          formUniqueId, reason, context.getRequest().getRemoteAddr(), resolvePageUrl(context));
    } catch (Exception e) {
      LOG.error("Could not record a form submission failure", e);
    }
  }
}
