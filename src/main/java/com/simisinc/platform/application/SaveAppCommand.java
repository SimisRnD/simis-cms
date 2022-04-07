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

import com.simisinc.platform.domain.model.App;
import com.simisinc.platform.infrastructure.persistence.AppRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.UUID;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/30/18 8:41 AM
 */
public class SaveAppCommand {

  private static Log LOG = LogFactory.getLog(SaveAppCommand.class);

  public static App saveApp(App appBean) throws DataException {

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(appBean.getName())) {
      errorMessages.append("A name is required");
    }

    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    App app;
    if (appBean.getId() > -1) {
      LOG.debug("Saving an existing record... ");
      app = AppRepository.findById(appBean.getId());
      if (app == null) {
        throw new DataException("The existing record could not be found");
      }
    } else {
      LOG.debug("Saving a new record... ");
      app = new App();
      app.setPublicKey(generateKey());
      app.setPrivateKey(generateKey());
    }
    app.setCreatedBy(appBean.getCreatedBy());
    app.setName(appBean.getName());
    app.setSummary(appBean.getSummary());
    return AppRepository.save(app);
  }

  private static String generateKey() {
    return UUID.randomUUID().toString();
  }

}
