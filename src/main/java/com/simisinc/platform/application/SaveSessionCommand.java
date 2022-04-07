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

import com.simisinc.platform.domain.model.Session;
import com.simisinc.platform.infrastructure.persistence.SessionRepository;
import com.simisinc.platform.presentation.controller.login.UserSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/2/18 12:59 PM
 */
public class SaveSessionCommand {

  private static Log LOG = LogFactory.getLog(SaveSessionCommand.class);

  public static void saveSession(UserSession userSession) {
    Session session = new Session();
    session.setSessionId(userSession.getSessionId());
    session.setSource(userSession.getSource());
    session.setAppId(userSession.getAppId());
    session.setIpAddress(userSession.getIpAddress());
    session.setUserAgent(userSession.getUserAgent());
    session.setReferer(userSession.getReferer());
    if (userSession.getGeoIP() != null) {
      session.setContinent(userSession.getGeoIP().getContinent());
      session.setCountryIso(userSession.getGeoIP().getCountryISOCode());
      session.setCountry(userSession.getGeoIP().getCountry());
      session.setCity(userSession.getGeoIP().getCity());
      session.setStateIso(userSession.getGeoIP().getStateISOCode());
      session.setState(userSession.getGeoIP().getState());
      session.setPostalCode(userSession.getGeoIP().getPostalCode());
      session.setTimezone(userSession.getGeoIP().getTimezone());
      session.setLatitude(userSession.getGeoIP().getLatitude());
      session.setLongitude(userSession.getGeoIP().getLongitude());
      session.setMetroCode(userSession.getGeoIP().getMetroCode());
    }
    session.setIsBot(SessionCommand.checkForBot(userSession.getUserAgent()));
    SessionRepository.add(session);
  }
}
