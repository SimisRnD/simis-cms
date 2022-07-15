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

package com.simisinc.platform.application.elearning;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.UrlCommand;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * E-learning functions
 *
 * @author matt rajkowski
 * @created 6/14/2022 7:52 AM
 */
public class ElearningCommand {

  private static Log LOG = LogFactory.getLog(ElearningCommand.class);

  public static boolean isEnabled() {
    return ("true".equals(LoadSitePropertyCommand.loadByName("elearning.enabled", "false")));
  }

  public static boolean isLRSEnabled() {
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("elearning.xapi.enabled", "false")));
    if (!enabled) {
      return false;
    }
    String url = LoadSitePropertyCommand.loadByName("elearning.lrs.url");
    if (!UrlCommand.isUrlValid(url)) {
      return false;
    }
    String key = LoadSitePropertyCommand.loadByName("elearning.lrs.key");
    String secret = LoadSitePropertyCommand.loadByName("elearning.lrs.secret");
    return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(key) && StringUtils.isNotBlank(secret);
  }

  public static boolean isMoodleEnabled() {
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("elearning.moodle.enabled", "false")));
    if (!enabled) {
      return false;
    }
    String url = LoadSitePropertyCommand.loadByName("elearning.moodle.url");
    if (!UrlCommand.isUrlValid(url)) {
      return false;
    }
    String token = LoadSitePropertyCommand.loadByName("elearning.moodle.token");
    return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(token);
  }

  public static boolean isPERLSEnabled() {
    boolean enabled = ("true".equals(LoadSitePropertyCommand.loadByName("elearning.perls.enabled", "false")));
    if (!enabled) {
      LOG.debug("Not enabled");
      return false;
    }
    String url = LoadSitePropertyCommand.loadByName("elearning.perls.url");
    if (!UrlCommand.isUrlValid(url)) {
      LOG.debug("Invalid URL");
      return false;
    }
    String clientId = LoadSitePropertyCommand.loadByName("elearning.perls.clientId");
    String token = LoadSitePropertyCommand.loadByName("elearning.perls.secret");
    return StringUtils.isNotBlank(url) && StringUtils.isNotBlank(clientId) && StringUtils.isNotBlank(token);
  }
}
