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

import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 5/3/18 12:30 PM
 */
public class LoadContentCommand {

  private static Log LOG = LogFactory.getLog(LoadContentCommand.class);

  public static Content loadContentByUniqueId(String contentUniqueId) {
    if (StringUtils.isBlank(contentUniqueId)) {
      return null;
    }
    // Use the cache
    return (Content) CacheManager.getLoadingCache(CacheManager.CONTENT_UNIQUE_ID_CACHE).get(contentUniqueId);
  }
}
