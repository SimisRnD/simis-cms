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

package com.simisinc.platform.application.email;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates an email context with site information properties to be used by the email template
 *
 * @author matt rajkowski
 * @created 4/26/2021 9:23 PM
 */
public class EmailTemplateCommand {

  private static Log LOG = LogFactory.getLog(EmailTemplateCommand.class);

  public static Context createSiteContext() {
    Context ctx = new Context();

    // Site information
    String siteUrl = LoadSitePropertyCommand.loadByName("site.url");
    String siteLogo = LoadSitePropertyCommand.loadByName("site.logo");

    Map<String, String> siteObject = new HashMap<>();
    addValue(siteObject, "name", LoadSitePropertyCommand.loadByName("site.name"));
    addValue(siteObject, "keyword", LoadSitePropertyCommand.loadByName("site.name.keyword"));
    addValue(siteObject, "url", siteUrl);
    if (StringUtils.isNotBlank(siteUrl) && StringUtils.isNotBlank(siteLogo)) {
      siteObject.put("logo", siteUrl + siteLogo);
    }
    siteObject.put("accountPageUrl", siteUrl + "/my-page");
    siteObject.put("contactUsUrl", siteUrl + "/contact-us");

    // Site location information
    addValue(siteObject, "addressLine1", LoadSitePropertyCommand.loadByName("ecommerce.from.address1"));
    addValue(siteObject, "addressLine2", LoadSitePropertyCommand.loadByName("ecommerce.from.address2"));
    addValue(siteObject, "city", LoadSitePropertyCommand.loadByName("ecommerce.from.city"));
    addValue(siteObject, "state", LoadSitePropertyCommand.loadByName("ecommerce.from.stateCode"));
    addValue(siteObject, "postalCode", LoadSitePropertyCommand.loadByName("ecommerce.from.postalCode"));
    addValue(siteObject, "country", LoadSitePropertyCommand.loadByName("ecommerce.from.countryCode"));

    ctx.setVariable("site", siteObject);
    return ctx;
  }

  private static void addValue(Map<String, String> map, String name, String value) {
    if (StringUtils.isBlank(value)) {
      return;
    }
    map.put(name, value);
  }
}
