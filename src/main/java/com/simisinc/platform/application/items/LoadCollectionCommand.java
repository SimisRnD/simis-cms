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

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.infrastructure.cache.CacheManager;
import com.simisinc.platform.infrastructure.persistence.items.CollectionRepository;
import com.simisinc.platform.infrastructure.persistence.items.CollectionSpecification;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Loads a collection object from cache or storage
 *
 * @author matt rajkowski
 * @created 4/23/18 3:39 PM
 */
public class LoadCollectionCommand {

  private static Log LOG = LogFactory.getLog(LoadCollectionCommand.class);

  public static Collection loadCollectionByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (Collection) CacheManager.getLoadingCache(CacheManager.COLLECTION_UNIQUE_ID_CACHE).get(uniqueId);
  }

  public static Collection loadCollectionById(long collectionId) {
    return CollectionRepository.findById(collectionId);
  }


  public static Collection loadCollectionByIdForAuthorizedUser(long collectionId, long userId) {
    if (collectionId < 1) {
      return null;
    }
    CollectionSpecification specification = new CollectionSpecification();
    specification.setId(collectionId);
    specification.setForUserId(userId);
    List<Collection> collectionList = CollectionRepository.findAll(specification, null);
    if (collectionList.size() == 1) {
      return collectionList.get(0);
    }
    return null;
  }

  public static Collection loadCollectionByUniqueIdForAuthorizedUser(String uniqueId, long userId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    CollectionSpecification specification = new CollectionSpecification();
    specification.setUniqueId(uniqueId);
    specification.setForUserId(userId);
    List<Collection> collectionList = CollectionRepository.findAll(specification, null);
    if (collectionList.size() == 1) {
      return collectionList.get(0);
    }
    return null;
  }

  public static List<Collection> findAllAuthorizedForUser(long userId) {
    // @todo determine records
    // Some users are admins
    // Some collections map to a user group
    CollectionSpecification specification = new CollectionSpecification();
    // if an admin, then skip this...
    specification.setForUserId(userId);
    return CollectionRepository.findAll(specification, null);
  }

}
