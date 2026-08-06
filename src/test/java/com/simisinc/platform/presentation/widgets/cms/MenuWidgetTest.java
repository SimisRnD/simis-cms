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
import com.simisinc.platform.application.cms.LoadTableOfContentsCommand;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.presentation.controller.RequestConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
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

  @Test
  void tableOfContentsLinkWithAttributeBreakoutPayloadIsNeutralized() {
    // menu.jsp renders href="${ctx}${link['link']}" -- unlike the widget-preference <link> path
    // (entriesList loop in MenuWidget.execute(), which already calls UrlCommand.sanitizeUrl()), the
    // private addLink() helper used for tocUniqueId table-of-contents entries did NOT sanitize the
    // link value before this test's fix, so a stored TOC entry containing a double-quote and an
    // onmouseover payload would have been forwarded into linkList verbatim and broken out of the
    // href attribute at render time. addLink() now runs the link through the same
    // UrlCommand.sanitizeUrl() call, which rejects (returns null for) any value carrying a
    // character outside its href-safe set -- including '"' -- so the payload never reaches the map
    // that menu.jsp renders from.
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"menu\">\n" +
            "  <tocUniqueId>toc-xss-test</tocUniqueId>\n" +
            "</widget>");

    request.setAttribute(RequestConstants.WEB_PAGE_PATH, "/");

    TableOfContents tableOfContents = new TableOfContents();
    tableOfContents.setTocUniqueId("toc-xss-test");
    List<TableOfContentsLink> entries = new ArrayList<>();
    entries.add(new TableOfContentsLink("Safe Link", "/help"));
    entries.add(new TableOfContentsLink("XSS", "\" onmouseover=\"alert(document.cookie)"));
    tableOfContents.setEntries(entries);

    try (MockedStatic<LoadTableOfContentsCommand> tocCommand = mockStatic(LoadTableOfContentsCommand.class)) {
      tocCommand.when(() -> LoadTableOfContentsCommand.loadByUniqueId("toc-xss-test", false)).thenReturn(tableOfContents);

      MenuWidget widget = new MenuWidget();
      widget.execute(widgetContext);
      List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

      Assertions.assertEquals(2, linkList.size());

      // The legitimate entry is untouched
      Map<String, String> safeLink = linkList.stream()
          .filter(link -> "Safe Link".equals(link.get("name")))
          .findFirst()
          .orElse(null);
      Assertions.assertNotNull(safeLink);
      Assertions.assertEquals("/help", safeLink.get("link"));

      // The malicious entry's link value never reaches the rendered map -- it is stripped (null),
      // never the raw payload that could break out of the href attribute in menu.jsp
      Map<String, String> xssLink = linkList.stream()
          .filter(link -> "XSS".equals(link.get("name")))
          .findFirst()
          .orElse(null);
      Assertions.assertNotNull(xssLink);
      String renderedLink = xssLink.get("link");
      Assertions.assertTrue(renderedLink == null || (!renderedLink.contains("\"") && !renderedLink.contains("onmouseover")),
          "A link value containing '\"' and an event-handler payload must never reach the href attribute unescaped, but got: " + renderedLink);
    }
  }
}
