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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.application.items.LoadCollectionCommand;
import com.simisinc.platform.application.items.LoadItemCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.items.Collection;
import com.simisinc.platform.domain.model.items.Item;
import com.simisinc.platform.domain.model.medicine.Medicine;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.medicine.MedicineRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 9/19/18 4:10 PM
 */
public class DeleteMedicineCommand {

  private static Log LOG = LogFactory.getLog(DeleteMedicineCommand.class);

  private static String ADMIN_USER_GROUP = "Program Administrator";
  private static String CAREGIVERS_UNIQUE_ID = "caregivers";
  private static String INDIVIDUALS_UNIQUE_ID = "individuals";

  /**
   * Delete the medicine and all of the dependencies
   *
   * @param medicine
   * @return
   * @throws DataException
   */
  public static boolean deleteMedicine(Medicine medicine, long userId) throws DataException {

    // Required dependencies
    if (medicine == null) {
      throw new DataException("The medicine was not specified");
    }

    // Check the collections for access
    Collection caregiversCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(CAREGIVERS_UNIQUE_ID, userId);
    Collection individualsCollection = LoadCollectionCommand.loadCollectionByUniqueIdForAuthorizedUser(INDIVIDUALS_UNIQUE_ID, userId);
    if (caregiversCollection == null || individualsCollection == null) {
      throw new DataException("The collections could not be found");
    }

    // Verify the Individual is in one of the Caregiver groups
    Item individual = LoadItemCommand.loadItemByIdWithinCollection(medicine.getIndividualId(), individualsCollection);
    if (individual == null) {
      throw new DataException("The individual could not be found");
    }

    // Make sure the user has the "Program Administrator" role
    User user = LoadUserCommand.loadUser(userId);
    Group group = GroupRepository.findByName(ADMIN_USER_GROUP);
    if (group == null || !user.hasGroup(group.getId())) {
      throw new DataException("The user is not authorized, please check with a program administrator");
    }

    // Delete the record
    return MedicineRepository.remove(medicine);
  }
}
