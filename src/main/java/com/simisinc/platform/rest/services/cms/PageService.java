/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

package com.simisinc.platform.rest.services.cms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.ValidateApiAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a single web page's public metadata by its link (issue #412).
 *
 * @author SimIS Inc.
 */
public class PageService {

  private static Log LOG = LogFactory.getLog(PageService.class);

  // GET /page/{pagePath} -- pagePath may itself contain "/" (e.g. "/about/team"). RestServlet's
  // 2-path-param router preserves this: pathParam2 holds everything after the second "/" verbatim
  // (it is not further split), so pathParam + "/" + pathParam2 always reconstructs the original
  // suffix exactly, regardless of how many segments it has. A trailing slash (e.g.
  // "/page/about/") yields pathParam2 == "" (not null) per RestServlet's substring logic, so an
  // empty pathParam2 is treated the same as "not present" rather than appended literally.
  public ServiceResponse get(ServiceContext context) {

    String pathParam = context.getPathParam();
    if (StringUtils.isBlank(pathParam)) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Page was not found");
      return response;
    }
    String pagePath = pathParam;
    if (StringUtils.isNotEmpty(context.getPathParam2())) {
      pagePath = pagePath + "/" + context.getPathParam2();
    }
    String link = "/" + pagePath;

    WebPage webPage = WebPageRepository.findByLink(link);
    if (webPage == null || !ValidateApiAccessToWebPageCommand.hasAccess(webPage, context.getUser())) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Page was not found");
      return response;
    }

    WebPageResponse webPageResponse = new WebPageResponse(webPage);

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "page");
    response.setData(webPageResponse);
    return response;
  }
}
