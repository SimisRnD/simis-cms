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

package com.simisinc.platform.application.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Date;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * @author matt rajkowski
 * @created 4/30/18 8:56 AM
 */
class LocalDateCommandTest {

  // 2026-01-15T02:30:00Z is 2026-01-14 21:30 in America/New_York (EST, UTC-5, no DST in
  // January) -- a date near local midnight that lands on the previous UTC day, used to verify
  // conversion follows the site's configured zone rather than the instant's UTC calendar day.
  private static final Date NEAR_LOCAL_MIDNIGHT = Date.from(Instant.parse("2026-01-15T02:30:00Z"));

  @Test
  void convertToLocalDateUsesTheSiteConfiguredZoneNotJvmDefault() {
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      LocalDate localDate = LocalDateCommand.convertToLocalDate(NEAR_LOCAL_MIDNIGHT);
      Assertions.assertEquals(LocalDate.of(2026, 1, 14), localDate);
    }
  }

  @Test
  void convertToLocalTimeUsesTheSiteConfiguredZoneNotJvmDefault() {
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any())).thenReturn("America/New_York");
      LocalTime localTime = LocalDateCommand.convertToLocalTime(NEAR_LOCAL_MIDNIGHT);
      Assertions.assertEquals(LocalTime.of(21, 30), localTime);
    }
  }

  @Test
  void convertToLocalDateFallsBackToJvmDefaultWhenSiteTimezoneIsUnset() {
    try (MockedStatic<LoadSitePropertyCommand> siteProps = mockStatic(LoadSitePropertyCommand.class)) {
      // loadByName(name, defaultValue) falls back to the passed-through default when unset
      siteProps.when(() -> LoadSitePropertyCommand.loadByName(eq("site.timezone"), any()))
          .thenAnswer(invocation -> invocation.getArgument(1));
      LocalDate expected = NEAR_LOCAL_MIDNIGHT.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
      Assertions.assertEquals(expected, LocalDateCommand.convertToLocalDate(NEAR_LOCAL_MIDNIGHT));
    }
  }
}
