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

package com.simisinc.platform.presentation.controller.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static com.simisinc.platform.presentation.controller.cms.MenuWidget.JSP;
import static org.mockito.Mockito.mockStatic;

/**
 * @author matt rajkowski
 * @created 5/4/2022 7:00 PM
 */
class MenuWidgetTest extends WidgetBase {

  //  <widget name="menu">
  //     <class>vertical</class>
  //     <links>
  //       <link name="Contact Us" link="/contact-us" />
  //       <link name="Login" link="/login" role="guest" rule="site.login" />
  //       <link name="Register" link="/login" role="guest" rule="site.registrations" />
  //       <link name="My Account" link="/my-page" role="users" />
  //       <link name="Log Out" link="/logout" role="users" />
  //     </links>
  //   </widget>

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("showWhenEmpty", "false");
    preferences.put("class", "vertical");
    preferences.put("links",
        "link|/login||name|Login||role|guest||rule|site.login|||" +
            "link|/login||name|Register||role|guest||rule|site.registrations|||" +
            "link|/my-page||name|My Profile||role|users|||" +
            "link|/logout||name|Log Out||role|users");

    try (MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      property.when(() -> LoadSitePropertyCommand.loadByName("site.login")).thenReturn("true");
      property.when(() -> LoadSitePropertyCommand.loadByName("site.registrations")).thenReturn("false");

      {
        // Execute the widget
        MenuWidget widget = new MenuWidget();
        widget.execute(widgetContext);
        List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

        // Verify the result
        Assertions.assertEquals(2, linkList.size());
        Assertions.assertEquals(JSP, widgetContext.getJsp());
      }

      // Log the user out
      logout(widgetContext);

      {
        // Execute the widget
        MenuWidget widget = new MenuWidget();
        widget.execute(widgetContext);
        List<Map<String, String>> linkList = (List) widgetContext.getRequest().getAttribute("linkList");

        Assertions.assertEquals(1, linkList.size());
        Assertions.assertEquals(JSP, widgetContext.getJsp());
      }
    }
  }
}