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

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.persistence.cms.FormDataRepository;
import com.simisinc.platform.infrastructure.workflow.WorkflowManager;
import com.simisinc.platform.presentation.controller.SessionConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static com.simisinc.platform.presentation.widgets.cms.FormWidget.JSP;
import static com.simisinc.platform.presentation.widgets.cms.FormWidget.SUCCESS_JSP;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class FormWidgetTest extends WidgetBase {

  public void initCommonPreferences() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"form\">\n" +
            "  <formUniqueId>contact</formUniqueId>\n" +
            "  <useCaptcha>true</useCaptcha>\n" +
            "  <checkForSpam>true</checkForSpam>\n" +
            "  <fields>\n" +
            "    <field name=\"Your first and last name\" value=\"name\" required=\"true\" />\n" +
            "    <field name=\"Name of your organization\" value=\"organization\" />\n" +
            "    <field name=\"Your email address\" value=\"email\" type=\"email\" required=\"true\" />\n" +
            "    <!--<field name=\"Are you:\" value=\"are-you\" list=\"Patient,Doctor,Lawmaker,Other\" />-->\n" +
            "    <field name=\"An optional phone number we can contact you at\" value=\"phoneNumber\" />\n" +
            "    <field name=\"Who would you like to contact?\" value=\"who\" list=\"Sales,Marketing,Business Development,Contracts,Technical,Security,Other\" />\n" +
            "    <field name=\"How Can We Help?\" value=\"comments\" type=\"textarea\" placeholder=\"Your message\" required=\"true\" />\n" +
            "  </fields>\n" +
            "  <buttonName>Contact Me</buttonName>\n" +
            "  <successMessage><![CDATA[Thanks! We normally respond within 24-48 hours.]]></successMessage>\n" +
            "  <emailTo>inquiries@example.com</emailTo>\n" +
            "</widget>");
  }

  @Test
  void executeFormDisplay() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Shows the form
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      FormWidget widget = new FormWidget();
      widget.execute(widgetContext);
      // Verify the output
      Assertions.assertEquals(JSP, widgetContext.getJsp());

      List<FormField> formFieldList = (List) widgetContext.getRequest().getAttribute("formFieldList");
      Assertions.assertEquals(6, formFieldList.size());

      FormField formFieldName = formFieldList.get(0);
      Assertions.assertEquals("Your first and last name", formFieldName.getLabel());
      Assertions.assertEquals("name", formFieldName.getName());
      Assertions.assertNull(formFieldName.getType());
      Assertions.assertTrue(formFieldName.isRequired());

      FormField formFieldOrganization = formFieldList.get(1);
      Assertions.assertEquals("Name of your organization", formFieldOrganization.getLabel());
      Assertions.assertEquals("organization", formFieldOrganization.getName());
      Assertions.assertNull(formFieldOrganization.getType());
      Assertions.assertFalse(formFieldOrganization.isRequired());

      FormField formFieldReason = formFieldList.get(5);
      Assertions.assertEquals("How Can We Help?", formFieldReason.getLabel());
      Assertions.assertEquals("comments", formFieldReason.getName());
      Assertions.assertEquals("textarea", formFieldReason.getType());
      Assertions.assertTrue(formFieldReason.isRequired());
    }
  }

  @Test
  void executeFormDisplayError() {
    // Add the form data which has error
    FormData formData = new FormData();
    widgetContext.setRequestObject(formData);

    // Show a form Error
    FormWidget widget = new FormWidget();
    widget.execute(widgetContext);
    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }

  @Test
  void executeFormDisplaySuccess() {
    // Uses the posted success status
    widgetContext.addSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess", "true");

    // Show the form submit success message
    FormWidget widget = new FormWidget();
    widget.execute(widgetContext);
    Assertions.assertEquals(SUCCESS_JSP, widgetContext.getJsp());
  }

  @Test
  void postSuccess() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Set the session values
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");

    // Set the request values
    request.setAttribute("captcha", "G1B8A");
    request.setAttribute(widgetContext.getUniqueId() + "name", "First Last");
    request.setAttribute(widgetContext.getUniqueId() + "organization", "Organization");
    request.setAttribute(widgetContext.getUniqueId() + "email", "email@example.com");
    request.setAttribute(widgetContext.getUniqueId() + "comments", "These are my comments.");

    // Execute the widget
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      try (MockedStatic<FormDataRepository> formDataRepositoryMockedStatic = mockStatic(FormDataRepository.class)) {
        formDataRepositoryMockedStatic.when(() -> FormDataRepository.save(any())).thenReturn(new FormData());

        try (MockedStatic<WorkflowManager> workflowManagerMockedStatic = mockStatic(WorkflowManager.class)) {

          // Execute
          FormWidget widget = new FormWidget();
          WidgetContext result = widget.post(widgetContext);

          // Verify
          Assertions.assertNull(result);
          Assertions.assertNull(widgetContext.getWarningMessage());
          Assertions.assertNull(widgetContext.getErrorMessage());
          workflowManagerMockedStatic.verify(() -> WorkflowManager.triggerWorkflowForEvent(any()));
          Assertions.assertEquals("true", widgetContext.getSharedRequestValue(widgetContext.getUniqueId() + "formWidgetSuccess"));
        }
      }
    }
  }

  @Test
  void postError() {
    // Set widget preferences
    initCommonPreferences();
    Assertions.assertEquals(7, widgetContext.getPreferences().size());

    // Set the session values
    session.setAttribute(SessionConstants.CAPTCHA_TEXT, "G1B8A");

    // Set the request values
    request.setAttribute("captcha", "G1B8A");
    request.setAttribute(widgetContext.getUniqueId() + "name", "First Last");
    request.setAttribute(widgetContext.getUniqueId() + "organization", "Organization");

    // Execute the widget
    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.service")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.sitekey")).thenReturn(null);
      property.when(() -> LoadSitePropertyCommand.loadByName("captcha.google.secretkey")).thenReturn(null);

      // Execute
      FormWidget widget = new FormWidget();
      WidgetContext result = widget.post(widgetContext);

      // Verify
      Assertions.assertNotNull(result);
      Assertions.assertEquals("How Can We Help? is required", widgetContext.getWarningMessage());
      Assertions.assertNull(widgetContext.getErrorMessage());
    }
  }
}