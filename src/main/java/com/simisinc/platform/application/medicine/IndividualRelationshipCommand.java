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

package com.simisinc.platform.application.medicine;

import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.infrastructure.persistence.items.ItemRelationshipRepository;
import com.simisinc.platform.infrastructure.persistence.items.ItemRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/28/18 10:34 AM
 */
public class IndividualRelationshipCommand {

  private static Log LOG = LogFactory.getLog(IndividualRelationshipCommand.class);

  public static boolean isAuthorizedForUser(Item individual, Collection caregiverCollection, long userId) {
    return ItemRelationshipRepository.isAuthorizedForUser(individual, caregiverCollection, userId);
  }

  public static boolean isAuthorizedForUser(Item individual, Collection caregiverCollection, long userId, long collectionRoleId) {
    return ItemRelationshipRepository.isAuthorizedForUser(individual, caregiverCollection, userId, collectionRoleId);
  }

}
