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

package com.simisinc.platform.presentation.widgets.admin;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.cms.CspPolicyCommand;
import com.simisinc.platform.domain.model.cms.CspViolation;
import com.simisinc.platform.infrastructure.persistence.cms.CspViolationRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Shows what a candidate Content-Security-Policy would have blocked, so the policy can be written
 * from evidence rather than guesswork.
 *
 * @author SimIS Inc.
 */
public class CspViolationsWidget extends GenericWidget {

  private static Log LOG = LogFactory.getLog(CspViolationsWidget.class);

  static String JSP = "/admin/csp-violations.jsp";

  public WidgetContext execute(WidgetContext context) {
    if (!context.hasRole("admin")) {
      return null;
    }
    List<CspViolation> violationList = CspViolationRepository.findAll();
    // <jsp:useBean> casts this attribute directly, so it must be an ArrayList and never null
    context.getRequest().setAttribute("violationList",
        violationList instanceof ArrayList ? violationList : new ArrayList<>(violationList));
    context.getRequest().setAttribute("reportingEnabled", CspPolicyCommand.isReportingEnabled());
    context.getRequest().setAttribute("maxDistinctViolations", CspViolationRepository.MAX_DISTINCT_VIOLATIONS);
    context.setJsp(JSP);
    return context;
  }

  /** Clears the collected reports, for once a policy has been updated from them. */
  public WidgetContext post(WidgetContext context) {
    if (!context.hasRole("admin")) {
      return null;
    }
    int deleted = CspViolationRepository.deleteAll();
    LOG.info("Cleared " + deleted + " CSP violation records");
    context.setSuccessMessage("The collected reports were cleared");
    context.setRedirect("/admin/csp-violations");
    return context;
  }
}
