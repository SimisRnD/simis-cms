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

package com.simisinc.platform.application.ecommerce;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests JSON functions
 *
 * @author matt rajkowski
 * @created 1/4/22 8:14 AM
 */
class JsonTest {

  public static final String JSON_VALUE = "[{\"id\": 1, \"name\": \"attribute0\", \"value\": \"value1\"}, {\"id\": 2, \"name\": \"attribute1\", \"value\": \"\"}]";

  @Test
  void jsonFromString() throws IOException {

    JsonNode root = JsonLoader.fromString(JSON_VALUE);
    assertTrue(root.isArray());
    assertEquals(2, root.size());

    Iterator<JsonNode> fields = root.elements();
    while (fields.hasNext()) {
      JsonNode node = fields.next();

      assertTrue(node.has("id"));
      assertTrue(node.get("id").asLong() > 0);

      assertTrue(node.has("name"));
      assertTrue(node.get("name").asText().startsWith("attribute"));

      assertTrue(node.has("value"));
      assertNotNull(node.get("value").asText());
    }
  }

}
