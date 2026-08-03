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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Tests {@link FeatureFlagCommand}, the {@code features.*} site-property wrapper (issue #410)
 *
 * @author elizabeth houser
 */
class FeatureFlagCommandTest {

  @Test
  void enabledFlagReturnsTrue() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(true);
      assertTrue(FeatureFlagCommand.isEnabled("layout-editor"));
    }
  }

  @Test
  void disabledFlagReturnsFalse() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.layout-editor")).thenReturn(false);
      assertFalse(FeatureFlagCommand.isEnabled("layout-editor"));
    }
  }

  @Test
  void aNeverSeededFlagDefaultsToFalse() {
    // LoadSitePropertyCommand.loadByNameAsBoolean already returns false for a missing property (its
    // loadByName returns null, and "true".equals(null) is false) -- confirm isEnabled() inherits that,
    // rather than silently failing open for a flag nobody seeded.
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("features.never-seeded")).thenReturn(false);
      assertFalse(FeatureFlagCommand.isEnabled("never-seeded"));
    }
  }

  @Test
  void blankOrNullNameIsFalseWithoutConsultingSiteProperties() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      assertFalse(FeatureFlagCommand.isEnabled(null));
      assertFalse(FeatureFlagCommand.isEnabled(""));
      assertFalse(FeatureFlagCommand.isEnabled("   "));
      m.verify(() -> LoadSitePropertyCommand.loadByNameAsBoolean(org.mockito.ArgumentMatchers.anyString()), never());
    }
  }

  @Test
  void isEnabledPrefixesTheNameWithFeatures() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      FeatureFlagCommand.isEnabled("item-tags-facet-search");
      m.verify(() -> LoadSitePropertyCommand.loadByNameAsBoolean(eq("features.item-tags-facet-search")));
    }
  }

  @Test
  void getActiveFlagNamesReturnsOnlyTrueFlagsSortedWithoutThePrefix() {
    Map<String, String> stored = new LinkedHashMap<>();
    stored.put("features.layout-editor", "true");
    stored.put("features.item-tags-facet-search", "false");
    stored.put("features.a-second-enabled-flag", "true");

    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadNonEmptyAsMap("features")).thenReturn(stored);

      List<String> active = FeatureFlagCommand.getActiveFlagNames();

      assertEquals(List.of("a-second-enabled-flag", "layout-editor"), active);
    }
  }

  @Test
  void getActiveFlagNamesIsEmptyWhenNothingIsSeededYet() {
    try (MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class)) {
      m.when(() -> LoadSitePropertyCommand.loadNonEmptyAsMap("features")).thenReturn(null);
      assertTrue(FeatureFlagCommand.getActiveFlagNames().isEmpty());
    }
  }
}
