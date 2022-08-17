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

package com.simisinc.platform.application.datasets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 8/9/2022 6:33 AM
 */
class DatasetFieldOptionCommandTest {

  @Test
  void testMultipleOptionsWithNull() {
    String options = "blank(\"Not Available\");replace(\"Yes\", \"No\");replace(\"True\", \"False\")";
    String value = "Not Available";

    String newValue = DatasetFieldOptionCommand.applyOptionsToField(options, value);
    Assertions.assertEquals("", newValue);
  }

  @Test
  void testMultipleOptions() {
    String options = "toNull(\"Not Available\");replace(\"Yes\", \"No\");replace(\"True\", \"False\")";
    String value = "Yes and True";

    String newValue = DatasetFieldOptionCommand.applyOptionsToField(options, value);
    Assertions.assertEquals("No and False", newValue);
  }

  @Test
  void testRepeatedOption() {
    String options = "blank(\"Not Available\");blank(\"The Value\")";
    String value = "The Value";

    String newValue = DatasetFieldOptionCommand.applyOptionsToField(options, value);
    Assertions.assertEquals("", newValue);
  }

  @Test
  void testNoOptions() {
    String options = "";
    String value = "Yes and True";
    String newValue = DatasetFieldOptionCommand.applyOptionsToField(options, value);
    Assertions.assertEquals("Yes and True", newValue);

    newValue = DatasetFieldOptionCommand.applyOptionsToField(null, value);
    Assertions.assertEquals("Yes and True", newValue);
  }

  @Test
  void testNullValue() {
    String options = "blank(\"Not Available\");replace(\"Yes\", \"No\");replace(\"True\", \"False\")";
    Assertions.assertEquals("", DatasetFieldOptionCommand.applyOptionsToField(options, null));
    Assertions.assertEquals("", DatasetFieldOptionCommand.applyOptionsToField(options, "null"));
  }

  @Test
  void testCase() {
    String value = "Not AVAILABLE";
    Assertions.assertEquals(value.toLowerCase(), DatasetFieldOptionCommand.applyOptionsToField("lowercase", value));
    Assertions.assertEquals(value.toUpperCase(), DatasetFieldOptionCommand.applyOptionsToField("uppercase", value));
    Assertions.assertEquals("Not Available", DatasetFieldOptionCommand.applyOptionsToField("caps", value));
  }

  @Test
  void testURL() {
    Assertions.assertEquals("Not%20Available", DatasetFieldOptionCommand.applyOptionsToField("uriEncode", "Not Available"));
    Assertions.assertEquals("", DatasetFieldOptionCommand.applyOptionsToField("validateUrl", "Not Available"));
    Assertions.assertEquals("https://www.simiscms.com", DatasetFieldOptionCommand.applyOptionsToField("validateUrl", "https://www.simiscms.com"));
  }

}