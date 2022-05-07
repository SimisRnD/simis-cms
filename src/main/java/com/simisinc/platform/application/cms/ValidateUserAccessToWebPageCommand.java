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

import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.WebComponentCommand;
import com.simisinc.platform.presentation.controller.cms.Column;
import com.simisinc.platform.presentation.controller.cms.Page;
import com.simisinc.platform.presentation.controller.cms.Section;
import com.simisinc.platform.presentation.controller.cms.Widget;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates access to a link's web page object
 *
 * @author matt rajkowski
 * @created 8/8/18 11:26 AM
 */
public class ValidateUserAccessToWebPageCommand {

  private static Log LOG = LogFactory.getLog(ValidateUserAccessToWebPageCommand.class);

  public static boolean hasAccess(String link, UserSession userSession) {
    if (userSession.hasRole("admin") || userSession.hasRole("content-manager")) {
      return true;
    }
    WebPage webPage = LoadWebPageCommand.loadByLink(link);
    if (webPage == null) {
      return false;
    }
    // The page is a draft
    if (webPage.getDraft()) {
      return false;
    }
    // The page is empty
    if (StringUtils.isBlank(webPage.getPageXml())) {
      return false;
    }
    // The user does not have access to any widgets on the page
    Page pageRef = WebPageXmlLayoutCommand.retrievePageForRequest(webPage, link);
    if (pageRef == null) {
      return false;
    }
    // Best to place the group list at the page level, to cover the whole page
    if (!WebComponentCommand.allowsUser(pageRef, userSession)) {
      return false;
    }
    for (Section section : pageRef.getSections()) {
      if (!WebComponentCommand.allowsUser(section, userSession)) {
        return false;
      }
      for (Column column : section.getColumns()) {
        if (!WebComponentCommand.allowsUser(column, userSession)) {
          return false;
        }
        for (Widget widget : column.getWidgets()) {
          if (!WebComponentCommand.allowsUser(widget, userSession)) {
            return false;
          }
          // @note the widget content response is not tested
          return true;
        }
      }
    }
    return false;
  }
}
