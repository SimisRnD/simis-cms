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
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Web pages in the repository
 *
 * @author matt rajkowski
 * @created 8/8/18 11:26 AM
 */
public class LoadWebPageCommand {

  private static Log LOG = LogFactory.getLog(LoadWebPageCommand.class);

  public static WebPage loadByLink(String pagePath) {
    // Look for web pages as-is
    WebPage webPage = WebPageRepository.findByLink(pagePath);
    if (webPage != null) {
      LOG.debug("Found web page: " + webPage.getLink());
      return webPage;
    }
    // Look for a dynamic page (.../* or .../.../*)
    int slashIndex = pagePath.indexOf("/", 1);
    if (slashIndex > 1) {
      int doubleSlashIndex = pagePath.indexOf("/", slashIndex + 1);
      if (doubleSlashIndex > 1) {
        // "/news/x/*"
        String alternatePage = pagePath.substring(0, doubleSlashIndex) + "/*";
        webPage = WebPageRepository.findByLink(alternatePage);
      }
      if (webPage == null) {
        // "/news/*"
        String alternatePage = pagePath.substring(0, slashIndex) + "/*";
        webPage = WebPageRepository.findByLink(alternatePage);
      }
      if (webPage == null) {
        // "/admin/documentation/wiki" ??
        // "/admin/documentation/wiki/Home" and name="/*"
        String alternatePage = pagePath.substring(0, slashIndex);
        LOG.debug("Checking for wildcard: " + alternatePage);
        WebPage testPage = WebPageRepository.findByLink(alternatePage);
        if (testPage != null && testPage.getPageXml().contains("name=\"/*\"")) {
          webPage = testPage;
        }
      }
    }
    if (LOG.isDebugEnabled()) {
      if (webPage != null) {
        LOG.debug("Found dynamic web page: " + webPage.getLink());
      }
    }
    return webPage;
  }
}
