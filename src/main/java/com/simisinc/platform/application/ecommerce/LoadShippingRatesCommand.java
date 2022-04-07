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

package com.simisinc.platform.application.ecommerce;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/2/19 6:26 AM
 */
public class LoadShippingRatesCommand {

  private static Log LOG = LogFactory.getLog(LoadShippingRatesCommand.class);

  public static Map<String, String> load() {

    Map<String, String> redirectMap = new HashMap<>();

    // Get a file handle
    String serverConfigPath = FileSystemCommand.getFileServerConfigPath();
    File serverFile = new File(serverConfigPath + "/e-commerce/" + "shipping-rates.csv");
    if (!serverFile.exists()) {
      LOG.info("Skipping, no redirects found in: " + serverFile.getAbsolutePath());
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

//          String url = row[0].trim();
//          String redirect = row[1].trim();


      }
    } catch (Exception e) {
      LOG.error("CSV Error: " + e.getMessage());
    } finally {
      parser.stopParsing();
    }

    LOG.info("Shipping rates found: " + redirectMap.size());
    return redirectMap;
  }

}
