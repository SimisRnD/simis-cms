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

import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.infrastructure.persistence.cms.BlogRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads a blog object from cache or storage
 *
 * @author matt rajkowski
 * @created 8/7/18 10:48 AM
 */
public class LoadBlogCommand {

  private static Log LOG = LogFactory.getLog(LoadBlogCommand.class);

  public static Blog loadBlogByUniqueId(String blogUniqueId) {
//    return (Blog) CacheManager.getLoadingCache(CacheManager.COLLECTION_UNIQUE_ID_CACHE).get(uniqueId);
    return BlogRepository.findByUniqueId(blogUniqueId);
  }

  public static Blog loadBlogById(long blogId) {
    return BlogRepository.findById(blogId);
  }

}
