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

package com.simisinc.platform.application.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.Test;

/**
 * {@link LoadWidgetSchemaCommand} caches the loaded file in a static field for the life of the
 * JVM, so the caching behavior and the content it returns are asserted together in one test
 * rather than split across methods that could observe each other's cached state.
 *
 * @author SimIS Inc.
 */
class LoadWidgetSchemaCommandTest {

  @Test
  void loadsAndCachesTheSchemaFileContent() {
    ServletContext servletContext = mock(ServletContext.class);
    when(servletContext.getResourceAsStream(anyString()))
        .thenReturn(new ByteArrayInputStream("{\"widgets\":{}}".getBytes(StandardCharsets.UTF_8)));

    String first = LoadWidgetSchemaCommand.getWidgetSchemaJson(servletContext);
    String second = LoadWidgetSchemaCommand.getWidgetSchemaJson(servletContext);

    assertEquals("{\"widgets\":{}}", first);
    assertEquals(first, second);
    // Proves the cache, not a re-read on every call
    verify(servletContext, times(1)).getResourceAsStream(anyString());
  }
}
