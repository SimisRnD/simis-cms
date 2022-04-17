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

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Methods for woroking with TinyMCE content
 *
 * @author matt rajkowski
 * @created 3/2/20 10:00 PM
 */
public class TinyMceCommand {

  private static Log LOG = LogFactory.getLog(TinyMceCommand.class);

  private static final String[] TINY_MCE_ICON_TAGS = new String[]{"span", "em"};
  private static final String[] FA_ICON_CSS = new String[]{"far", "fas", "fal", "fad", "fab", "fa"};

  // Set the icon tags for use in TinyMCE
  public static String prepareContentForEditor(String contentHtml) {
    if (StringUtils.isBlank(contentHtml)) {
      return contentHtml;
    }
    // Swap the tags
    return replaceIconTagsInContent(contentHtml, "i", "span", false);
  }

  // Replace icon tags from TinyMCE with FontAwesome tags
  public static String updateContentFromEditor(String contentHtml) {
    if (StringUtils.isBlank(contentHtml)) {
      return contentHtml;
    }
    // Swap the tags
    for (String tag : TINY_MCE_ICON_TAGS) {
      contentHtml = replaceIconTagsInContent(contentHtml, tag, "i", true);
    }
    return contentHtml;
  }

  private static String replaceIconTagsInContent(String contentHtml, String tag, String newTag, boolean fromTinyMCE) {
    // for (String tag : TINY_MCE_ICON_TAGS (em,span)) {
    //     replaceIconTagsInContent(contentHtml, tag, "i", true);
    // Content received will look like: <em class="far fa-code"></em> <em class="far fa-code-2"></em>
    if (tag.equals(newTag)) {
      return contentHtml;
    }
    if (!contentHtml.contains("<" + tag)) {
      return contentHtml;
    }
    int tagIdx = 0;
    int endTagIdx = 0;
    int endTagLength = tag.length() + 3;
    while (tagIdx > -1) {
      // <em
      tagIdx = contentHtml.indexOf("<" + tag + " ", tagIdx);
      if (tagIdx == -1) {
        break;
      }
      // </em>
      endTagIdx = contentHtml.indexOf("</" + tag + ">", tagIdx);
      if (endTagIdx == -1) {
        break;
      }

      LOG.trace("TAG IDX (tagIdx:" + tagIdx + "; endTagIdx:" + endTagIdx + ")");

      // Look for a class attribute in-between
      int classIdx = contentHtml.indexOf("class=\"", tagIdx);
      if (classIdx == -1 || classIdx > endTagIdx) {
        tagIdx = endTagIdx + endTagLength;
        continue;
      }
      int endClassIdx = contentHtml.indexOf("\"", classIdx + 7);
      if (endClassIdx == -1) {
        tagIdx = endTagIdx + endTagLength;
        continue;
      }
      // If the class values contain 1 or more of the cssClassArray, switch this to required <i></i>
      String classValue = contentHtml.substring(classIdx + 7, endClassIdx).trim();
      LOG.trace("classValue: " + classValue);
      if (classValue.length() > 0) {
        List<String> cssValueList = Stream.of(classValue.split(" ")).map(String::trim).collect(toList());
        if (CollectionUtils.containsAny(cssValueList, FA_ICON_CSS)) {
          if (fromTinyMCE) {
            // Remove TinyMCE editor
            cssValueList.remove("tinymce-noedit");
          } else {
            // Add TinyMCE editor
            cssValueList.add("tinymce-noedit");
          }
          // Switch the tag content
          contentHtml =
              contentHtml.substring(0, tagIdx) + "<" + newTag + " class=\"" + StringUtils.join(cssValueList, " ") + "\">" +
                  (fromTinyMCE ? "" : "&nbsp;") +
              "</" + newTag + ">" + contentHtml.substring(endTagIdx + endTagLength);
          tagIdx = contentHtml.indexOf("</" + newTag + ">", tagIdx + 1) + newTag.length() + 3;
          continue;
        }
      }
      tagIdx = endTagIdx + endTagLength;
    }
    return contentHtml;
  }
}
