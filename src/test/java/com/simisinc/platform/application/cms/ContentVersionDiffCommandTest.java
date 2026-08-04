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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Verifies the word-level LCS diff used by the content version-history compare view (#406).
 *
 * @author elizabeth houser
 */
class ContentVersionDiffCommandTest {

  @Test
  void identicalContentProducesNoInsOrDelMarkup() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff("<p>Hello world</p>", "<p>Hello world</p>");

    assertFalse(result.isTruncated());
    assertEquals("Hello world", result.getHtml());
  }

  @Test
  void aChangedWordIsShownAsADeletionFollowedByAnInsertion() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff("<p>The quick fox</p>", "<p>The slow fox</p>");

    assertEquals("The <del>quick</del> <ins>slow</ins> fox", result.getHtml());
  }

  @Test
  void addedWordsAreWrappedInIns() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff("<p>Hello</p>", "<p>Hello there world</p>");

    assertEquals("Hello <ins>there world</ins>", result.getHtml());
  }

  @Test
  void removedWordsAreWrappedInDel() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff("<p>Hello there world</p>", "<p>Hello</p>");

    assertEquals("Hello <del>there world</del>", result.getHtml());
  }

  @Test
  void firstEverVersionAgainstBlankShowsEverythingAsInserted() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff(null, "<p>Brand new content</p>");

    assertEquals("<ins>Brand new content</ins>", result.getHtml());
  }

  @Test
  void purelyStructuralHtmlChangesProduceNoWordDiff() {
    // Diffing is word-level, not tag-level: which element wraps the same words is not a content change.
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff(
        "<p>Hello world</p>", "<div><span>Hello</span> world</div>");

    assertEquals("Hello world", result.getHtml());
  }

  @Test
  void wordsAreHtmlEscapedInTheOutput() {
    // Jsoup's .text() decodes entities back to literal characters -- the diff renderer must
    // re-escape them so the diff HTML itself cannot be reinterpreted as markup.
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff(
        "<p>safe</p>", "<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>");

    assertFalse(result.getHtml().contains("<script>"), result.getHtml());
    assertTrue(result.getHtml().contains("&lt;script&gt;"), result.getHtml());
  }

  @Test
  void oversizedContentIsNotDiffedAndReportsTruncated() {
    String hugeOld = "word ".repeat(ContentVersionDiffCommand.MAX_DIFF_WORDS + 1);
    String hugeNew = "different ".repeat(ContentVersionDiffCommand.MAX_DIFF_WORDS + 1);

    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff(hugeOld, hugeNew);

    assertTrue(result.isTruncated());
    assertNull(result.getHtml());
  }

  @Test
  void blankInputsOnBothSidesProduceEmptyOutput() {
    ContentVersionDiffCommand.Result result = ContentVersionDiffCommand.diff(null, null);

    assertFalse(result.isTruncated());
    assertEquals("", result.getHtml());
  }
}
