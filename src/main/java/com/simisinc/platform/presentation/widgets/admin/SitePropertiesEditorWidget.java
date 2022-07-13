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

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ColorCommand;
import com.simisinc.platform.domain.model.SiteProperty;
import com.simisinc.platform.infrastructure.persistence.SitePropertyRepository;
import com.simisinc.platform.presentation.controller.SqlTimestampConverter;
import com.simisinc.platform.presentation.widgets.GenericWidget;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.UrlValidator;

import javax.servlet.jsp.jstl.core.Config;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/18/18 4:20 PM
 */
public class SitePropertiesEditorWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/admin/site-properties-editor.jsp";

  static String PREFIX_PREFERENCE = "prefix";


  public WidgetContext execute(WidgetContext context) {

    // Check the request for POST errors
    List<SiteProperty> siteProperties = (List) context.getRequestObject();
    if (siteProperties == null) {
      // Load the properties
      String prefix = context.getPreferences().get(PREFIX_PREFERENCE);
      siteProperties = new ArrayList<>();
      String[] prefixList = prefix.split(",");
      for (String thisPrefix : prefixList) {
        List<SiteProperty> sitePropertiesList = SitePropertyRepository.findAllByPrefix(thisPrefix);
        if (sitePropertiesList != null) {
          siteProperties.addAll(sitePropertiesList);
        }
      }
    }
    context.getRequest().setAttribute("sitePropertyList", siteProperties);

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    // Show the editor
    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) {

    // Load the properties
    String prefix = context.getPreferences().get(PREFIX_PREFERENCE);
    List<SiteProperty> siteProperties = new ArrayList<>();
    String[] prefixList = prefix.split(",");
    for (String thisPrefix : prefixList) {
      List<SiteProperty> sitePropertiesList = SitePropertyRepository.findAllByPrefix(thisPrefix);
      if (sitePropertiesList != null) {
        siteProperties.addAll(sitePropertiesList);
      }
    }

    // Populate the entries from the request and Validate the values
    for (SiteProperty siteProperty : siteProperties) {

      // Determine the value
      String newValue = context.getParameter(siteProperty.getName());
      if (newValue == null) {
        newValue = "";
      } else {
        newValue = newValue.trim();
      }

      // Handle types
      if ("boolean".equals(siteProperty.getType())) {
        if (!"true".equals(newValue)) {
          newValue = "false";
        }
      } else if ("web-page".equals(siteProperty.getType())) {
        if (StringUtils.isNotBlank(newValue) && !newValue.startsWith("/")) {
          newValue = "/" + newValue;
        }
      }

      // Validate the values based on type
      siteProperty.setValue(newValue);
      if (StringUtils.isBlank(newValue)) {
        continue;
      }
      if ("url".equals(siteProperty.getType())) {
        if (newValue.startsWith("http://localhost") || newValue.startsWith("https://localhost")) {
          continue;
        }
        String[] schemes = {"http", "https"};
        UrlValidator urlValidator = new UrlValidator(schemes);
        if (!urlValidator.isValid(newValue)) {
          context.setErrorMessage(siteProperty.getLabel() + " has an invalid URL");
        }
      } else if ("color".equals(siteProperty.getType())) {
        if (!ColorCommand.isHexColor(newValue)) {
          context.setErrorMessage(siteProperty.getLabel() + " needs hex formatting value");
        }
      }
    }

    // If there's an error, pass the form values back
    if (context.getErrorMessage() != null) {
      context.setRequestObject(siteProperties);
      return context;
    }

    // Save the entries
    boolean saved = SitePropertyRepository.saveAll(prefix, siteProperties);

    if (saved) {
      // Update global cached settings
      String timezone = LoadSitePropertyCommand.loadByName("site.timezone");
      if (StringUtils.isNotBlank(timezone)) {
        // The format users see
        Config.set(context.getRequest().getServletContext(), Config.FMT_TIME_ZONE, timezone);
        // Replace the default converter
        SqlTimestampConverter converter = (SqlTimestampConverter) ConvertUtils.lookup(Timestamp.class);
        converter.setTimeZone(TimeZone.getTimeZone(ZoneId.of(timezone)));
        ConvertUtils.register(converter, Timestamp.class);
      }
      // Determine the page to return to
      context.setSuccessMessage("Values were saved");
    } else {
      context.setErrorMessage("Values could not be saved");
    }
    return context;
  }
}
