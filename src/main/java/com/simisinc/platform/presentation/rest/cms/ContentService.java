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

package com.simisinc.platform.presentation.rest.cms;

import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.presentation.controller.ServiceContext;
import com.simisinc.platform.presentation.controller.ServiceResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/17/18 9:00 AM
 */
public class ContentService {

  private static Log LOG = LogFactory.getLog(ContentService.class);

  // GET /content/{contentUniqueId}
  public ServiceResponse get(ServiceContext context) {

    String contentUniqueId = context.getPathParam();
    Content content = LoadContentCommand.loadContentByUniqueId(contentUniqueId);
    if (content == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Content was not found");
      return response;
    }

    // Set the fields to return
    ContentHandler contentHandler = new ContentHandler(content);

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    response.getMeta().put("type", "content");
    response.getMeta().put("currentPage", 1);
    response.getMeta().put("pages", 1);
    response.setData(contentHandler);
    return response;
  }

}
