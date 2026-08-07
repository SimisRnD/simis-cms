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

package com.simisinc.platform.application.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

/**
 * Tests the eventType -> plain-language phrase mapping behind the activity feed (issue #1006).
 *
 * @author SimIS Inc.
 */
class DescribeAuditEventCommandTest {

  @Test
  void mapsAKnownEventTypeFromEveryCategory() {
    assertEquals("logged in", DescribeAuditEventCommand.describe("authentication.login.success"));
    assertEquals("created a user account", DescribeAuditEventCommand.describe("user.create"));
    assertEquals("granted a capability to a user", DescribeAuditEventCommand.describe("capability_grant.grant"));
    assertEquals("updated a site setting", DescribeAuditEventCommand.describe("setting.update"));
    assertEquals("published a page", DescribeAuditEventCommand.describe("content.publish"));
    assertEquals("exported data", DescribeAuditEventCommand.describe("data.export"));
  }

  @Test
  void trimsWhitespaceBeforeLookup() {
    assertEquals("published a page", DescribeAuditEventCommand.describe("  content.publish  "));
  }

  @Test
  void blankOrNullFallsBackToAGenericPhraseRatherThanAnEmptyString() {
    assertEquals("performed an action", DescribeAuditEventCommand.describe(null));
    assertEquals("performed an action", DescribeAuditEventCommand.describe(""));
    assertEquals("performed an action", DescribeAuditEventCommand.describe("   "));
  }

  @Test
  void anUnmappedEventTypeIsPrettifiedRatherThanShownRaw() {
    // Not in the table -- must never surface the raw dotted/underscored code with no fallback.
    String result = DescribeAuditEventCommand.describe("widget_preview.render");
    assertFalse(result.contains("."));
    assertFalse(result.contains("_"));
    assertEquals("Widget Preview Render", result);
  }

  @Test
  void prettifySplitsCamelCaseDotsAndUnderscoresAndTitleCases() {
    assertEquals("Content Save Draft", DescribeAuditEventCommand.prettify("content.saveDraft"));
    assertEquals("Page Layout Set Widget Preferences",
        DescribeAuditEventCommand.prettify("page_layout.setWidgetPreferences"));
    assertEquals("Some Thing", DescribeAuditEventCommand.prettify("some-thing"));
  }

  @Test
  void neverReturnsNullOrBlankForAnyInput() {
    assertNotNull(DescribeAuditEventCommand.describe("...."));
  }
}
