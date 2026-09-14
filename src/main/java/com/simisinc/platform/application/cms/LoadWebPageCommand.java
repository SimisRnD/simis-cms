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
import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads a link's web page object
 *
 * @author matt rajkowski
 * @created 8/8/18 11:26 AM
 */
public class LoadWebPageCommand {

  private static Log LOG = LogFactory.getLog(LoadWebPageCommand.class);

  public static WebPage loadByLink(String pagePath) {
    // Look for web pages as-is
    WebPage webPage = findByLinkCached(pagePath);
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
        webPage = findByLinkCached(alternatePage);
      }
      if (webPage == null) {
        // "/news/*"
        String alternatePage = pagePath.substring(0, slashIndex) + "/*";
        webPage = findByLinkCached(alternatePage);
      }
      if (webPage == null) {
        // "/admin/documentation/wiki" ??
        // "/admin/documentation/wiki/Home" and name="/*"
        String alternatePage = pagePath.substring(0, slashIndex);
        LOG.debug("Checking for wildcard: " + alternatePage);
        WebPage testPage = findByLinkCached(alternatePage);
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

  /**
   * The cached form of {@link WebPageRepository#findByLink(String)} (issue #2034).
   *
   * <p>This method is called up to four times per {@link #loadByLink(String)} -- the exact path,
   * then two wildcard shapes, then the parent segment -- and {@code loadByLink} is itself called
   * once per main-menu entry on every anonymous render, via
   * {@code ValidateUserAccessToWebPageCommand.hasAccess}. Uncached that measured ~50 database
   * queries per page render; cached it is ~50 in-memory lookups.
   *
   * <p>{@code WebPage.NONE} is the cache's stand-in for "no row", translated back to {@code null}
   * here so no caller outside this method ever sees the sentinel.
   */
  private static WebPage findByLinkCached(String link) {
    if (StringUtils.isBlank(link)) {
      return null;
    }
    WebPage result = (WebPage) CacheManager.getLoadingCache(CacheManager.WEB_PAGE_CACHE).get(link);
    return result == WebPage.NONE ? null : result;
  }

}
