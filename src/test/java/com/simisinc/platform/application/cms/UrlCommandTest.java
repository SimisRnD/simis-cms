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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author matt rajkowski
 * @created 5/8/2022 7:00 AM
 */
class UrlCommandTest {

  @Test
  void encode() {
    Assertions.assertEquals("http://example.com", UrlCommand.encode("http://example.com"));
    Assertions.assertEquals("http://example.com?name=value", UrlCommand.encode("http://example.com?name=value"));
    Assertions.assertEquals("http://example.com?name=value&name1=value1", UrlCommand.encode("http://example.com?name=value&name1=value1"));
    Assertions.assertEquals("#", UrlCommand.encode("http://example.com "));
    Assertions.assertEquals("#", UrlCommand.encode("something"));
  }

  @Test
  void encodeUri() {
    Assertions.assertEquals("value1", UrlCommand.encodeUri("value1"));
    Assertions.assertEquals("the%20value", UrlCommand.encodeUri("the value"));
    Assertions.assertEquals("the%20value!", UrlCommand.encodeUri("the value!"));
    Assertions.assertEquals("%22the%20value%22", UrlCommand.encodeUri("\"the value\""));
    Assertions.assertEquals("'the%20value'", UrlCommand.encodeUri("'the value'"));
  }

  @Test
  void isUrlValid() {
    Assertions.assertTrue(UrlCommand.isUrlValid("http://www.example.com"));
    Assertions.assertTrue(UrlCommand.isUrlValid("http://example.com"));
    Assertions.assertTrue(UrlCommand.isUrlValid("http://example.com?name=value"));
    Assertions.assertTrue(UrlCommand.isUrlValid("http://example.com?name=value&name1=value1"));
    Assertions.assertFalse(UrlCommand.isUrlValid("http://example.com?\"name=value\""));
    Assertions.assertFalse(UrlCommand.isUrlValid("http://example.com?name=\"value\""));
    Assertions.assertFalse(UrlCommand.isUrlValid("http://example.com "));
    Assertions.assertFalse(UrlCommand.isUrlValid("file:///web/index.html"));
    Assertions.assertFalse(UrlCommand.isUrlValid("ftp://ftp.example.com"));
  }

  @Test
  void getValidReturnPage() {
    Assertions.assertNull(UrlCommand.getValidReturnPage(""));
    Assertions.assertNull(UrlCommand.getValidReturnPage(" "));
    Assertions.assertNull(UrlCommand.getValidReturnPage(null));
    Assertions.assertNull(UrlCommand.getValidReturnPage("http://example.com"));
    Assertions.assertEquals("/web-page", UrlCommand.getValidReturnPage("/web-page"));
  }
}