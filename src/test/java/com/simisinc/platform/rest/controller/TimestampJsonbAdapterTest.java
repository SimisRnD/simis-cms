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

package com.simisinc.platform.rest.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/**
 * Verifies {@link TimestampJsonbAdapter}'s conversion directly, and null handling on both sides.
 */
class TimestampJsonbAdapterTest {

  private final TimestampJsonbAdapter adapter = new TimestampJsonbAdapter();

  @Test
  void adaptToJsonFormatsAsIsoInstant() {
    Timestamp timestamp = Timestamp.from(Instant.parse("2026-08-04T12:34:56.789Z"));
    assertEquals("2026-08-04T12:34:56.789Z", adapter.adaptToJson(timestamp));
  }

  @Test
  void adaptToJsonHandlesNull() {
    assertNull(adapter.adaptToJson(null));
  }

  @Test
  void adaptFromJsonParsesIsoInstant() {
    Timestamp timestamp = adapter.adaptFromJson("2026-08-04T12:34:56.789Z");
    assertEquals(Instant.parse("2026-08-04T12:34:56.789Z"), timestamp.toInstant());
  }

  @Test
  void adaptFromJsonHandlesNull() {
    assertNull(adapter.adaptFromJson(null));
  }
}
