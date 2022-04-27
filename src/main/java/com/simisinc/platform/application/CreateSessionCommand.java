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

package com.simisinc.platform.application;

import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Creates a user's session object
 *
 * @author matt rajkowski
 * @created 7/2/18 12:59 PM
 */
public class CreateSessionCommand {

  private static Log LOG = LogFactory.getLog(CreateSessionCommand.class);

  public static UserSession createSession(String source, String sessionId, String ipAddress, String referer, String userAgent) {
    LOG.debug("Creating session...");
    UserSession userSession = new UserSession(source, sessionId, ipAddress);
    if (StringUtils.isNotBlank(referer)) {
      userSession.setReferer(referer);
    }
    userSession.setUserAgent(userAgent);
    userSession.setGeoIP(GeoIPCommand.getLocation(ipAddress));
    if (userSession.getGeoIP() != null && StringUtils.isNotBlank(userSession.getGeoIP().getTimezone())) {
      LOG.debug("Using Timezone: " + userSession.getGeoIP().getTimezone());
      // Override the system timezone for this user session
//            Config.set(httpServletRequest.getSession(), Config.FMT_TIME_ZONE, userSession.getGeoIP().getTimezone());
    }
    return userSession;
  }
}
