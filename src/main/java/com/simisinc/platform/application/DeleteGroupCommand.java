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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods to delete a group
 *
 * @author matt rajkowski
 * @created 4/24/18 9:33 AM
 */
public class DeleteGroupCommand {

  private static Log LOG = LogFactory.getLog(DeleteGroupCommand.class);

  public static boolean deleteGroup(Group groupBean) throws DataException {

    // Verify the object
    if (groupBean == null || groupBean.getId() == -1) {
      throw new DataException("The group was not specified");
    }

    GroupRepository.remove(groupBean);
    return true;
  }

}
