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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.FileItem;
import com.simisinc.platform.infrastructure.persistence.cms.FileItemRepository;

/**
 * Methods for updating file links in content
 *
 * @author matt rajkowski
 * @created 11/19/2022 7:57 AM
 */
public class ReplaceFilePathCommand {

  private static Log LOG = LogFactory.getLog(ReplaceFilePathCommand.class);

  /**
   * Update file values in content
   *
   * @param content
   * @param value
   * @return
   */
  public static String updateFileReferences(String originalContent) {
    String newContent = updateFileReferences(originalContent, "/assets/view/");
    newContent = updateFileReferences(newContent, "/assets/file/");
    return newContent;
  }
    
  private static String updateFileReferences(String originalContent, String searchValue) {
    String content = originalContent;

    int idx = 0;

    while (idx > -1) {

      // Find the content
      idx = content.indexOf(searchValue, idx);
      if (idx < 0) {
        // didn't find a match
        break;
      }

      // Extract the value...
      int startIdx = idx + searchValue.length();
      int endIdx = content.indexOf("/", startIdx);
      if (endIdx <= 0) {
        // reached an invalid condition
        break;
      }

      // The next index to start from after this pass
      idx = startIdx;

      // Verify there is a dash
      String resourceValue = content.substring(startIdx, endIdx);
      int dashIdx = resourceValue.lastIndexOf("-");
      if (dashIdx == -1) {
        continue;
      }

      // Determine the web path and file id
      String webPath = resourceValue.substring(0, dashIdx);
      String fileIdValue = resourceValue.substring(dashIdx + 1);
      long fileId = Long.parseLong(fileIdValue);
      if (fileId <= 0) {
        continue;
      }

      // Get the image
      FileItem file = FileItemRepository.findById(fileId);
      if (file == null) {
        continue;
      }

      // Confirm a change is needed
      if (webPath.equals(file.getWebPath())) {
        continue;
      }

      // Replace the value
      String newWebPath = file.getWebPath();
      content = StringUtils.replace(content, searchValue + webPath, searchValue + newWebPath);
    }

    return content;
  }

}
