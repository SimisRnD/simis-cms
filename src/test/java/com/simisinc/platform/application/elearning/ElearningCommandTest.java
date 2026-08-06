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

package com.simisinc.platform.application.elearning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Covers the master "Enable e-learning?" switch actually gating the three per-integration checks
 * -- previously {@code elearning.enabled} was read by {@link ElearningCommand#isEnabled()} but
 * never consulted by isLRSEnabled()/isMoodleEnabled()/isPERLSEnabled(), so turning it off had no
 * effect on an already-configured integration.
 *
 * @author SimIS Inc.
 */
class ElearningCommandTest {

  private static final Map<String, String> FULLY_CONFIGURED_MOODLE = Map.of(
      "elearning.moodle.enabled", "true",
      "elearning.moodle.url", "https://moodle.example.com",
      "elearning.moodle.token", "a-token");

  private static final Map<String, String> FULLY_CONFIGURED_LRS = Map.of(
      "elearning.xapi.enabled", "true",
      "elearning.lrs.url", "https://lrs.example.com",
      "elearning.lrs.key", "a-key",
      "elearning.lrs.secret", "a-secret");

  private static final Map<String, String> FULLY_CONFIGURED_PERLS = Map.of(
      "elearning.perls.enabled", "true",
      "elearning.perls.url", "https://perls.example.com",
      "elearning.perls.clientId", "a-client-id",
      "elearning.perls.secret", "a-secret");

  private MockedStatic<LoadSitePropertyCommand> properties(boolean masterEnabled, Map<String, String> rest) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    Map<String, String> all = new HashMap<>(rest);
    all.put("elearning.enabled", String.valueOf(masterEnabled));
    for (Map.Entry<String, String> entry : all.entrySet()) {
      m.when(() -> LoadSitePropertyCommand.loadByName(eq(entry.getKey()))).thenReturn(entry.getValue());
      m.when(() -> LoadSitePropertyCommand.loadByName(eq(entry.getKey()), org.mockito.ArgumentMatchers.anyString()))
          .thenReturn(entry.getValue());
    }
    return m;
  }

  @Test
  void masterSwitchOffDisablesAFullyConfiguredMoodleIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(false, FULLY_CONFIGURED_MOODLE)) {
      assertFalse(ElearningCommand.isMoodleEnabled());
    }
  }

  @Test
  void masterSwitchOffDisablesAFullyConfiguredLRSIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(false, FULLY_CONFIGURED_LRS)) {
      assertFalse(ElearningCommand.isLRSEnabled());
    }
  }

  @Test
  void masterSwitchOffDisablesAFullyConfiguredPERLSIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(false, FULLY_CONFIGURED_PERLS)) {
      assertFalse(ElearningCommand.isPERLSEnabled());
    }
  }

  @Test
  void masterSwitchOnAllowsAFullyConfiguredMoodleIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(true, FULLY_CONFIGURED_MOODLE)) {
      assertTrue(ElearningCommand.isMoodleEnabled());
    }
  }

  @Test
  void masterSwitchOnAllowsAFullyConfiguredLRSIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(true, FULLY_CONFIGURED_LRS)) {
      assertTrue(ElearningCommand.isLRSEnabled());
    }
  }

  @Test
  void masterSwitchOnAllowsAFullyConfiguredPERLSIntegration() {
    try (MockedStatic<LoadSitePropertyCommand> m = properties(true, FULLY_CONFIGURED_PERLS)) {
      assertTrue(ElearningCommand.isPERLSEnabled());
    }
  }
}
