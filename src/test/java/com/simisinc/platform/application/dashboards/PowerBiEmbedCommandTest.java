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

package com.simisinc.platform.application.dashboards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PowerBiEmbedCommandTest {

  @Test
  void acceptsARealPublishToWebUrl() {
    String url = "https://app.powerbi.com/view?r=eyJrIjoiYWJjMTIzIn0%3D";

    assertEquals(url, PowerBiEmbedCommand.validateEmbedUrl(url));
  }

  @Test
  void rejectsAMissingUrl() {
    assertNull(PowerBiEmbedCommand.validateEmbedUrl(null));
    assertNull(PowerBiEmbedCommand.validateEmbedUrl(""));
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("   "));
  }

  @Test
  void rejectsAMalformedUrl() {
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("not a url at all"));
  }

  @Test
  void rejectsAnHttpUrl() {
    // Publish-to-web URLs are always https -- refuse to embed a downgraded/spoofed variant.
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("http://app.powerbi.com/view?r=abc123"));
  }

  @Test
  void rejectsANonPowerBiHost() {
    // Guards against a page author (or a compromised page-layout XML edit) pointing this widget
    // at an arbitrary attacker-controlled iframe target instead of a real Power BI embed.
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("https://evil.example.com/view?r=abc123"));
  }

  @Test
  void rejectsALookalikeHost() {
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("https://app.powerbi.com.evil.com/view?r=abc123"));
    assertNull(PowerBiEmbedCommand.validateEmbedUrl("https://notapp.powerbi.com/view?r=abc123"));
  }
}
