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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;

/**
 * Tests the per-form notification subject, whose placeholders substitute values a stranger typed
 * into a public form. The substitution is the feature; keeping it out of the header structure is
 * the requirement.
 */
class FormNotificationSubjectCommandTest {

  /** A submission whose topic field carries options, so it counts as the form's categorisation. */
  private static FormData submissionWithTopic(String... namesAndValues) {
    FormData formData = submission(namesAndValues);
    FormField topic = new FormField();
    topic.setName("topic");
    topic.setUserValue("Sales & Business Development");
    java.util.Map<String, String> options = new java.util.LinkedHashMap<>();
    options.put("sales-business-development", "Sales & Business Development");
    options.put("general-inquiry", "General Inquiry");
    topic.setListOfOptions(options);
    formData.getFormFieldList().add(topic);
    return formData;
  }

  private static FormData submission(String... namesAndValues) {
    FormData formData = new FormData();
    formData.setFormUniqueId("contact-us");
    List<FormField> fields = new ArrayList<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      FormField field = new FormField();
      field.setName(namesAndValues[i]);
      field.setUserValue(namesAndValues[i + 1]);
      fields.add(field);
    }
    formData.setFormFieldList(fields);
    return formData;
  }

  @Test
  void placeholdersAreReplacedWithTheSubmittedValues() {
    // The point of the feature: someone triaging a mailbox can see what arrived without opening it.
    String subject = FormNotificationSubjectCommand.createSubject(
        "{{topic}} - {{organization}}", submission("topic", "Sales & Business Development",
            "organization", "Acme Defense"), "Contact Us");
    assertEquals("Sales & Business Development - Acme Defense", subject);
  }

  @Test
  void aNewlineInAValueCannotForgeAHeader() {
    // The one that matters. A newline ends the Subject header, and what follows would be read as
    // another header -- including a recipient. This is header injection, from a public form.
    String subject = FormNotificationSubjectCommand.createSubject(
        "Enquiry from {{name}}",
        submission("name", "Chris\r\nBcc: attacker@example.com"), "Contact Us");
    assertFalse(subject.contains("\r"), "a carriage return must not survive into a header");
    assertFalse(subject.contains("\n"), "a line feed must not survive into a header");
    assertTrue(subject.startsWith("Enquiry from Chris"));
    assertTrue(subject.contains("Bcc"), "the text is kept, only its line structure is removed");
  }

  @Test
  void aValueCannotSmuggleTemplateSyntaxOnward() {
    // The resolved subject is handed to a workflow whose own templating uses these braces. A
    // submitted value carrying them must not come back as something to evaluate.
    String subject = FormNotificationSubjectCommand.createSubject(
        "From {{name}}", submission("name", "{{ 7*7 }}"), "Contact Us");
    assertFalse(subject.contains("{{"), "placeholder syntax must not survive substitution");
    assertFalse(subject.contains("}}"), "placeholder syntax must not survive substitution");
  }

  @Test
  void oneLongValueCannotTakeOverTheWholeSubject() {
    String essay = "x".repeat(500);
    String subject = FormNotificationSubjectCommand.createSubject(
        "{{topic}} - {{message}}", submission("topic", "Careers", "message", essay), "Contact Us");
    assertTrue(subject.startsWith("Careers - "), "the leading term survives the long value");
    assertTrue(subject.length() <= 200, "subject length is capped, was " + subject.length());
  }

  @Test
  void aPlaceholderForAFieldTheFormDoesNotHaveDoesNotShipTheLiteral() {
    // An authoring typo. Leaving it in would send "{{compnay}}" to a recipient.
    String subject = FormNotificationSubjectCommand.createSubject(
        "Enquiry from {{compnay}}", submission("organization", "Acme"), "Contact Us");
    assertFalse(subject.contains("compnay"));
    assertFalse(subject.contains("{{"));
  }

  @Test
  void anEmptyValueReadsAsMissingRatherThanAsNothing() {
    String subject = FormNotificationSubjectCommand.createSubject(
        "Enquiry from {{organization}}", submission("organization", "  "), "Contact Us");
    assertEquals("Enquiry from (not given)", subject);
  }

  @Test
  void withNoTemplateTheDefaultNamesTheFormAndWhoItIsFrom() {
    // One convention for every form, so nothing has to be configured for a subject to be useful.
    // "inquiry" rather than "form submitted" is the point: it reads as business, not as a system
    // notice, which is what a recipient triaging a mailbox is scanning for.
    for (String template : Arrays.asList(null, "", "   ")) {
      assertEquals("New Contact Us inquiry - Acme Defense (Sales & Business Development)",
          FormNotificationSubjectCommand.createSubject(template,
              submissionWithTopic("organization", "Acme Defense"), "Contact Us"));
    }
  }

  @Test
  void theDefaultDegradesRatherThanBreakingWhenFieldsAreAbsent() {
    // A form with no organisation, no dropdown, or neither still gets a usable subject. This is
    // what lets one convention serve every form instead of a template per form.
    assertEquals("New Contact Us inquiry - chris@example.com",
        FormNotificationSubjectCommand.createSubject(null,
            submission("email", "chris@example.com"), "Contact Us"));
    assertEquals("New Contact Us inquiry",
        FormNotificationSubjectCommand.createSubject(null, submission(), "Contact Us"));
  }

  @Test
  void theIdentifierPrefersAnOrganisationOverAPersonOverAnAddress() {
    // An organisation identifies a prospect better than a name, and a name better than an address.
    assertTrue(FormNotificationSubjectCommand.createSubject(null,
        submission("organization", "Acme Defense", "name", "Chris", "email", "c@acme.com"),
        "Contact Us").endsWith("Acme Defense"));
    assertTrue(FormNotificationSubjectCommand.createSubject(null,
        submission("name", "Chris", "email", "c@acme.com"), "Contact Us").endsWith("Chris"));
  }

  @Test
  void anUnansweredDropdownIsNotPrintedAsACategory() {
    // "< Please Choose >" means the visitor did not answer. Printing it is worse than printing
    // nothing, and it is exactly what an optional dropdown produces.
    FormData formData = submissionWithTopic("organization", "Acme Defense");
    formData.getFormFieldList().stream()
        .filter(f -> "topic".equals(f.getName())).forEach(f -> f.setUserValue("< Please Choose >"));
    assertEquals("New Contact Us inquiry - Acme Defense",
        FormNotificationSubjectCommand.createSubject(null, formData, "Contact Us"));
  }

  @Test
  void aConfiguredTemplateOverridesTheConvention() {
    // The default is the answer for most forms; a template is the escape hatch for one that needs
    // something else.
    assertEquals("RFI: Acme Defense",
        FormNotificationSubjectCommand.createSubject("RFI: {{organization}}",
            submissionWithTopic("organization", "Acme Defense"), "Contact Us"));
  }

  @Test
  void aTemplateThatResolvesToNothingFallsBackRatherThanSendingPunctuation() {
    // "{{a}} - {{b}}" with both empty would otherwise send " - ". An email with no meaningful
    // subject is worse than a generic one.
    String subject = FormNotificationSubjectCommand.createSubject(
        "{{missingOne}}{{missingTwo}}", submission(), "Contact Us");
    assertEquals("New Contact Us inquiry", subject);
  }

  @Test
  void fieldNamesMatchWithoutRegardToCase() {
    assertEquals("Acme", FormNotificationSubjectCommand.createSubject(
        "{{Organization}}", submission("organization", "Acme"), "Contact Us"));
  }
}
