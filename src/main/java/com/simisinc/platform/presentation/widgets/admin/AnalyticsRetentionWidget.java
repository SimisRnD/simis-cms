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

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Admin widget that shows how much visitor PII is currently retained in the sessions table
 * and lets an administrator trigger an immediate scrub without waiting for the nightly job.
 *
 * @author SimIS Inc.
 */
public class AnalyticsRetentionWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;
  private static Log LOG = LogFactory.getLog(AnalyticsRetentionWidget.class);

  static String JSP = "/admin/analytics-retention.jsp";

  public WidgetContext execute(WidgetContext context) {

    if (!context.hasRole("admin")) {
      LOG.warn("No access to analytics retention dashboard");
      return null;
    }

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    loadStats(context);

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    if (!context.hasRole("admin")) {
      LOG.warn("No access to trigger analytics retention purge");
      return null;
    }

    int days = SessionRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("analytics.retentionDays"));
    int scrubbed = SessionRepository.scrubOldPii(days);

    if (scrubbed > 0) {
      context.setSuccessMessage("Purged PII from " + scrubbed + " session record(s) older than " + days + " days");
    } else {
      context.setSuccessMessage("No session records required scrubbing — all PII is within the " + days + "-day retention window");
    }

    loadStats(context);
    context.setJsp(JSP);
    return context;
  }

  private void loadStats(WidgetContext context) {
    int retentionDays = SessionRepository.resolveRetentionDays(LoadSitePropertyCommand.loadByName("analytics.retentionDays"));
    long withPii = SessionRepository.countSessionsWithPii();
    context.getRequest().setAttribute("retentionDays", retentionDays);
    context.getRequest().setAttribute("sessionsWithPii", withPii);
  }
}
