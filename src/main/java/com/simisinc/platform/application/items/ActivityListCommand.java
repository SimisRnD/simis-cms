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

package com.simisinc.platform.application.items;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.UserCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.domain.model.items.Item;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/20/18 2:05 PM
 */
public class ActivityListCommand {

  private static Log LOG = LogFactory.getLog(ActivityListCommand.class);

  public static void createMessageHtml(List<Activity> activityList) {
    activityList.removeIf(activity -> !replaceAllVariables(activity));
  }

  private static boolean replaceAllVariables(Activity activity) {
    String messageHtml = HtmlCommand.toHtml(activity.getMessageText());
    while (messageHtml.contains("${")) {
      messageHtml = replaceNextVariable(messageHtml);
    }
    activity.setMessageHtml(messageHtml);
    return !messageHtml.contains("&lt;unknown&gt;");
  }

  private static String replaceNextVariable(String messageHtml) {
    int startIdx = messageHtml.indexOf("${");
    if (startIdx == -1) {
      return messageHtml;
    }
    int endIdx = messageHtml.indexOf("}", startIdx);
    if (endIdx == -1) {
      // It's not valid, but needs to be replaced anyhow
      messageHtml = messageHtml.substring(0, startIdx) + " " + HtmlCommand.toHtml("<error>");
      return messageHtml;
    }
    // Found a variable
    String extractedValue = messageHtml.substring(startIdx + 2, endIdx);
    if (!extractedValue.contains(":")) {
      // Just a type?
      messageHtml = messageHtml.substring(0, startIdx) + HtmlCommand.toHtml(extractedValue) + messageHtml.substring(endIdx + 1);
      return messageHtml;
    } else {
      // Look up the object
      int pIndex = extractedValue.indexOf(":");
      String type = extractedValue.substring(0, pIndex);
      String id = extractedValue.substring(pIndex + 1);
      String htmlValue = retrieveObjectHtmlValue(type, id);
      messageHtml = messageHtml.substring(0, startIdx) + htmlValue + messageHtml.substring(endIdx + 1);
      return messageHtml;
    }
  }

  private static String retrieveObjectHtmlValue(String type, String id) {

    // Determine the object and construct a link
    if ("user".equals(type)) {
      // Show the name of the user
      User user = LoadUserCommand.loadUser(Long.parseLong(id));
      if (user != null) {
        return "<strong>" + HtmlCommand.toHtml(UserCommand.name(user)) + "</strong>";
      }
    } else if ("item".equals(type)) {
      // Show the name of the item, link to it
      Item item = LoadItemCommand.loadItemById(Long.parseLong(id));
      if (item != null) {
        return ("<a href=\"#\">" + ItemCommand.name(item) + "</a>");
      }
    }
    return HtmlCommand.toHtml("<unknown>");
  }

}
