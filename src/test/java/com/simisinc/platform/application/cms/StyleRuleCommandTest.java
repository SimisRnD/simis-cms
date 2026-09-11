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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Style values move from an escaped attribute into a stylesheet (issue #1999), where a stray "}" or
 * "&lt;/style&gt;" would be page-wide. Every declaration that could do that must be dropped; every
 * ordinary one must survive unchanged except for !important.
 *
 * @author elizabeth houser
 * @created 9/10/26
 */
class StyleRuleCommandTest {

  @Test
  void ordinaryDeclarationsSurviveWithImportant() {
    assertEquals("background-color: #0b1024 !important; padding-top: 48px !important",
        StyleRuleCommand.safeDeclarations("background-color: #0b1024; padding-top: 48px;"));
  }

  @Test
  void anExistingImportantIsNotDoubled() {
    assertEquals("z-index: 5 !important", StyleRuleCommand.safeDeclarations("z-index: 5 ! important"));
  }

  @Test
  void theSiteLayoutValuesAllSurvive() {
    // Shapes the pilot's page XML actually uses on sections, columns and widgets.
    String[] authored = {
        "background-image: url('/assets/img/20260827230916-330/SimIS%20Digital%20Network%20Landscape.webp'); background-size: cover; background-position: center; min-height: 360px",
        "background: #ffffff; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.25); border: 1px solid rgba(255,255,255,.1); margin: 0 auto; padding: 24px",
        "position: absolute; top: 0; left: 0; right: 0; bottom: 0; z-index: 2; border-left: 4px solid #c1550a; border-radius: 8px",
        "background-image: linear-gradient(rgba(11, 16, 36, 0.4), rgba(11, 16, 36, 0.4)), url(\"/assets/img/x.webp\"); background-attachment: fixed",
        "border-top: 1px solid rgba(255, 255, 255, 0.18); margin-top: 24px",
        "max-width: 720px; margin: 0 auto",
    };
    for (String css : authored) {
      String safe = StyleRuleCommand.safeDeclarations(css);
      assertEquals(css.split(";").length, safe.split("; ").length, "every declaration must survive: " + css);
    }
  }

  @Test
  void aValueThatWouldCloseTheRuleIsDropped() {
    assertNull(StyleRuleCommand.safeDeclarations("color: red} body{display:none"));
  }

  @Test
  void aValueThatWouldCloseTheStyleElementIsDropped() {
    assertNull(StyleRuleCommand.safeDeclarations("color: red</style><script>alert(1)</script>"));
  }

  @Test
  void onlyTheBadDeclarationIsDropped() {
    assertEquals("margin: 0 !important; padding: 4px !important",
        StyleRuleCommand.safeDeclarations("margin: 0; color: x{y}; padding: 4px"));
  }

  @Test
  void atRulesCommentsAndEscapesAreRefused() {
    // The @import "declaration" is dropped on its own; the ordinary one before it survives.
    assertEquals("color: red !important",
        StyleRuleCommand.safeDeclarations("color: red;@import url(https://evil.example/x.css)"));
    assertNull(StyleRuleCommand.safeDeclarations("color: re/**/d"));
    assertNull(StyleRuleCommand.safeDeclarations("content: \"\\7d\""));
  }

  @Test
  void scriptBearingCssIsRefused() {
    assertNull(StyleRuleCommand.safeDeclarations("width: expression(alert(1))"));
    assertNull(StyleRuleCommand.safeDeclarations("background: url('javascript:alert(1)')"));
    assertNull(StyleRuleCommand.safeDeclarations("-moz-binding: url('/x.xml#y')"));
    assertNull(StyleRuleCommand.safeDeclarations("behavior: url(/x.htc)"));
  }

  @Test
  void urlsMustPointHereOrAtHttp() {
    assertTrue(StyleRuleCommand.safeDeclarations("background-image: url(/assets/img/a.webp)") != null);
    assertTrue(StyleRuleCommand.safeDeclarations("background-image: url('https://cdn.example.com/a.png')") != null);
    assertNull(StyleRuleCommand.safeDeclarations("background-image: url(//evil.example/a.png)"));
    assertNull(StyleRuleCommand.safeDeclarations("background-image: url(data:image/svg+xml;base64,PHN2Zz4=)"));
    assertNull(StyleRuleCommand.safeDeclarations("background-image: url( 'a' 'b' )"));
    // Parentheses are legal inside a quoted url and occur in real filenames
    assertEquals("background-image: url('/assets/img/photo (1).png') !important",
        StyleRuleCommand.safeDeclarations("background-image: url('/assets/img/photo (1).png')"));
  }

  @Test
  void unbalancedQuotesAndParenthesesAreRefused() {
    assertNull(StyleRuleCommand.safeDeclarations("font-family: 'Inter"));
    assertNull(StyleRuleCommand.safeDeclarations("width: calc(100% - 2px"));
    assertNull(StyleRuleCommand.safeDeclarations("width: 100%)"));
  }

  @Test
  void aSemicolonInsideAQuotedUrlDoesNotSplitTheDeclaration() {
    String safe = StyleRuleCommand.safeDeclarations("background-image: url('/assets/a;b.png'); margin: 0");
    assertEquals("background-image: url('/assets/a;b.png') !important; margin: 0 !important", safe);
  }

  @Test
  void propertyNamesMustBePlainIdentifiers() {
    assertNull(StyleRuleCommand.safeDeclarations("x y: 1"));
    assertNull(StyleRuleCommand.safeDeclarations(": 1"));
    assertEquals("--sc-gap: 4px !important", StyleRuleCommand.safeDeclarations("--sc-gap: 4px"));
  }

  @Test
  void blankInputHasNoHookAndNoRule() {
    assertEquals("", StyleRuleCommand.hook(null));
    assertEquals("", StyleRuleCommand.hook("  "));
    assertEquals("", StyleRuleCommand.rule("color: red}"));
  }

  @Test
  void anUnsetOptionalValueIsSkippedAndTheRestKept() {
    // What a template builds when its optional value is empty, e.g. 'color:' += iconColor
    assertNull(StyleRuleCommand.safeDeclarations("color:"));
    assertEquals("", StyleRuleCommand.hook("object-position: "));
    assertEquals(StyleRuleCommand.hook("margin-top: 6px"), StyleRuleCommand.hook("margin-top: 6px;background-color:"));
  }

  @Test
  void theSameDeclarationsAlwaysGiveTheSameHook() {
    String a = StyleRuleCommand.hook("margin: 0; padding: 4px");
    assertEquals(a, StyleRuleCommand.hook("margin:0 ; padding:4px;"));
    assertTrue(a.matches("^sc-[0-9a-f]{12}$"), a);
    assertFalse(a.equals(StyleRuleCommand.hook("margin: 0; padding: 5px")));
  }

  @Test
  void theRuleTargetsTheHook() {
    String css = "background: #ffffff; margin: 0";
    assertEquals("[data-sc-style=\"" + StyleRuleCommand.hook(css) + "\"]{background: #ffffff !important; margin: 0 !important}",
        StyleRuleCommand.rule(css));
  }

  @Test
  void theCategoryHeaderShapeSurvives() {
    // CategoryCommand.headerColorCSS() returns exactly this shape.
    assertEquals("background: #336699 !important; color: #ffffff !important",
        StyleRuleCommand.safeDeclarations("background:#336699;color:#ffffff"));
  }
}
