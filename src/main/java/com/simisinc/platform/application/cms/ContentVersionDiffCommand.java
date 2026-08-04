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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;

/**
 * Word-level diff between two stored {@code content_versions} rows (#406), rendered as HTML with
 * {@code <ins>}/{@code <del>} markup for the admin version-history compare view.
 *
 * <p>Diffs at the level of visible words, not raw HTML tags: both inputs are reduced to plain text
 * (Jsoup strips markup) before diffing, so purely structural changes -- which {@code <p>}/{@code
 * <div>} happens to wrap a paragraph -- don't drown out the words an author actually changed. The
 * classic LCS (longest common subsequence) algorithm identifies the words common to both versions in
 * order; everything else is an insertion or a deletion, and consecutive same-type words are grouped
 * into a single run so the output reads as phrases, not a word-by-word confetti of tags.
 *
 * <p>Both {@code content_versions.content} values (and the live {@code content.content} a caller may
 * diff a version against) are already sanitized HTML by the time they reach here -- {@link
 * ContentHtmlCommand#toHtml} rendered them at snapshot/save time -- but the diff output re-escapes
 * every word regardless, since Jsoup's {@code .text()} decodes entities back to literal characters
 * that must not be reinterpreted as markup when reassembled into this method's own HTML string.
 *
 * @author elizabeth houser
 */
public class ContentVersionDiffCommand {

  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  /**
   * The LCS table is O(n*m) time and space -- fine for a content block's handful of paragraphs, but a
   * resource-exhaustion risk for an unbounded document. Beyond this many words in either version, the
   * diff is skipped rather than computed.
   */
  static final int MAX_DIFF_WORDS = 3000;

  private ContentVersionDiffCommand() {
    // Static command
  }

  public static class Result {
    private final String html;
    private final boolean truncated;

    Result(String html, boolean truncated) {
      this.html = html;
      this.truncated = truncated;
    }

    /** The rendered diff HTML, or null when {@link #isTruncated()} is true. */
    public String getHtml() {
      return html;
    }

    /** True when either version exceeded {@link #MAX_DIFF_WORDS} and no diff was computed. */
    public boolean isTruncated() {
      return truncated;
    }
  }

  public static Result diff(String oldHtml, String newHtml) {
    List<String> oldWords = tokenize(oldHtml);
    List<String> newWords = tokenize(newHtml);
    if (oldWords.size() > MAX_DIFF_WORDS || newWords.size() > MAX_DIFF_WORDS) {
      return new Result(null, true);
    }
    return new Result(render(oldWords, newWords), false);
  }

  private static List<String> tokenize(String html) {
    List<String> words = new ArrayList<>();
    if (StringUtils.isBlank(html)) {
      return words;
    }
    String text = Jsoup.parse(html).text().trim();
    if (text.isEmpty()) {
      return words;
    }
    for (String word : WHITESPACE.split(text)) {
      if (!word.isEmpty()) {
        words.add(word);
      }
    }
    return words;
  }

  private static String render(List<String> oldWords, List<String> newWords) {
    int n = oldWords.size();
    int m = newWords.size();

    // lcs[i][j] = length of the longest common subsequence of oldWords[i..] and newWords[j..]
    int[][] lcs = new int[n + 1][m + 1];
    for (int i = n - 1; i >= 0; i--) {
      for (int j = m - 1; j >= 0; j--) {
        lcs[i][j] = oldWords.get(i).equals(newWords.get(j))
            ? lcs[i + 1][j + 1] + 1
            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
      }
    }

    StringBuilder result = new StringBuilder();
    StringBuilder run = new StringBuilder();
    int runType = 0; // 0 = unchanged, 1 = deleted, 2 = inserted -- 0 doubles as "no run open" since run starts empty
    int i = 0;
    int j = 0;
    while (i < n || j < m) {
      int type;
      String word;
      if (i < n && j < m && oldWords.get(i).equals(newWords.get(j))) {
        type = 0;
        word = oldWords.get(i);
        i++;
        j++;
      } else if (j >= m || (i < n && lcs[i + 1][j] >= lcs[i][j + 1])) {
        type = 1;
        word = oldWords.get(i);
        i++;
      } else {
        type = 2;
        word = newWords.get(j);
        j++;
      }
      if (type != runType) {
        flushRun(result, runType, run);
        runType = type;
        run.setLength(0);
      }
      if (run.length() > 0) {
        run.append(' ');
      }
      run.append(StringEscapeUtils.escapeHtml4(word));
    }
    flushRun(result, runType, run);
    return result.toString().trim();
  }

  private static void flushRun(StringBuilder result, int type, StringBuilder run) {
    if (run.isEmpty()) {
      return;
    }
    switch (type) {
      case 1 -> result.append("<del>").append(run).append("</del> ");
      case 2 -> result.append("<ins>").append(run).append("</ins> ");
      default -> result.append(run).append(' ');
    }
  }
}
