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

package com.simisinc.platform.application.datasets;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Reads in dataset rows from an RSS feed dataset file
 *
 * @author matt rajkowski
 * @created 6/12/18 9:24 AM
 */
public class LoadRSSFeedCommand {

  private static Log LOG = LogFactory.getLog(LoadRSSFeedCommand.class);

  public static SyndFeed loadFeed(Dataset dataset) throws DataException {
    File file = DatasetFileCommand.getFile(dataset);
    if (file == null) {
      throw new DataException("Dataset file not found");
    }
    // Convert the file into to a feed
    try {
      return new SyndFeedInput().build(new FileReader(file));
    } catch (Exception e) {
      LOG.error(e);
      throw new DataException("RSS is incorrect");
    }
  }

  public static List<String[]> loadRows(Dataset dataset, int maxRowCountToReturn) throws DataException {
    SyndFeed feed = LoadRSSFeedCommand.loadFeed(dataset);
    if (feed == null) {
      throw new DataException("Dataset file not found");
    }
    return loadRows(dataset, feed, maxRowCountToReturn);
  }

  public static List<String[]> loadRows(Dataset dataset, SyndFeed feed, int maxRowCountToReturn) {
    List<String[]> rows = new ArrayList<>();
    if (feed == null || feed.getEntries().isEmpty()) {
      return rows;
    }
    if (feed.getEntries().size() < maxRowCountToReturn) {
      maxRowCountToReturn = feed.getEntries().size();
    }
    for (int i = 0; i < maxRowCountToReturn; i++) {
      List<String> row = new ArrayList<>();
      SyndEntry syndEntry = feed.getEntries().get(i);
      for (String column : dataset.getColumnNamesList()) {
        // "title", "link", "pubDate", "description"
        if ("title".equals(column)) {
          row.add(syndEntry.getTitle());
        } else if ("link".equals(column)) {
          row.add(syndEntry.getLink());
        } else if ("pubDate".equals(column)) {
          Date publishDate = syndEntry.getPublishedDate();
          if (publishDate != null) {
            row.add(new SimpleDateFormat("MM-dd-yyyy HH:mm").format(publishDate));
          } else {
            row.add("none");
          }
        } else if ("description".equals(column)) {
          String type = syndEntry.getDescription().getType();
          if (type == null || "text/plain".equals(type)) {
            row.add(syndEntry.getDescription().getValue());
          } else if (type.contains("html")) {
            row.add(HtmlCommand.text(syndEntry.getDescription().getValue()));
          } else {
            row.add(type + ": " + syndEntry.getDescription().getValue());
          }
        }
      }
      rows.add(row.toArray(new String[0]));
    }
    return rows;
  }
}
