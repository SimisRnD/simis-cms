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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.DataException;

/**
 * @author Elizabeth Houser
 * @created 7/22/2026 10:35 AM
 */
class ValidateStylesheetCommandTest {

  @Test
  void acceptsBlankContent() {
    Assertions.assertDoesNotThrow(() -> ValidateStylesheetCommand.checkCss(null));
    Assertions.assertDoesNotThrow(() -> ValidateStylesheetCommand.checkCss(""));
    Assertions.assertDoesNotThrow(() -> ValidateStylesheetCommand.checkCss("   \n  "));
  }

  @Test
  void acceptsOrdinaryRules() {
    Assertions.assertDoesNotThrow(
        () -> ValidateStylesheetCommand.checkCss("body { color: #333; }\n.header a:hover { text-decoration: none; }"));
  }

  @Test
  void acceptsNestedBlocksSuchAsMediaQueries() {
    Assertions.assertDoesNotThrow(() -> ValidateStylesheetCommand
        .checkCss("@media (max-width: 640px) {\n  .sidebar { display: none; }\n  .main { width: 100%; }\n}"));
  }

  @Test
  void acceptsCommentsIncludingBracesInsideThem() {
    Assertions.assertDoesNotThrow(
        () -> ValidateStylesheetCommand.checkCss("/* a commented-out rule: .x { color: red; } */\nbody { margin: 0; }"));
  }

  @Test
  void acceptsBracesInsideQuotedValues() {
    Assertions.assertDoesNotThrow(
        () -> ValidateStylesheetCommand.checkCss(".icon::after { content: \"}\"; }\n.other::after { content: '{'; }"));
  }

  @Test
  void acceptsEscapedQuoteInsideAQuotedValue() {
    Assertions
        .assertDoesNotThrow(() -> ValidateStylesheetCommand.checkCss(".q::after { content: \"say \\\" here\"; }"));
  }

  @Test
  void rejectsUnclosedBraceAndReportsTheLine() {
    DataException e = Assertions.assertThrows(DataException.class,
        () -> ValidateStylesheetCommand.checkCss("body { margin: 0; }\n\n.sidebar {\n  color: red;\n"));
    Assertions.assertTrue(e.getMessage().contains("line 3"), "expected the opening line, got: " + e.getMessage());
  }

  @Test
  void rejectsExtraClosingBrace() {
    DataException e = Assertions.assertThrows(DataException.class,
        () -> ValidateStylesheetCommand.checkCss("body { margin: 0; }\n}"));
    Assertions.assertTrue(e.getMessage().contains("line 2"), "expected the offending line, got: " + e.getMessage());
  }

  @Test
  void rejectsUnterminatedComment() {
    DataException e = Assertions.assertThrows(DataException.class,
        () -> ValidateStylesheetCommand.checkCss("body { margin: 0; }\n/* still going...\n.more { color: red; }"));
    Assertions.assertTrue(e.getMessage().contains("line 2"), "expected the comment's line, got: " + e.getMessage());
  }

  @Test
  void rejectsUnterminatedQuote() {
    DataException e = Assertions.assertThrows(DataException.class,
        () -> ValidateStylesheetCommand.checkCss(".icon::after { content: \"oops; }"));
    Assertions.assertTrue(e.getMessage().contains("line 1"), "expected the quote's line, got: " + e.getMessage());
  }

  @Test
  void doesNotMistakeACommentInsideAStringForARealComment() {
    Assertions.assertDoesNotThrow(() -> ValidateStylesheetCommand.checkCss(".x::after { content: \"/*\"; }"));
  }
}
