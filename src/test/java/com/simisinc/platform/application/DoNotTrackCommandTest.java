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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Tests honoring of the Do-Not-Track and Global Privacy Control signals
 *
 * @author elizabeth houser
 */
class DoNotTrackCommandTest {

  private MockedStatic<LoadSitePropertyCommand> honoring(boolean enabled) {
    MockedStatic<LoadSitePropertyCommand> m = mockStatic(LoadSitePropertyCommand.class);
    m.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean("analytics.honorDnt")).thenReturn(enabled);
    return m;
  }

  @Test
  void whenHonoringIsDisabledNothingIsDoNotTrack() {
    try (MockedStatic<LoadSitePropertyCommand> m = honoring(false)) {
      // Even a DNT/GPC signal is ignored when honoring is off (default behavior)
      assertFalse(DoNotTrackCommand.isDoNotTrack("1", "1"));
    }
  }

  @Test
  void whenHonoringIsEnabledTheSignalsAreRespected() {
    try (MockedStatic<LoadSitePropertyCommand> m = honoring(true)) {
      assertTrue(DoNotTrackCommand.isDoNotTrack("1", null), "DNT: 1");
      assertTrue(DoNotTrackCommand.isDoNotTrack(null, "1"), "Sec-GPC: 1");
      assertFalse(DoNotTrackCommand.isDoNotTrack(null, null), "no signal");
      assertFalse(DoNotTrackCommand.isDoNotTrack("0", "0"), "explicit not-set");
    }
  }
}
