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
import com.simisinc.platform.presentation.controller.XMLPageLoader;
import com.simisinc.platform.presentation.controller.cms.Page;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/8/21 9:45 PM
 */
public class WebPageXmlLayoutCommand {

  private static Log LOG = LogFactory.getLog(WebPageXmlLayoutCommand.class);

  private static XMLPageLoader pages = new XMLPageLoader(new HashMap<>());
  private static XMLPageLoader customPages = new XMLPageLoader(new HashMap<>());

  public static Map<String, String> init(ServletContext servletContext) {
    // Load the widget library, the XML validates against it
    pages.loadWidgetLibrary(servletContext, "/WEB-INF/widgets/widget-library.xml");
    customPages.setWidgetLibrary(pages.getWidgetLibrary());

    // Load the page layouts and json services
    LOG.info("Loading the page layouts from a directory...");
    pages.addDirectory(servletContext, "web-layouts/page");
    pages.addDirectory(servletContext, "web-layouts/collection");
    // @todo remove when json services are separate
    pages.addDirectory(servletContext, "json-services");
    pages.load(servletContext);

    // Test and Pre-process the XML layouts
    LOG.info("Loading the custom page layouts...");
    List<WebPage> webPageList = WebPageRepository.findAll();
    for (WebPage webPage : webPageList) {
      try {
        if (webPage.getPageXml() != null) {
          customPages.addFromXml(webPage.getLink(), webPage);
        }
      } catch (Exception e) {
        LOG.error("Page XML Error: " + webPage.getLink() + " " + e.getMessage(), e);
      }
    }
    return pages.getWidgetLibrary();
  }

  public static void reloadPages(ServletContext servletContext) {
    pages.load(servletContext);
  }

  public static boolean containsPage(String name) {
    return pages.containsKey(name);
  }

  public static Page retrievePage(String name) {
    return pages.get(name);
  }

  public static void removeCustomPage(String name) {
    customPages.remove(name);
  }

  public static Map<String, String> getWidgetLibrary() {
    return pages.getWidgetLibrary();
  }

  public static Page retrievePageForRequest(WebPage webPage, String pagePath) {
    // Check the system cache for this page (or link?)
    Page pageRef = pages.get(pagePath);
    if (pageRef != null) {
      LOG.debug("Found page: " + pagePath);
    }
    if (pageRef == null) {
      // Check the custom pages
      if (webPage != null) {
        // Try the link first for dynamic pages
        pageRef = customPages.get(webPage.getLink());
      }
      if (pageRef == null) {
        pageRef = customPages.get(pagePath);
      }
    }

    // Determine the best source of the page design (it may have expired)
    if (pageRef == null) {
      // Check the Web Page Repository for more info
      if (webPage != null) {
        if (StringUtils.isNotBlank(webPage.getPageXml())) {
          pageRef = customPages.get(pagePath);
          if (pageRef == null) {
            try {
              // @todo pre-process these
              LOG.debug("Creating page from XML...");
              pageRef = customPages.addFromXml(pagePath, webPage);
            } catch (Exception e) {
              LOG.error("The XML had an error...", e);
            }
          }
        } else if (StringUtils.isNotBlank(webPage.getTemplate())) {
          LOG.debug("Looking for XML template: " + webPage.getTemplate());
          pageRef = pages.get(webPage.getTemplate());
        }
      }

      // Still not found? Look for an alternate page name...
      // /admin/documentation/wiki/Home
      if (pageRef == null) {
        int slashIndex = pagePath.indexOf("/", 1);
        if (slashIndex > 1) {
          // /show/itemUniqueId
          // /show/itemUniqueId/settings
          // /show/itemUniqueId/assets/file
          int doubleSlashIndex = pagePath.indexOf("/", slashIndex + 1);
          String[] pathArray = pagePath.substring(1).split("/");
          if ("show".equals(pathArray[0])) {
            if (doubleSlashIndex > -1) {
              pageRef = locatePage("/show/*" + pagePath.substring(doubleSlashIndex));
            } else {
              pageRef = pages.get("/show/*");
            }
            if (pageRef == null) {
              // Always return the base page for later expanding the item
              pageRef = pages.get("/show/*/not-configured");
            }
          } else {
            pageRef = locatePage(pagePath);
          }
        }
        // @todo customize the page by collection
        // @todo customize the page by item and tab
      }
    }
    return pageRef;
  }

  private static Page locatePage(String pagePath) {
    LOG.debug("Locate page: " + pagePath);

    // Check the given name as-is
    Page pageRef = pages.get(pagePath);
    if (pageRef != null) {
      return pageRef;
    }

    // Break down the url to search for parts of it, to remove variables
    int slashIndex = pagePath.indexOf("/", 1);
    int doubleSlashIndex = pagePath.indexOf("/", slashIndex + 1);
    int tripleSlashIndex = pagePath.indexOf("/", doubleSlashIndex + 1);
    int quadrupleSlashIndex = pagePath.indexOf("/", tripleSlashIndex + 1);

    // Look for /show/*/assets/view
    if (quadrupleSlashIndex > 1) {
      String alternatePage = pagePath.substring(0, quadrupleSlashIndex);
      LOG.debug("Looking for alternate XML page (4): " + alternatePage);
      pageRef = pages.get(alternatePage);
    }
    // Look for /show/*/assets
    if (pageRef == null && tripleSlashIndex > 1) {
      String alternatePage = pagePath.substring(0, tripleSlashIndex);
      LOG.debug("Looking for alternate XML page (3): " + alternatePage);
      pageRef = pages.get(alternatePage);
    }
    if (!pagePath.startsWith("/show/*")) {
      // Look for /news/*
      if (pageRef == null && doubleSlashIndex > 1) {
        String alternatePage = pagePath.substring(0, doubleSlashIndex);
        LOG.debug("Looking for alternate XML page (2): " + alternatePage);
        pageRef = pages.get(alternatePage);
      }
      // Look for /news
      if (pageRef == null) {
        String alternatePage = pagePath.substring(0, slashIndex);
        LOG.debug("Looking for alternate XML page (1): " + alternatePage);
        pageRef = pages.get(alternatePage);
      }
    }
    return pageRef;
  }
}
