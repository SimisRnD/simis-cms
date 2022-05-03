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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests HTML functions
 *
 * @author matt rajkowski
 * @created 10/29/20 2:07 PM
 */
public class HtmlTest {

  @Test
  void htmlText() {
    String html = "<p>this is html</p>";
    String expected = "this is html";
    String text = HtmlCommand.text(html);
    assertEquals(expected, text);
  }

  @Test
  void stringValueToHtml() {
    String text = "this is the \"text\" value";
    String expected = "this is the &quot;text&quot; value";
    String html = HtmlCommand.toHtml(text);
    assertEquals(expected, html);
  }

  @Test
  void cleanHtmlContent() {
    String html = "<div onClick=\"javascript:alert()\"><p>this is html</p></div><p>&nbsp;</p>";
    String expected = "<p>this is html</p>";
    String cleanHtml = HtmlCommand.cleanContent(html);
    assertEquals(expected, cleanHtml);
  }
}
