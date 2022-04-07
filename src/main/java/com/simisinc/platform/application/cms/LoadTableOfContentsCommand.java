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

import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/20/19 8:41 AM
 */
public class LoadTableOfContentsCommand {

  private static Log LOG = LogFactory.getLog(LoadTableOfContentsCommand.class);

  public static TableOfContents loadByUniqueId(String tableOfContentsUniqueId, boolean clone) {
    TableOfContents cachedTableOfContents = (TableOfContents) CacheManager.getLoadingCache(CacheManager.TABLE_OF_CONTENTS_UNIQUE_ID_CACHE).get(tableOfContentsUniqueId);
    if (!clone || cachedTableOfContents == null) {
      return cachedTableOfContents;
    }

    // Make a deep copy
    TableOfContents tableOfContents = new TableOfContents();
    tableOfContents.setId(tableOfContents.getId());
    tableOfContents.setName(tableOfContents.getName());
    tableOfContents.setTocUniqueId(tableOfContents.getTocUniqueId());
    List<TableOfContentsLink> entries = new ArrayList<>();
    for (TableOfContentsLink cachedLink : cachedTableOfContents.getEntries()) {
      TableOfContentsLink link = new TableOfContentsLink();
      link.setId(cachedLink.getId());
      link.setName(cachedLink.getName());
      link.setLink(cachedLink.getLink());
      link.setActive(cachedLink.isActive());
      entries.add(link);
    }
    tableOfContents.setEntries(entries);
    return tableOfContents;
  }

}
