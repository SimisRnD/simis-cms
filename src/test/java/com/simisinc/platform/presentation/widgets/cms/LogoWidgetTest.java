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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Tests for LogoWidget, covering the "view" and "colorProperty" widget preferences that
 * cms/logo.jsp reads via request attributes, and issue #1287-adjacent request-attribute hygiene
 * (a stale attribute from an earlier logo widget in the same request must not leak into a later
 * one that doesn't set its own).
 *
 * @author elizabeth houser
 */
class LogoWidgetTest extends WidgetBase {

  private static MockedStatic<LoadSitePropertyCommand> mockLoadSiteProperty() {
    MockedStatic<LoadSitePropertyCommand> mocked = mockStatic(LoadSitePropertyCommand.class);
    mocked.when(() -> LoadSitePropertyCommand.loadAsMap("system")).thenReturn(new HashMap<>());
    mocked.when(() -> LoadSitePropertyCommand.loadAsMap("site")).thenReturn(new HashMap<>());
    mocked.when(() -> LoadSitePropertyCommand.loadAsMap("theme")).thenReturn(new HashMap<>());
    return mocked;
  }

  @Test
  void executeSetsTheViewAttributeWhenTheViewPreferenceIsSet() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockLoadSiteProperty()) {
      Map<String, String> preferences = new HashMap<>();
      preferences.put("view", "color");
      widgetContext.setPreferences(preferences);

      new LogoWidget().execute(widgetContext);

      assertEquals("color", request.getAttribute("view"));
      assertNull(request.getAttribute("logoColorProperty"));
    }
  }

  @Test
  void executeSetsTheLogoColorPropertyAttributeWhenTheColorPropertyPreferenceIsSetAndViewIsNot() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockLoadSiteProperty()) {
      Map<String, String> preferences = new HashMap<>();
      preferences.put("colorProperty", "theme.footer.logo.color");
      widgetContext.setPreferences(preferences);

      new LogoWidget().execute(widgetContext);

      assertEquals("theme.footer.logo.color", request.getAttribute("logoColorProperty"));
      assertNull(request.getAttribute("view"));
    }
  }

  @Test
  void executeSetsTheLogoColorPropertyDarkAttributeWhenTheColorPropertyDarkPreferenceIsSetAndViewIsNot() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockLoadSiteProperty()) {
      Map<String, String> preferences = new HashMap<>();
      preferences.put("colorPropertyDark", "theme.footer.logo.color.dark");
      widgetContext.setPreferences(preferences);

      new LogoWidget().execute(widgetContext);

      assertEquals("theme.footer.logo.color.dark", request.getAttribute("logoColorPropertyDark"));
      assertNull(request.getAttribute("view"));
      assertNull(request.getAttribute("logoColorProperty"));
    }
  }

  @Test
  void executeSetsBothColorPropertyAttributesWhenBothPreferencesAreSet() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockLoadSiteProperty()) {
      // The real shape footer-layout.xml's logo widget passes: both a light and a dark theme
      // property, naming the footer's own pair rather than the header's defaults.
      Map<String, String> preferences = new HashMap<>();
      preferences.put("colorProperty", "theme.footer.logo.color");
      preferences.put("colorPropertyDark", "theme.footer.logo.color.dark");
      widgetContext.setPreferences(preferences);

      new LogoWidget().execute(widgetContext);

      assertEquals("theme.footer.logo.color", request.getAttribute("logoColorProperty"));
      assertEquals("theme.footer.logo.color.dark", request.getAttribute("logoColorPropertyDark"));
    }
  }

  @Test
  void executeSetsNeitherAttributeWhenNeitherPreferenceIsSet() {
    try (MockedStatic<LoadSitePropertyCommand> ignored = mockLoadSiteProperty()) {
      widgetContext.setPreferences(new HashMap<>());

      new LogoWidget().execute(widgetContext);

      assertNull(request.getAttribute("view"));
      assertNull(request.getAttribute("logoColorProperty"));
      assertNull(request.getAttribute("logoColorPropertyDark"));
    }
  }

  // The test that used to sit here asserted that the footer's logo widget does not inherit a view
  // the header's logo widget set earlier in the same request. It passed for the wrong reason: it
  // shared one mock request across both executions with no container in between, so it was really
  // testing the removeAttribute calls the widget carried, not the behaviour. The behaviour is the
  // container's -- WebContainerCommand clears every non-page-level attribute before each widget --
  // and it is now asserted where it lives, in
  // WebContainerCommandTest.theResetLoopRemovesAWidgetsLeftoversBeforeTheNextWidgetRuns.
}
