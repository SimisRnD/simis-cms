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

import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.presentation.controller.XMLFooterLoader;
import com.simisinc.platform.presentation.controller.XMLHeaderLoader;
import com.simisinc.platform.presentation.controller.cms.Footer;
import com.simisinc.platform.presentation.controller.cms.Header;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContext;
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 2/8/21 5:06 PM
 */
public class WebContainerCommand {

  private static Log LOG = LogFactory.getLog(WebContainerCommand.class);

  public static Header retrieveHeader(ServletContext servletContext, Map<String, String> widgetLibrary) {
    // Use the header preference?
//    String layoutValue = LoadSitePropertyCommand.loadByName("theme.header.layout");
//    if (!"default".equals(layoutValue)) {
//      layout = layoutValue;
//    }
    Header header = (Header) CacheManager.getFromObjectCache(CacheManager.WEBSITE_HEADER);
    if (header == null) {
      header = XMLHeaderLoader.retrieveHeader(servletContext, widgetLibrary, "header.default");
      CacheManager.addToObjectCache(CacheManager.WEBSITE_HEADER, header);
    }
    return header;
  }

  public static Header retrievePlainHeader(ServletContext servletContext, Map<String, String> widgetLibrary) {
    Header plainHeader = (Header) CacheManager.getFromObjectCache(CacheManager.WEBSITE_PLAIN_HEADER);
    if (plainHeader == null) {
      plainHeader = XMLHeaderLoader.retrieveHeader(servletContext, widgetLibrary, "header.plain");
      CacheManager.addToObjectCache(CacheManager.WEBSITE_PLAIN_HEADER, plainHeader);
    }
    return plainHeader;
  }

  public static Footer retrieveFooter(ServletContext servletContext, Map<String, String> widgetLibrary) {
    Footer footer = (Footer) CacheManager.getFromObjectCache(CacheManager.WEBSITE_FOOTER);
    if (footer == null) {
      footer = XMLFooterLoader.retrieveFooter(servletContext, widgetLibrary, "footer.default");
      CacheManager.addToObjectCache(CacheManager.WEBSITE_FOOTER, footer);
    }
    return footer;
  }

  public static void populateCache(ServletContext servletContext, Map<String, String> widgetLibrary) {
    retrieveHeader(servletContext, widgetLibrary);
    retrievePlainHeader(servletContext, widgetLibrary);
    retrieveFooter(servletContext, widgetLibrary);
  }
}
