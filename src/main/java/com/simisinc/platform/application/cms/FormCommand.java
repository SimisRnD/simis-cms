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
import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.model.cms.FormData;
import com.simisinc.platform.domain.model.cms.FormField;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Determines if the form submission should be flagged for spam
 *
 * @author matt rajkowski
 * @created 11/9/20 8:00 AM
 */
public class FormCommand {

  private static Log LOG = LogFactory.getLog(FormCommand.class);

  public static final String COUNTRY_IGNORE_LIST = "country-ignore-list.csv";
  public static final String EMAIL_IGNORE_LIST = "email-ignore-list.csv";
  public static final String SPAM_LIST = "spam-list.csv";

  private static Map<String, List<String>> listMap = new HashMap<>();
  private static Map<String, Long> lastModifiedMap = new HashMap<>();

  public static synchronized void load() {
    load(COUNTRY_IGNORE_LIST);
    load(EMAIL_IGNORE_LIST);
    load(SPAM_LIST);
  }

  private static void load(String filename) {
    // Get a file handle
    String serverConfigPath = FileSystemCommand.getFileServerConfigPath();
    File file = new File(serverConfigPath + "cms/" + filename);

    // Determine if the file is new
    if (lastModifiedMap.containsKey(filename) && !FileSystemCommand.isModified(file, lastModifiedMap.get(filename))) {
      return;
    }

    // Load the file
    List<String> list = FileSystemCommand.loadFileToList(file);

    // Cache the result
    listMap.put(filename, list);
    lastModifiedMap.put(filename, file.lastModified());
  }

  public static void setList(String filename, List<String> list) {
    listMap.put(filename, list);
  }

  public static boolean checkNotificationRules(FormData formData) {

    // Check country first
    List<String> countryIgnoreList = listMap.get(COUNTRY_IGNORE_LIST);
    String countryCode = GeoIPCommand.getCountryCode(formData.getIpAddress());
    if (countryIgnoreList != null && countryCode != null) {
      if (countryIgnoreList.contains(countryCode)) {
        LOG.debug("Flagged for country: " + countryCode);
        formData.setFlaggedAsSpam(true);
        return true;
      }
    }

    // Some rules for dealing with junk data
    List<String> spamList = listMap.get(SPAM_LIST);
    List<String> emailIgnoreList = listMap.get(EMAIL_IGNORE_LIST);
    String location = GeoIPCommand.getCityStateCountryLocation(formData.getIpAddress());
    List<FormField> formFieldList = formData.getFormFieldList();
    for (FormField field : formFieldList) {
      if (StringUtils.isBlank(field.getUserValue())) {
        continue;
      }
      if (StringUtils.isNotBlank(field.getType()) && "textarea".equals(field.getType())) {
        String value = field.getUserValue().toLowerCase();
        if (spamList != null && spamList.stream().anyMatch(value::contains)) {
          LOG.debug("Flagged for textarea value");
          formData.setFlaggedAsSpam(true);
          return true;
        }
      } else if ("organization".equalsIgnoreCase(field.getName())) {
        String value = field.getUserValue().toLowerCase();
        if (value.equals("gsa")) {
          if (countryCode != null && !"US".equals(countryCode)) {
            LOG.debug("Flagged for organization and country");
            formData.setFlaggedAsSpam(true);
            return true;
          } else if (location != null &&
              (location.contains("Dallas") || location.contains("Los Angeles"))) {
            LOG.debug("Flagged for organization and location");
            formData.setFlaggedAsSpam(true);
            return true;
          }
        }
      } else if ("email".equalsIgnoreCase(field.getName())) {
        String value = field.getUserValue().toLowerCase();
        if (emailIgnoreList != null) {
          // Contains
          if (emailIgnoreList.stream()
              .filter(line -> !line.startsWith("*"))
              .anyMatch(value::contains)) {
            LOG.debug("Flagged for email address");
            formData.setFlaggedAsSpam(true);
            return true;
          }
          // Ends with
          if (emailIgnoreList.stream()
              .filter(line -> line.startsWith("*"))
              .anyMatch(term -> value.endsWith(term.substring(1)))) {
            LOG.debug("Flagged for email address");
            formData.setFlaggedAsSpam(true);
            return true;
          }
        }
      }
    }
    return false;
  }

}
