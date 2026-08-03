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

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.ValidateApiAccessToWebPageCommand;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Returns a paginated list of web pages visible to the caller (issue #412), using the same
 * {@link ValidateApiAccessToWebPageCommand} gate as {@link PageService} -- draft/blank-pageXml,
 * publish schedule, and widget/section/column role/group/capability access. {@code enabled}/
 * {@code draft} are NOT used as a SQL pre-filter here since neither one alone matches the real
 * gate (see {@link ValidateApiAccessToWebPageCommand}'s javadoc).
 * <p>
 * Known limitation: the per-page visibility check runs in Java, after the DB-level
 * page/size pagination, so a returned page can legitimately contain fewer than {@code size}
 * entries when some pages within that DB window aren't visible to the caller -- this trades an
 * exactly-full page for not leaking access-check logic into SQL. Revisit if that's an issue.
 * </p>
 *
 * @author SimIS Inc.
 */
public class PagesListService {

  private static Log LOG = LogFactory.getLog(PagesListService.class);

  // GET /pages?page={pageNumber}&size={pageSize}
  public ServiceResponse get(ServiceContext context) {

    int pageNumber = context.getParameterAsInt("page", 1);
    int pageSize = context.getParameterAsInt("size", 20);
    DataConstraints constraints = new DataConstraints(pageNumber, pageSize);

    WebPageSpecification specification = new WebPageSpecification();

    List<WebPage> webPageList = WebPageRepository.findAll(specification, constraints);

    List<WebPageResponse> recordList = new ArrayList<>();
    for (WebPage webPage : webPageList) {
      if (ValidateApiAccessToWebPageCommand.hasAccess(webPage, context.getUser())) {
        recordList.add(new WebPageResponse(webPage));
      }
    }

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "page", recordList, constraints);
    response.setData(recordList);
    return response;
  }
}
