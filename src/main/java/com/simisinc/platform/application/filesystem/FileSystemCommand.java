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

package com.simisinc.platform.application.filesystem;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Commands for working with the file system
 *
 * @author matt rajkowski
 * @created 4/25/18 9:56 AM
 */
public class FileSystemCommand {

  private static Log LOG = LogFactory.getLog(FileSystemCommand.class);

  private static String configPath = null;
  private static String filesPath = null;

  public static String getFileServerConfigPath() {
    if (configPath != null) {
      return configPath;
    }
    String serverConfigPath = null;
    if (System.getenv().containsKey("CMS_PATH")) {
      serverConfigPath = System.getenv("CMS_PATH") + "/config";
    }
    if (StringUtils.isBlank(serverConfigPath)) {
      serverConfigPath = LoadSitePropertyCommand.loadByName("system.configpath");
    }
    if (StringUtils.isBlank(serverConfigPath)) {
      // @note this value is checked at system startup and will abort then
      LOG.error("system.configpath does not exist");
      LOG.info("HINT: INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('Configuration path', 'system.configpath', '/opt/simis/config')");
      return null;
    }
    if (!serverConfigPath.endsWith("/")) {
      serverConfigPath += "/";
    }
    configPath = serverConfigPath;
    return configPath;
  }

  public static String getFileServerRootPath() {
    if (filesPath != null) {
      return filesPath;
    }
    String serverRootPath = null;
    if (System.getenv().containsKey("CMS_PATH")) {
      serverRootPath = System.getenv("CMS_PATH") + "/files";
    }
    if (StringUtils.isBlank(serverRootPath)) {
      serverRootPath = LoadSitePropertyCommand.loadByName("system.filepath");
    }
    if (StringUtils.isBlank(serverRootPath)) {
      // @note this value is checked at system startup and will abort then
      LOG.error("system.filepath does not exist");
      LOG.info("HINT: INSERT INTO site_properties (property_label, property_name, property_value) VALUES ('File server path', 'system.filepath', '/opt/simis/files')");
      return null;
    }
    if (!serverRootPath.endsWith("/")) {
      serverRootPath += "/";
    }
    filesPath = serverRootPath;
    return filesPath;
  }

  public static String generateFileServerSubPath(String module) {
    Date date = new Date();
    LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    int year = localDate.getYear();
    int month = localDate.getMonthValue();
    int day = localDate.getDayOfMonth();
    String monthValue = (month < 10 ? "0" : "") + month;
    String dayValue = (day < 10 ? "0" : "") + day;
    String subPath = module + "/" + year + "/" + monthValue + "/" + dayValue + "/";

    // Make sure the path exists
    String fullPath = getFileServerRootPath() + subPath;
    File directory = new File(fullPath);
    directory.mkdirs();

    return subPath;
  }

  public static String generateUniqueFilename(long appendValue) {
    String filenameUUID = UUID.randomUUID().toString();
    return System.currentTimeMillis() + "-" + filenameUUID + "-" + appendValue;
  }

  public static String getFileChecksum(File file) {
    try {
      String method = "SHA-512";
      MessageDigest digest = MessageDigest.getInstance(method);

      FileInputStream fis = new FileInputStream(file);
      byte[] byteArray = new byte[1024];
      int bytesCount = 0;
      while ((bytesCount = fis.read(byteArray)) != -1) {
        digest.update(byteArray, 0, bytesCount);
      }
      fis.close();

      // Convert decimal to hexadecimal format
      byte[] bytes = digest.digest();
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < bytes.length; i++) {
        sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      return method + ";" + sb.toString();
    } catch (Exception e) {
      LOG.error(e);
    }
    return null;
  }

  public static File generateTempFile(String folderName, long userId, String extension) {
    String serverRootPath = FileSystemCommand.getFileServerRootPath();
    String serverSubPath = FileSystemCommand.generateFileServerSubPath(folderName);
    String serverCompletePath = serverRootPath + serverSubPath;
    String uniqueFilename = FileSystemCommand.generateUniqueFilename(userId);
    return new File(serverCompletePath + uniqueFilename + (extension != null ? "." + extension : ""));
  }

  public static boolean isModified(File file, long previousModifiedValue) {
    if (previousModifiedValue == file.lastModified()) {
      return false;
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("File is newer: " + file.getAbsolutePath() + " lastModified: " + file.lastModified());
    }
    return true;
  }

  public static List<String> loadFileToList(File file) {
    List<String> list = new ArrayList<>();
    if (!file.exists()) {
      LOG.warn("File not found: " + file.getAbsolutePath());
      return list;
    }
    // Determine CSV settings
    CsvParserSettings parserSettings = new CsvParserSettings();
    parserSettings.setLineSeparatorDetectionEnabled(true);
    if (file.getAbsolutePath().endsWith(".csv")) {
      parserSettings.setHeaderExtractionEnabled(true);
    }
    // Read the file
    CsvParser parser = new CsvParser(parserSettings);
    try (InputStream inputStream = new FileInputStream(file)) {
      parser.beginParsing(inputStream, "ISO-8859-1");
      String[] row;
      while ((row = parser.parseNext()) != null) {
        if (row.length == 1) {
          String value = row[0];
          if (StringUtils.isNotBlank(value)) {
            list.add(value);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("File Error: " + e.getMessage());
    } finally {
      parser.stopParsing();
    }
    LOG.info("File loaded: " + file.getAbsolutePath() + " size: " + list.size());
    return list;
  }
}
