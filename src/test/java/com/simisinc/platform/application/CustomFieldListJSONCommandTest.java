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

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.domain.model.CustomField;

/**
 * Verifies the "filterable" flag added for issue #635 round-trips through the JSON encoding used
 * by the /admin/collection-custom-fields JSON editor (collections.field_values).
 *
 * @author elizabeth houser
 */
class CustomFieldListJSONCommandTest {

  @Test
  void filterableTrueIsEmittedInTheJson() {
    Map<String, CustomField> fields = new LinkedHashMap<>();
    CustomField field = new CustomField("region", "Region", "list", null);
    field.setFilterable(true);
    fields.put(field.getName(), field);

    String json = CustomFieldListJSONCommand.createJSONString(fields);

    assertTrue(json.contains("\"filterable\":true"), "unexpected JSON: " + json);
  }

  @Test
  void filterableFalseIsOmittedFromTheJson() {
    Map<String, CustomField> fields = new LinkedHashMap<>();
    CustomField field = new CustomField("region", "Region", "list", null);
    // filterable defaults to false -- not set

    fields.put(field.getName(), field);
    String json = CustomFieldListJSONCommand.createJSONString(fields);

    assertFalse(json.contains("filterable"), "the default (false) should not be written out at all: " + json);
  }

  @Test
  void filterableTrueRoundTripsBackThroughParsing() throws Exception {
    Map<String, CustomField> fields = new LinkedHashMap<>();
    CustomField field = new CustomField("region", "Region", "list", null);
    field.setFilterable(true);
    fields.put(field.getName(), field);
    String json = CustomFieldListJSONCommand.createJSONString(fields);

    Map<String, CustomField> parsed = CustomFieldListJSONCommand.populateFromJSONString(json);

    assertTrue(parsed.get("region").isFilterable());
  }

  @Test
  void missingFilterableKeyParsesAsFalse() throws Exception {
    String json = "[{\"label\":\"Region\",\"name\":\"region\",\"type\":\"list\"}]";

    Map<String, CustomField> parsed = CustomFieldListJSONCommand.populateFromJSONString(json);

    assertFalse(parsed.get("region").isFilterable());
  }
}
