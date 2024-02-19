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

import com.simisinc.platform.domain.model.cms.Stylesheet;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads the stylesheet for a web page
 *
 * @author matt rajkowski
 * @created 2/1/2021 8:00 AM
 */
public class LoadStylesheetCommand {

  private static Log LOG = LogFactory.getLog(LoadStylesheetCommand.class);

  public static Stylesheet loadStylesheetByWebPageId(long webPageId) {
    // @todo keep track of web pages without a custom stylesheet so the cache hit can be avoided
    // If no style then store the webPageId in a cache (invalidate the item when a style is set)
    
    // Use the cache
    return (Stylesheet) CacheManager.getLoadingCache(CacheManager.STYLESHEET_WEB_PAGE_ID_CACHE).get(webPageId);
  }
}
