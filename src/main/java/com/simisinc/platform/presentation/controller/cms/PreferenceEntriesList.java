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

package com.simisinc.platform.presentation.controller.cms;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/10/18 8:42 AM
 */
public class PreferenceEntriesList extends ArrayList<Map<String, String>> {

  private static Log LOG = LogFactory.getLog(PreferenceEntriesList.class);

  public PreferenceEntriesList() {

  }

  public PreferenceEntriesList(String data) {

    // Data format:
    // name=Folders|value=/admin/folders||name=Sub-Folder|value=/admin/folder-details?folderId={param:folderId}||name=Files|value=
    LOG.debug(data);

    // Determine the entries
    List<String> entries = Stream.of(data.split("\\|\\|\\|"))
        .map(String::trim)
        .collect(toList());

    for (String entry : entries) {

      // Each entry is semi-colon separated, for now...
      List<String> pairs = Stream.of(entry.split("\\|\\|"))
          .map(String::trim)
          .collect(toList());

      // Input: [label | name], value (html name), type, placeHolder, defaultValue, userValue
      Map<String, String> valueMap = new HashMap<>();
      for (String pair : pairs) {
        String[] nameValue = pair.split("\\|");
        if (nameValue.length > 1) {
          valueMap.put(nameValue[0], nameValue[1]);
        } else {
          valueMap.put(nameValue[0], "");
        }
      }
      this.add(valueMap);
    }
  }
}
