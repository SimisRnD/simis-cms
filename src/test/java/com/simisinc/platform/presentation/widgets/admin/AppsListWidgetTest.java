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

import com.simisinc.platform.WidgetBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * NOTE: despite this class's name, it exercises {@link ApisListWidget} (the admin /admin/apis
 * page listing REST endpoints), not the separate {@code AppsListWidget} class (the admin /admin/apps
 * page listing registered API client apps) -- that mismatch predates this change and is left as-is
 * here since renaming/relocating it is outside this change's scope; {@code AppsListWidget} itself
 * currently has no test coverage under this name.
 *
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class AppsListWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"apisList\">\n" +
        "  <title>APIs</title>\n" +
        "</widget>");

    // Execute the widget
    ApisListWidget widget = new ApisListWidget();
    widget.execute(widgetContext);

    // Verify
    Assertions.assertEquals(ApisListWidget.JSP, widgetContext.getJsp());
    Assertions.assertEquals("APIs", request.getAttribute("title"));

    List apiListRequest = (List) request.getAttribute("apiList");
    Assertions.assertNotNull(apiListRequest);
  }

  @Test
  void includesTheSyntheticOauth2AuthorizeRow() {
    // POST /api/oauth2/authorize (username/password -> bearer token) is handled as hardcoded
    // logic directly inside RestRequestFilter rather than a declared <service> entry in
    // rest-services.xml, so the XML scan ApisListWidget otherwise relies on never finds it. It's
    // the endpoint this admin page's own worked example is built around, so the widget adds it as
    // a synthetic row -- this asserts that row actually reaches the JSP's model.
    addPreferencesFromWidgetXml(widgetContext, "<widget name=\"apisList\">\n" +
        "  <title>APIs</title>\n" +
        "</widget>");

    ApisListWidget widget = new ApisListWidget();
    widget.execute(widgetContext);

    @SuppressWarnings("unchecked")
    List<Map<String, String>> apiListRequest = (List<Map<String, String>>) request.getAttribute("apiList");
    Assertions.assertNotNull(apiListRequest);

    boolean found = apiListRequest.stream().anyMatch(service ->
        "post".equals(service.get("method")) && "oauth2/authorize".equals(service.get("endpointValue")));
    Assertions.assertTrue(found, "Expected a synthetic POST oauth2/authorize row in the api list");
  }
}