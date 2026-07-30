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
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/4/2022 7:00 PM
 */
class MenuWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"menu\">\n" +
            "  <class>vertical</class>\n" +
            "  <showWhenEmpty>false</showWhenEmpty>\n" +
            "  <links>\n" +
            "    <link name=\"Contact Us\" link=\"/contact-us\" />\n" +
            "    <link name=\"Login\" link=\"/login\" role=\"guest\" rule=\"site.login\" />\n" +
            "    <link name=\"Register\" link=\"/login\" role=\"guest\" rule=\"site.registrations\" />\n" +
            "    <link name=\"My Account\" link=\"/my-page\" role=\"users\" />\n" +
            "    <link name=\"Admin\" link=\"/admin\" role=\"admin\" />\n" +
            "    <link name=\"Log Out\" link=\"/logout\" role=\"users\" />\n" +
            "  </links>\n" +
            "</widget>");

    // Set the page the user is on
    request.setAttribute(RequestConstants.WEB_PAGE_PATH, "/");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("site.login")).thenReturn("true");
      property.when(() -> LoadSitePropertyCommand.loadByName("site.registrations")).thenReturn("false");

      {
        // Execute the widget
        MenuWidget widget = new MenuWidget();
        widget.execute(widgetContext);
        List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

        // Verify the result
        Assertions.assertEquals(3, linkList.size());
        Assertions.assertEquals(MenuWidget.JSP, widgetContext.getJsp());

        // Upgrade the user to Admin
        setRoles(widgetContext, ADMIN);

        widget.execute(widgetContext);
        linkList = (List) widgetContext.getRequest().getAttribute("linkList");

        // Verify the result
        Assertions.assertEquals(4, linkList.size());
        Assertions.assertEquals(MenuWidget.JSP, widgetContext.getJsp());

      }

      // Log the user out
      logout(widgetContext);

      {
        // Execute the widget
        MenuWidget widget = new MenuWidget();
        widget.execute(widgetContext);
        List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

        Assertions.assertEquals(2, linkList.size());
        Assertions.assertEquals(MenuWidget.JSP, widgetContext.getJsp());
      }
    }
  }

  @Test
  void logoutLinkFromTheHeaderLayoutXmlIncludesTheCsrfToken() {
    // WebRequestFilter requires a "token" query param matching the session's formToken before it
    // will process /logout (GH-359). menu.jsp renders link['link'] verbatim into href, so a plain
    // "/logout" reaching the page here would silently fail to log anyone out.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"menu\">\n" +
            "  <links>\n" +
            "    <link name=\"Log Out\" link=\"/logout\" role=\"users\" />\n" +
            "  </links>\n" +
            "</widget>");

    request.setAttribute(RequestConstants.WEB_PAGE_PATH, "/");
    String expectedToken = widgetContext.getUserSession().getFormToken();

    MenuWidget widget = new MenuWidget();
    widget.execute(widgetContext);
    List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

    Assertions.assertEquals(1, linkList.size());
    Assertions.assertEquals("/logout?token=" + expectedToken, linkList.get(0).get("link"));
  }

  @Test
  void adminDropdownLogoutLinkAlsoIncludesTheCsrfToken() {
    // The Admin dropdown's Logout entry is injected internally (MenuWidget.execute(), the
    // "type=admin" branch) rather than read from widget preferences -- it needs the same token.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"menu\">\n" +
            "  <links>\n" +
            "    <link name=\"Settings\" icon=\"fa-cog\" icon-only=\"true\" type=\"admin\" />\n" +
            "  </links>\n" +
            "</widget>");

    request.setAttribute(RequestConstants.WEB_PAGE_PATH, "/");
    setRoles(widgetContext, ADMIN);
    String expectedToken = widgetContext.getUserSession().getFormToken();

    MenuWidget widget = new MenuWidget();
    widget.execute(widgetContext);
    List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

    Map<String, String> logoutLink = linkList.stream()
        .filter(link -> "Logout".equals(link.get("name")))
        .findFirst()
        .orElse(null);
    Assertions.assertNotNull(logoutLink, "Logout link missing from the admin dropdown");
    Assertions.assertEquals("/logout?token=" + expectedToken, logoutLink.get("link"));
  }
}
