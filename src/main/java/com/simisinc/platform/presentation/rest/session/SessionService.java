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

package com.simisinc.platform.presentation.rest.session;

import com.simisinc.platform.presentation.controller.ServiceContext;
import com.simisinc.platform.presentation.controller.ServiceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 10/29/18 9:00 AM
 */
public class SessionService {

  private static Log LOG = LogFactory.getLog(SessionService.class);

  // POST /session
  public ServiceResponse post(ServiceContext context) {
    // @note likely won't get here because session is handled in RestRequestFilter
    return new ServiceResponse(200);
  }
}
