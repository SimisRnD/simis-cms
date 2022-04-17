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

import com.simisinc.platform.domain.model.items.Activity;
import com.simisinc.platform.infrastructure.persistence.items.ActivityRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads an activity object from cache or storage
 *
 * @author matt rajkowski
 * @created 8/21/18 8:00 AM
 */
public class LoadActivityCommand {

  private static Log LOG = LogFactory.getLog(LoadActivityCommand.class);

  public static Activity loadActivityById(long itemId) {
    // @todo use a cache
    return ActivityRepository.findById(itemId);
  }

}
