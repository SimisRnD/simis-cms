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

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.infrastructure.persistence.items.ActivityRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Validates and saves an activity object
 *
 * @author matt rajkowski
 * @created 8/21/18 8:00 AM
 */
public class SaveActivityCommand {

  private static Log LOG = LogFactory.getLog(SaveActivityCommand.class);

  public static Activity saveActivity(Activity activityBean) throws DataException {

    // Required dependencies
    if (activityBean.getItemId() == -1) {
      throw new DataException("An item is required");
    }
    if (activityBean.getCollectionId() == -1) {
      throw new DataException("A collection is required");
    }

    // Validate the fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(activityBean.getMessageText())) {
      errorMessages.append("A message is required");
    }
    if (StringUtils.isBlank(activityBean.getActivityType())) {
      errorMessages.append("An activity type is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    Activity activity;
    if (activityBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      activity = ActivityRepository.findById(activityBean.getId());
      if (activity == null) {
        throw new DataException("The existing record could not be found");
      }
      if (activity.getItemId() != activityBean.getItemId()) {
        throw new DataException("There is an item mismatch");
      }
    } else {
      LOG.debug("Saving a new record... ");
      activity = new Activity();
      activity.setItemId(activityBean.getItemId());
      activity.setCollectionId(activityBean.getCollectionId());
      activity.setCreatedBy(activityBean.getCreatedBy());
      activity.setActivityType(activityBean.getActivityType());
    }
    activity.setMessageText(activityBean.getMessageText());
    activity.setModifiedBy(activityBean.getModifiedBy());
    return ActivityRepository.save(activity);
  }

}
