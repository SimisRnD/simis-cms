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

package com.simisinc.platform.application.cms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * Builds the subject line of a form's admin notification email.
 *
 * <p>
 * Every form used to notify with the same sentence -- "Website contact-us form 202608300003
 * submitted" -- which says which form fired and nothing about what arrived. Someone triaging a
 * mailbox had to open each one to learn whether it was a sales enquiry or a password question. A
 * form may now supply its own subject with {@code {{fieldName}}} placeholders, so the submission's
 * own values reach the subject line.
 * </p>
 *
 * <h2>Substituted values are attacker-controlled</h2>
 *
 * <p>
 * Anyone on the internet can type into a public form, and this text lands in an email header. Three
 * things follow, and the first is the one that matters:
 * </p>
 *
 * <ul>
 * <li><b>Line breaks are removed.</b> A newline in a subject is header injection -- it ends the
 * Subject header and lets whatever follows be read as another one, including recipients. Carriage
 * returns, line feeds and every other control character go.</li>
 * <li><b>Placeholder syntax is removed from values.</b> The resolved subject is handed to a
 * workflow whose own templating uses the same braces, so a submitted value containing them must not
 * come back as something to evaluate.</li>
 * <li><b>Length is capped</b>, per value and overall. Mail clients truncate anyway, and an
 * unbounded field would otherwise decide the whole header.</li>
 * </ul>
 *
 * <p>
 * What this does NOT do is make the subject trustworthy. A submitter can still put
 * "URGENT: invoice overdue" in a name field and see it in the subject. That is inherent to showing
 * submitted data at all; the value is triage, not authority, which is why a template should name
 * what it is showing ("from {{organization}}") rather than presenting the value bare.
 * </p>
 *
 * <p>
 * A select field is the exception worth knowing about: its options come from the form definition
 * rather than the submitter, so a topic or category placeholder carries a value the site chose. It
 * is the one part of a submission that can be relied on, and it makes the best leading term.
 * </p>
 */
public class FormNotificationSubjectCommand {

  /** Placeholders look like {{fieldName}}, with optional surrounding space. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");

  /** Anything that is not printable text has no business in a header. */
  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\p{Zl}\\p{Zp}]+");

  /** One value may not run away with the whole subject. */
  private static final int MAX_VALUE_LENGTH = 60;

  /** RFC 5322 recommends far less; clients truncate around here anyway. */
  private static final int MAX_SUBJECT_LENGTH = 200;

  /** Shown where a placeholder names a field the submission did not fill in. */
  private static final String EMPTY_VALUE = "(not given)";

  private FormNotificationSubjectCommand() {
    // Static utility, not instantiated
  }

  /**
   * The subject for this submission's notification.
   *
   * @param template the form's configured subject, which may contain {{fieldName}} placeholders
   * @param formData the submission, for placeholder values
   * @param formName the form's admin name, used by the default subject
   * @return a single-line subject, never null and never blank
   */
  public static String createSubject(String template, FormData formData, String formName) {
    if (StringUtils.isBlank(template)) {
      return defaultSubject(formData, formName);
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuilder result = new StringBuilder();
    int placeholders = 0;
    int resolved = 0;
    while (matcher.find()) {
      placeholders++;
      String value = valueOf(formData, matcher.group(1));
      if (!EMPTY_VALUE.equals(value)) {
        resolved++;
      }
      matcher.appendReplacement(result, Matcher.quoteReplacement(value));
    }
    matcher.appendTail(result);

    // A template that is only placeholders, none of which resolved, would send "(not given)" or
    // bare punctuation as the subject. Tracking what actually resolved is the honest test: looking
    // for letters in the output cannot tell a real value from the words this class substitutes
    // when a value is missing, which is how the first version of this passed its own check.
    boolean nothingResolved = placeholders > 0 && resolved == 0;
    String withoutPlaceholders = PLACEHOLDER.matcher(template).replaceAll("");
    boolean templateIsOnlyPlaceholders = StringUtils.isBlank(withoutPlaceholders.replaceAll("[^A-Za-z0-9]", ""));

    if (nothingResolved && templateIsOnlyPlaceholders) {
      return defaultSubject(formData, formName);
    }
    String subject = sanitise(result.toString());
    return StringUtils.isBlank(subject) ? defaultSubject(formData, formName) : subject;
  }

  /**
   * The subject a form gets when it has not configured one.
   *
   * <p>
   * Every form used to notify with "Website contact-us form 202608300003 submitted" -- which names
   * the form and the row id and says nothing a recipient can act on. Someone scanning a mailbox
   * could not tell a sales prospect from a password question without opening it.
   * </p>
   *
   * <p>
   * This reads as business rather than as a system notice, and it is one convention rather than a
   * template each form has to be given: no field is named, so it degrades cleanly on a form that
   * has no organisation, no dropdown, or neither.
   * </p>
   *
   * <pre>
   * New Contact Us inquiry - Acme Defense (Sales &amp; Business Development)
   * New Contact Us inquiry - chris@example.com
   * New Contact Us inquiry
   * </pre>
   *
   * @param formData the submission
   * @param formName the form's admin name, e.g. "Contact Us"
   * @return a single-line subject, never blank
   */
  public static String defaultSubject(FormData formData, String formName) {
    String name = StringUtils.defaultIfBlank(formName,
        formData != null ? formData.getFormUniqueId() : null);
    StringBuilder subject = new StringBuilder("New ");
    if (StringUtils.isNotBlank(name)) {
      subject.append(clean(name)).append(" ");
    }
    subject.append("inquiry");

    String identifier = bestIdentifier(formData);
    if (StringUtils.isNotBlank(identifier)) {
      subject.append(" - ").append(identifier);
    }
    String category = categoryValue(formData);
    if (StringUtils.isNotBlank(category)) {
      subject.append(" (").append(category).append(")");
    }
    return sanitise(subject.toString());
  }

  /**
   * Who the submission is from, in the order a reader would want it.
   *
   * <p>
   * An organisation identifies a prospect better than a person's name does, and a name better than
   * an address. Nothing here is required to exist -- a form with none of these still gets a usable
   * subject, which is what lets one convention serve every form.
   * </p>
   */
  private static String bestIdentifier(FormData formData) {
    for (String candidate : new String[] { "organization", "organisation", "company",
        "name", "fullname", "full-name", "email" }) {
      String value = rawValueOf(formData, candidate);
      if (StringUtils.isNotBlank(value)) {
        return StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
      }
    }
    return null;
  }

  /**
   * The submission's own categorisation, from the first field that offers a fixed list of options.
   *
   * <p>
   * A dropdown's options come from the form definition rather than from the submitter, so this is
   * the one part of a submission that carries a value the site chose. That makes it the most
   * trustworthy thing in the subject, and the reason a topic is worth showing at all.
   * </p>
   *
   * <p>
   * A placeholder choice is not a category. "&lt; Please Choose &gt;" and its kin mean the visitor
   * did not answer, and printing that is worse than printing nothing.
   * </p>
   */
  private static String categoryValue(FormData formData) {
    if (formData == null || formData.getFormFieldList() == null) {
      return null;
    }
    for (FormField field : formData.getFormFieldList()) {
      if (field.getListOfOptions() == null || field.getListOfOptions().isEmpty()) {
        continue;
      }
      String value = clean(field.getUserValue());
      if (StringUtils.isBlank(value) || isPlaceholderChoice(value)) {
        continue;
      }
      return StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
    }
    return null;
  }

  /** Whether a chosen option is really the "no choice made" entry. */
  private static boolean isPlaceholderChoice(String value) {
    String lower = value.toLowerCase().trim();
    if (lower.startsWith("<") && lower.endsWith(">")) {
      return true;
    }
    return lower.startsWith("please choose") || lower.startsWith("please select")
        || lower.equals("select") || lower.equals("choose") || lower.startsWith("-- ");
  }

  /** A field's submitted value, cleaned but not abbreviated. */
  private static String rawValueOf(FormData formData, String fieldName) {
    if (formData == null || formData.getFormFieldList() == null) {
      return null;
    }
    for (FormField field : formData.getFormFieldList()) {
      if (fieldName.equalsIgnoreCase(field.getName())) {
        return clean(field.getUserValue());
      }
    }
    return null;
  }

  /** The submitted value for a field name, already made safe for a header. */
  private static String valueOf(FormData formData, String fieldName) {
    if (formData == null || formData.getFormFieldList() == null) {
      return EMPTY_VALUE;
    }
    for (FormField field : formData.getFormFieldList()) {
      if (fieldName.equalsIgnoreCase(field.getName())) {
        String value = clean(field.getUserValue());
        return StringUtils.isBlank(value) ? EMPTY_VALUE : StringUtils.abbreviate(value, MAX_VALUE_LENGTH);
      }
    }
    // A placeholder naming a field this form does not have is an authoring mistake, not a
    // submission problem. Leaving the literal in would ship "{{compnay}}" to a recipient.
    return EMPTY_VALUE;
  }

  /** Strips what must never reach a header, from a value the submitter controls. */
  private static String clean(String value) {
    if (value == null) {
      return "";
    }
    String cleaned = CONTROL_CHARS.matcher(value).replaceAll(" ");
    // The workflow engine's own templating uses these braces; a value carrying them must not come
    // back as something for it to evaluate.
    cleaned = cleaned.replace("{{", "").replace("}}", "");
    return StringUtils.normalizeSpace(cleaned);
  }

  /** Final pass over the assembled subject: one line, bounded length. */
  private static String sanitise(String subject) {
    String cleaned = CONTROL_CHARS.matcher(StringUtils.defaultString(subject)).replaceAll(" ");
    return StringUtils.abbreviate(StringUtils.normalizeSpace(cleaned), MAX_SUBJECT_LENGTH);
  }
}
