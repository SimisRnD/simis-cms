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

package com.simisinc.platform.application.cms;

import java.io.InputStream;

import jakarta.servlet.ServletContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Loads {@code widget-schema.json} (labeled field metadata for the widget palette -- name, type,
 * default, select options -- keyed by widget-library name) once per JVM. Extracted from {@link
 * com.simisinc.platform.presentation.widgets.cms.WebPageDesignerWidget}, which was its sole
 * consumer until issue #1269 wired the same schema into the live-page composition canvas via
 * {@code PageServlet} -- a neutral {@code application.cms} home avoids the servlet reaching into a
 * widget class's internals.
 */
public class LoadWidgetSchemaCommand {

  private static Log LOG = LogFactory.getLog(LoadWidgetSchemaCommand.class);

  static final String WIDGET_SCHEMA_RESOURCE = "/WEB-INF/widgets/widget-schema.json";

  // Loaded once and cached; the file is static content shipped with the app, not per-request data.
  private static String widgetSchemaJson = null;

  private LoadWidgetSchemaCommand() {
  }

  public static synchronized String getWidgetSchemaJson(ServletContext servletContext) {
    if (widgetSchemaJson == null) {
      try (InputStream is = servletContext.getResourceAsStream(WIDGET_SCHEMA_RESOURCE)) {
        widgetSchemaJson = IOUtils.toString(is, "UTF-8");
      } catch (Exception e) {
        LOG.error("Could not load " + WIDGET_SCHEMA_RESOURCE, e);
        widgetSchemaJson = "{}";
      }
    }
    return widgetSchemaJson;
  }
}
