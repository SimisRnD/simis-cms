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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.application.datasets.DatasetFileCommand;
import com.simisinc.platform.application.cms.GenerateLinkFromNameCommand;
import com.simisinc.platform.domain.model.datasets.Dataset;
import com.simisinc.platform.infrastructure.persistence.datasets.DatasetRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import org.apache.commons.lang3.StringUtils;

import java.net.URL;
import java.util.*;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/26/20 4:36 PM
 */
public class LeaderboardWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/leaderboard.jsp";

  public WidgetContext execute(WidgetContext context) {

    // Common attributes
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Determine the dataset
    String datasetName = context.getPreferences().get("dataset");
    if (StringUtils.isBlank(datasetName)) {
      LOG.debug("Skipping - no dataset value found");
      return context;
    }
    Dataset dataset = DatasetRepository.findByName(datasetName);
    if (dataset == null) {
      LOG.debug("Skipping - dataset not found");
      return context;
    }

    // Determine the requested data
    String filter = context.getParameter("filter", "total").trim();
    context.getRequest().setAttribute("selectedFilter", filter);

    // Load the records
    try {
      List<String[]> rows = DatasetFileCommand.loadRows(dataset, -1, true);
      if (rows == null || rows.isEmpty()) {
        context.setWarningMessage("Dataset is empty");
        return context;
      }
      // Build a list of Column 1's Titles
      ArrayList<String> fields = new ArrayList<>();
      ArrayList<String> webFields = new ArrayList<>();
      String webFieldToShow = "total";
      for (String[] row : rows) {
        String columnValue = row[0];
        // Check if the key value is empty
        if (StringUtils.isBlank(columnValue)) {
          continue;
        }
        String thisField = columnValue.trim();
        String thisWebField = GenerateLinkFromNameCommand.getLink(thisField);
        if (filter.equals(thisWebField)) {
          webFieldToShow = thisWebField;
        }
        fields.add(thisField);
        webFields.add(thisWebField);
      }

      // Get each player's values
      ArrayList<HashMap> playerList = new ArrayList<>();
      int columnCount = 0;
      for (String column : dataset.getFieldTitles()) {
        ++columnCount;
        if (columnCount == 1) {
          continue;
        }
        String playerName = column;
        if (StringUtils.isBlank(playerName)) {
          continue;
        }
        HashMap<String, Object> player = new HashMap<>();
        player.put("NAME", playerName);

        // Check for an image
        if (fields.contains("IMAGE")) {
          // Only allow local validating URLs
          String image = null;
          try {
            image = ((String[]) rows.get(fields.indexOf("IMAGE")))[columnCount - 1];
            new URL(image);
            int imageIdx = image.indexOf("/assets/img/");
            if (imageIdx > -1) {
              image = image.substring(imageIdx);
              player.put("IMAGE", image);
            }
          } catch (Exception e) {
            LOG.error("Could not use url: " + image);
          }
        }

        // Use the specified points
        if (webFields.contains(webFieldToShow)) {
          String value = ((String[]) rows.get(webFields.indexOf(webFieldToShow)))[columnCount - 1];
          player.put("VALUE", parseLong(value));
        }

        // Put the score breakdown into the map
        LOG.debug(playerName);
        for (int i = fields.indexOf("TOTAL"); i < fields.size(); i++) {
          String name = fields.get(i).toLowerCase();
          String value = rows.get(i)[columnCount - 1];
          long score = parseLong(value);
          LOG.debug("  " + name + "=" + value);
          player.put(name, score);
        }
        playerList.add(player);
      }

      // Sort the list by the selected fieldToShow/value
      playerList.sort(Comparator.comparing(o -> (-(Long) ((HashMap) o).get("VALUE"))));

      // Make a list of filters
      Map<String, String> optionsList = new LinkedHashMap<>();
      optionsList.put("Total Points", "total");
      for (int i = fields.indexOf("TOTAL") + 1; i < fields.size(); i++) {
        String name = fields.get(i);
        String value = GenerateLinkFromNameCommand.getLink(name);
        optionsList.put(name, value);
      }

      // Prepare the JSP objects
      context.getRequest().setAttribute("playerList", playerList);
      context.getRequest().setAttribute("optionsList", optionsList);

    } catch (Exception e) {
      context.setErrorMessage("File type '" + dataset.getFileType() + "' could not be parsed... " + e.getMessage());
      LOG.warn("File type '" + dataset.getFileType() + "' could not be parsed... ", e);
      return context;
    }

    // Determine the view
    context.setJsp(JSP);
    return context;
  }

  private static final Long parseLong(String value) {
    if (StringUtils.isBlank(value)) {
      return 0L;
    }
    return Long.parseLong(StringUtils.getDigits(value));
  }

}
