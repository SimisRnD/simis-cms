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

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.infrastructure.persistence.cms.ImageRepository;

/**
 * Methods for updating image links in content
 *
 * @author matt rajkowski
 * @created 10/5/2022 8:10 PM
 */
public class ReplaceImagePathCommand {

  private static Log LOG = LogFactory.getLog(ReplaceImagePathCommand.class);

  /**
   * Replaces dynamic values for blog posts
   *
   * @param blogPost
   * @param value
   * @return
   */
  public static String updateImageReferences(String originalContent) {
    String content = originalContent;

    int idx = 0;

    while (idx > -1) {

      // Find the content
      idx = content.indexOf("/assets/img/", idx);
      if (idx < 0) {
        // didn't find an image
        break;
      }

      // Extract the value...
      int startIdx = idx + "/assets/img/".length();
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
      Image image = ImageRepository.findById(fileId);
      if (image == null) {
        continue;
      }

      // Confirm a change is needed
      if (webPath.equals(image.getWebPath())) {
        continue;
      }

      // Replace the value
      String newWebPath = image.getWebPath();
      content = StringUtils.replace(content, "/assets/img/" + webPath, "/assets/img/" + newWebPath);
    }

    return content;
  }

}
