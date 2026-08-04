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

import javax.json.bind.adapter.JsonbAdapter;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Unlike java.util.Date/java.time.*, java.sql.Timestamp has no built-in JSON-B handling, so the
 * default reflection-based mapper tries to introspect its fields directly -- including the
 * private `nanos` field -- which the JDK module system blocks (java.sql doesn't open itself to
 * unnamed modules), throwing InaccessibleObjectException. This adapter handles the conversion
 * explicitly so response DTOs can expose a Timestamp field without hitting that reflection wall.
 *
 * @author SimIS Inc.
 */
public class TimestampJsonbAdapter implements JsonbAdapter<Timestamp, String> {

  @Override
  public String adaptToJson(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant().toString();
  }

  @Override
  public Timestamp adaptFromJson(String value) {
    return value == null ? null : Timestamp.from(Instant.parse(value));
  }
}
