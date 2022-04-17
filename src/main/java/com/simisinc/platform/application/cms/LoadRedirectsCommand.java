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

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads a map of redirected URLs from a file
 *
 * @author matt rajkowski
 * @created 1/10/19 4:22 PM
 */
public class LoadRedirectsCommand {

  private static Log LOG = LogFactory.getLog(LoadRedirectsCommand.class);

  public static Map<String, String> load() {

    Map<String, String> redirectMap = new HashMap<>();

    // Get a file handle
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    File serverFile = new File(serverRootPath + "redirects.csv");
    if (!serverFile.exists()) {
      LOG.info("Skipping, no redirects found in: " + serverRootPath + "redirects.csv");
      return null;
    }

    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    parserSettings.setHeaderExtractionEnabled(true);

    // Read the file
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(serverFile)) {
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      while ((row = parser.parseNext()) != null) {
        if (row.length == 2) {
          String url = row[0].trim();
//          if (url.endsWith("/")) {
//            url = url.substring(0, url.length() - 1);
//          }
          String redirect = row[1].trim();
          if (!redirect.startsWith("http://") && !redirect.startsWith("https://") && !redirect.startsWith("/")) {
            redirect = "/" + redirect;
          }
          if (StringUtils.isNotBlank(url) && StringUtils.isNotBlank(redirect)) {
            redirectMap.put(url, redirect);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("CSV Error: " + e.getMessage());
    } finally {
      parser.stopParsing();
    }

    LOG.info("Redirects found: " + redirectMap.size());
    return redirectMap;
  }

}
