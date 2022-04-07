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
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests HTML functions
 *
 * @author matt rajkowski
 * @created 3/2/21 10:00 PM
 */
public class HtmlIconTagsTests {

  @Test
  void iconTagsBasicTest() {
    String html = "<em class=\"far fa-code tinymce-noedit\"></em> <span class=\"far fa-code-2 tinymce-noedit\"></span>";
    String expected = "<i class=\"far fa-code\"></i> <i class=\"far fa-code-2\"></i>";

    String value = TinyMceCommand.updateContentFromEditor(html);
    assertEquals(expected, value);

    // Run it through again
    value = TinyMceCommand.updateContentFromEditor(value);
    assertEquals(expected, value);
  }

  @Test
  void iconTagsNoMatchTest() {
    String html = "<p class=\"far fa-code tinymce-noedit\"></p> <p class=\"fab fa-code-2 tinymce-noedit\"></p>";
    String expected = "<p class=\"far fa-code tinymce-noedit\"></p> <p class=\"fab fa-code-2 tinymce-noedit\"></p>";

    String value = TinyMceCommand.updateContentFromEditor(html);
    assertEquals(expected, value);

    value = TinyMceCommand.updateContentFromEditor(value);
    assertEquals(expected, value);
  }

  @Test
  void iconTagsLinkTest() {
    String html = "<p>\n" +
        "<a href=\"mailto:example@example.com\"><span class=\"fas fa-envelope-square tinymce-noedit\">&nbsp;</span></a>\n" +
        "<a href=\"https://example.com\" target=\"_blank\"><span class=\"fab fa-linkedin tinymce-noedit\">&nbsp;</span></a>\n" +
        "</p>";
    String expected = "<p>\n" +
        "<a href=\"mailto:example@example.com\"><i class=\"fas fa-envelope-square\"></i></a>\n" +
        "<a href=\"https://example.com\" target=\"_blank\"><i class=\"fab fa-linkedin\"></i></a>\n" +
        "</p>";

    String value = TinyMceCommand.updateContentFromEditor(html);
    assertEquals(expected, value);

    value = TinyMceCommand.prepareContentForEditor(value);
    assertEquals(html, value);

    // Run it through again
    value = TinyMceCommand.updateContentFromEditor(value);
    assertEquals(expected, value);
  }

  @Test
  void iconTagsEmptyTest() {
    assertNull(TinyMceCommand.updateContentFromEditor(null));
    assertEquals("", TinyMceCommand.updateContentFromEditor(""));
  }

  @Test
  void iconTagsForEditorTest() {
    String html = "<i class=\"far fa-code\"></i> <i class=\"far fa-code-2\"></i>";
    String expected = "<span class=\"far fa-code tinymce-noedit\">&nbsp;</span> <span class=\"far fa-code-2 tinymce-noedit\">&nbsp;</span>";

    String value = TinyMceCommand.prepareContentForEditor(html);
    assertEquals(expected, value);

    // Run it through again
    value = TinyMceCommand.prepareContentForEditor(value);
    assertEquals(expected, value);
  }

  @Test
  void iconTagsRoundTripTest() {
    String html = "<i class=\"far fa-code\"></i> <i class=\"far fa-code-2\"></i>";
    String intermediate = "<span class=\"far fa-code tinymce-noedit\">&nbsp;</span> <span class=\"far fa-code-2 tinymce-noedit\">&nbsp;</span>";

    String value = TinyMceCommand.prepareContentForEditor(html);
    assertEquals(intermediate, value);

    // Run it backwards
    value = TinyMceCommand.updateContentFromEditor(intermediate);
    assertEquals(html, value);
  }@Test

  void iconTagsCleanShimTest() {
    String html = "<h2>Medical Solutions</h2>\n" +
        "<p><strong>Company</strong><span>&nbsp;is improving the features and functionalities that will advance the quality of care for patients while.</span></p>\n" +
        "<p><a href=\"/medical-solutions\"><span><span class=\"fa fa-angle-right\"></span>&nbsp;Learn More About Medical Solutions</span></a></p>";
    String expected = "<h2>Medical Solutions</h2>\n" +
        "<p><strong>Company</strong>&nbsp;is improving the features and functionalities that will advance the quality of care for patients while.</p>\n" +
        "<p><a href=\"/medical-solutions\"><i class=\"fa fa-angle-right\"></i>&nbsp;Learn More About Medical Solutions</a></p>";

    String value = HtmlCommand.cleanContent(html);
    assertEquals(expected, value);
  }

}
