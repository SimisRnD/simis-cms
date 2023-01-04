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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.presentation.widgets.cms.PreferenceEntriesList;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author matt rajkowski
 * @created 5/9/2022 7:00 AM
 */
class XMLContainerCommandsTest {

  @Test
  void validPreferences() {
    // Typical preferences
    String xmlFragment =
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
            "</widget>";

    // A map will store the preferences
    Map<String, String> preferences = new HashMap<>();

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);

      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = null;
      try (InputStream is = IOUtils.toInputStream(xmlFragment, "UTF-8")) {
        document = builder.parse(is);
      }
      NodeList nodeList = document.getElementsByTagName("widget");
      if (nodeList.getLength() < 0) {
        return;
      }
      XMLContainerCommands.addWidgetPreferences(preferences, nodeList.item(0).getChildNodes());
    } catch (Exception e) {
      Assertions.fail(e.getMessage());
    }

    // Verify
    Assertions.assertEquals(7, preferences.size());

    String data = preferences.get("fields");
    PreferenceEntriesList preferenceEntriesList = new PreferenceEntriesList(data);
    Assertions.assertEquals(6, preferenceEntriesList.size());
  }

  @Test
  void brokenPreferences() {
    // Placeholder is empty
    // Name is empty
    String xmlFragment =
        "<widget name=\"form\">\n" +
            "  <formUniqueId>contact</formUniqueId>\n" +
            "  <useCaptcha>true</useCaptcha>\n" +
            "  <checkForSpam>true</checkForSpam>\n" +
            "  <fields>\n" +
            "    <field name=\"Your first and last name\" placeholder=\"\" value=\"name\" required=\"true\" />\n" +
            "    <field name=\"Name of your organization\" value=\"\" />\n" +
            "    <field name=\"Your email address\" value=\"email\" type=\"email\" required=\"true\" />\n" +
            "    <!--<field name=\"Are you:\" value=\"are-you\" list=\"Patient,Doctor,Lawmaker,Other\" />-->\n" +
            "    <field name=\"An optional phone number we can contact you at\" value=\"phoneNumber\" />\n" +
            "    <field name=\"Who would you like to contact?\" value=\"who\" list=\"Sales,Marketing,Business Development,Contracts,Technical,Security,Other\" />\n" +
            "    <field name=\"How Can We Help?\" value=\"comments\" type=\"textarea\" placeholder=\"Your message\" required=\"true\" />\n" +
            "  </fields>\n" +
            "  <buttonName>Contact Me</buttonName>\n" +
            "  <successMessage><![CDATA[Thanks! We normally respond within 24-48 hours.]]></successMessage>\n" +
            "  <emailTo>inquiries@example.com</emailTo>\n" +
            "</widget>";

    // A map will store the preferences
    Map<String, String> preferences = new HashMap<>();

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);

      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = null;
      try (InputStream is = IOUtils.toInputStream(xmlFragment, "UTF-8")) {
        document = builder.parse(is);
      }
      NodeList nodeList = document.getElementsByTagName("widget");
      if (nodeList.getLength() < 0) {
        return;
      }
      XMLContainerCommands.addWidgetPreferences(preferences, nodeList.item(0).getChildNodes());
    } catch (Exception e) {
      Assertions.fail(e.getMessage());
    }

    // Verify
    Assertions.assertEquals(7, preferences.size());

    String data = preferences.get("fields");
    PreferenceEntriesList preferenceEntriesList = new PreferenceEntriesList(data);
    Assertions.assertEquals(6, preferenceEntriesList.size());
  }
}