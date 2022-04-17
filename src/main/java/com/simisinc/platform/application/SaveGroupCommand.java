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

package com.simisinc.platform.application;

import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.simisinc.platform.application.GenerateGroupUniqueIdCommand.generateUniqueId;

/**
 * Validates and saves a group object
 *
 * @author matt rajkowski
 * @created 4/24/18 8:58 AM
 */
public class SaveGroupCommand {

  private static Log LOG = LogFactory.getLog(SaveGroupCommand.class);

  public static Group saveGroup(Group groupBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(groupBean.getName())) {
      errorMessages.append("A name is required");
    }

    // Validate it's a unique name
    if (groupBean.getId() == -1 && GroupRepository.findByName(groupBean.getName()) != null) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("A unique name is required");
    }

    // Validate the unique id
    if (StringUtils.isNotBlank(groupBean.getUniqueId()) && !GenerateGroupUniqueIdCommand.isValid(groupBean.getUniqueId())) {
      if (errorMessages.length() > 0) {
        errorMessages.append(", ");
      }
      errorMessages.append("The uniqueId contains invalid characters");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Group group;
    if (groupBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      group = GroupRepository.findById(groupBean.getId());
      if (group == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      group = new Group();
    }
    // @note set the uniqueId before setting the name
    group.setUniqueId(generateUniqueId(group, groupBean));
    group.setName(groupBean.getName());
    group.setDescription(groupBean.getDescription());
    return GroupRepository.save(group);
  }

}
