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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.admin.SendDataManagerEmailCommand;
import com.simisinc.platform.application.email.EmailCommand;
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.model.CustomField;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Category;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.mail.ImageHtmlEmail;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.WordUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/31/18 8:27 AM
 */
public class ItemCommand {

  private static Log LOG = LogFactory.getLog(ItemCommand.class);

  public static String name(Long itemId) {
    Item item = LoadItemCommand.loadItemById(itemId);
    if (item == null) {
      return null;
    }
    return item.getName();
  }

  public static String name(Item item) {
    if (item == null) {
      return null;
    }
    return item.getName();
  }

  public static Item findById(Long id) {
    return LoadItemCommand.loadItemById(id);
  }

  public static boolean hasViewPermission(Item item, User user) {
    return CheckItemPermissionCommand.userHasViewPermission(item, user);
  }

  // @todo send using a workflow email
  public static void sendEmail(Item item, String emailAddresses) {

    LOG.debug("Sending item needs approval email...");

    // Email subject
    String subject = "New listing - " + WordUtils.capitalizeFully(item.getName(), ' ', '-');

    // Email body
    StringBuilder textSb = new StringBuilder();
    StringBuilder listSb = new StringBuilder();

    // Details
    appendEmailValue(textSb, listSb, "Name", item.getName());
    appendEmailValue(textSb, listSb, "Address", item.getAddress());
    appendEmailValue(textSb, listSb, "Web Site", item.getUrl());
    appendEmailValue(textSb, listSb, "Phone Number", item.getPhoneNumber());

    // Categories
    Category mainCategory = LoadCategoryCommand.loadCategoryById(item.getCategoryId());
    if (mainCategory != null) {
      appendEmailValue(textSb, listSb, "Category", mainCategory.getName());
    }
    Long[] categories = item.getCategoryIdList();
    if (categories != null && categories.length > 0) {
      StringBuilder categorySb = new StringBuilder();
      for (long categoryId : categories) {
        Category thisCategory = LoadCategoryCommand.loadCategoryById(categoryId);
        if (thisCategory != null) {
          if (categorySb.length() > 0) {
            categorySb.append(", ");
          }
          categorySb.append(thisCategory.getName());
        }
      }
      appendEmailValue(textSb, listSb, "Category List", categorySb.toString());
    }

    // Description
    appendEmailValue(textSb, listSb, "Description", item.getSummary());

    // Contact Info
    if (item.getCustomFieldList() != null) {
      for (CustomField customField : item.getCustomFieldList().values()) {
        appendEmailValue(textSb, listSb, customField.getName(), customField.getValue());
      }
    }

    // Approval Info
    appendEmailValue(textSb, listSb, "Needs approval?", item.getApproved() == null ? "Yes" : "No");

    // HTML and Text bodies
    String html = "<html>" +
        "<p>The following information was submitted:</p>" +
        "<ul>" + listSb.toString() + "</ul>" +
        "<small>Url: " + StringEscapeUtils.escapeXml11(item.getSource()) + "</small><br />" +
        "<small>IP: " + item.getIpAddress() + " ("
        + StringEscapeUtils.escapeXml11(GeoIPCommand.getCityStateCountryLocation(item.getIpAddress())) + ")</small>" +
        "</html>";

    String text = "The following information was submitted:\n" +
        "\n" +
        textSb.toString() + "\n" +
        "Url: " + item.getSource() + "\n" +
        "IP: " + item.getIpAddress() + " (" + GeoIPCommand.getCityStateCountryLocation(item.getIpAddress()) + ")" + "\n"
        +
        "\n";

    // Send the email
    if (StringUtils.isNotBlank(emailAddresses)) {
      try {
        ImageHtmlEmail email = EmailCommand.prepareNewEmail();
        String[] listOfEmails = emailAddresses.split(",");
        for (String thisEmail : listOfEmails) {
          email.addTo(thisEmail.trim());
        }
        email.setSubject(subject);
        //        email.addPart(html, "text/html;charset=UTF-8");
        email.setHtmlMsg(html);
        email.setTextMsg(text);
        email.send();
        return;
      } catch (Exception e) {
        LOG.error("sendMessage could not send mail", e);
      }
    }

    // Default to the data managers (and if there was an error)
    SendDataManagerEmailCommand.sendMessage(subject, html, text);
  }

  private static void appendEmailValue(StringBuilder textSb, StringBuilder listSb, String label, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    if (textSb.length() > 0) {
      textSb.append("\n");
    }
    if (label.endsWith("?")) {
      textSb.append(label).append(" ").append(value);
    } else {
      textSb.append(label).append(": ").append(value);
    }
    listSb.append("<li>").append(StringEscapeUtils.escapeXml11(label)).append(": ")
        .append(StringEscapeUtils.escapeXml11(value)).append("</li>");
  }
}
