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

package com.simisinc.platform.presentation.rest.userProfile;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.presentation.controller.ServiceContext;
import com.simisinc.platform.presentation.controller.ServiceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 8/28/18 9:38 AM
 */
public class MeService {

  private static Log LOG = LogFactory.getLog(MeService.class);

  // GET /me
  public ServiceResponse get(ServiceContext context) {

    User user = context.getUser();

    // Set the fields to return
    MeHandler handler = new MeHandler(user);

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    response.getMeta().put("type", "user");
    response.setData(handler);
    return response;
  }

}
