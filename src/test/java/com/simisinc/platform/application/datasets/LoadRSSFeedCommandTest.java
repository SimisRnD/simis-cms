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

package com.simisinc.platform.application.datasets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringReader;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.simisinc.platform.domain.model.datasets.Dataset;

/**
 * Verifies {@link LoadRSSFeedCommand#loadRows} tolerates an RSS entry with no
 * &lt;description&gt; element -- not required by the RSS spec -- instead of throwing an unhandled
 * NullPointerException. This method is reached by the real "Save & Sync" path (via
 * DatasetFileCommand's RSS case), not just Preview, so a feed like this must not fail the whole
 * sync.
 *
 * @author SimIS Inc.
 */
class LoadRSSFeedCommandTest {

  private Dataset datasetWithColumns(String... columnNames) {
    Dataset dataset = new Dataset();
    dataset.setColumnNames(columnNames);
    return dataset;
  }

  private SyndFeed parseFeed(String rssXml) throws Exception {
    return new SyndFeedInput().build(new StringReader(rssXml));
  }

  @Test
  void loadRowsTreatsAMissingDescriptionAsBlankInsteadOfThrowing() throws Exception {
    String rssXml = "<?xml version=\"1.0\"?>" +
        "<rss version=\"2.0\"><channel><title>Feed</title><link>http://example.com</link>" +
        "<description>Feed</description>" +
        "<item><title>Headline</title><link>http://example.com/a</link></item>" +
        "</channel></rss>";
    SyndFeed feed = parseFeed(rssXml);
    Dataset dataset = datasetWithColumns("title", "link", "description");

    List<String[]> rows = assertDoesNotThrow(() -> LoadRSSFeedCommand.loadRows(dataset, feed, Integer.MAX_VALUE));

    assertEquals(1, rows.size());
    assertEquals("Headline", rows.get(0)[0]);
    assertEquals("http://example.com/a", rows.get(0)[1]);
    assertEquals("", rows.get(0)[2]);
  }

  @Test
  void loadRowsStillReadsADescriptionWhenPresent() throws Exception {
    String rssXml = "<?xml version=\"1.0\"?>" +
        "<rss version=\"2.0\"><channel><title>Feed</title><link>http://example.com</link>" +
        "<description>Feed</description>" +
        "<item><title>Headline</title><link>http://example.com/a</link><description>Body text</description></item>" +
        "</channel></rss>";
    SyndFeed feed = parseFeed(rssXml);
    Dataset dataset = datasetWithColumns("title", "link", "description");

    List<String[]> rows = LoadRSSFeedCommand.loadRows(dataset, feed, Integer.MAX_VALUE);

    assertEquals("Body text", rows.get(0)[2]);
  }
}
