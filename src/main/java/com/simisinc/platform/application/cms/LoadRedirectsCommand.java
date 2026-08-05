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

  // Issue #408 review: load() is called twice at startup -- once from ContextListener (via
  // ImportLegacyRedirectsCommand) and once from WebRequestFilter.init() -- and, before this, each
  // call re-parsed the file and re-logged the deprecation warning. Memoized here so the file is only
  // ever actually read once per JVM; both call sites still get the (identical) result. There is
  // deliberately no reload path -- the same "@todo option to reload" already noted in
  // WebRequestFilter applies here too.
  private static volatile boolean loaded = false;
  private static Map<String, String> cachedRedirectMap = null;

  public static synchronized Map<String, String> load() {
    if (loaded) {
      return cachedRedirectMap;
    }
    loaded = true;
    cachedRedirectMap = loadFromDisk();
    return cachedRedirectMap;
  }

  private static Map<String, String> loadFromDisk() {

    Map<String, String> redirectMap = new HashMap<>();

    // Get a file handle
    String serverConfigPath = FileSystemCommand.getFileServerConfigPath();
    File file = new File(serverConfigPath + "cms/redirects.csv");
    if (!file.exists()) {
      LOG.info("Skipping, no redirects found in: " + file.getAbsolutePath());
      return null;
    }

    // Issue #408: redirects.csv is a legacy path being replaced by the database-backed web_redirects
    // table managed from /admin/web-redirects (see WebRedirectRepository/LoadWebRedirectCommand).
    // This file is still read for backward compatibility during the transition, and
    // ImportLegacyRedirectsCommand can copy its rows into the database, but new redirects should be
    // added through the admin UI going forward.
    LOG.warn("Legacy redirects.csv found at " + file.getAbsolutePath() + " -- this file is deprecated in favor "
        + "of the database-backed web_redirects table (manage redirects at /admin/web-redirects). It will "
        + "continue to be read for backward compatibility, but consider migrating its entries (see "
        + "ImportLegacyRedirectsCommand) and removing the file.");

    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    parserSettings.setHeaderExtractionEnabled(true);

    // Read the file
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(file)) {
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
