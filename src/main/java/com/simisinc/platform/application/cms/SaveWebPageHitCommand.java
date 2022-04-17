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

package com.simisinc.platform.application.cms;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.presentation.controller.cms.WebPageHit;
import com.simisinc.platform.presentation.controller.login.UserSession;

import java.sql.Timestamp;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Saves web page hit objects
 *
 * @author matt rajkowski
 * @created 5/21/18 1:30 PM
 */
public class SaveWebPageHitCommand {

  // Use a queue, so a single job can store the hits
  public static ConcurrentLinkedQueue<WebPageHit> queue = new ConcurrentLinkedQueue<>();

  public static void saveHit(String ipAddress, String method, String pagePath, WebPage webPage, UserSession userSession) {
    WebPageHit webPageHit = new WebPageHit();
    webPageHit.setIpAddress(ipAddress);
    webPageHit.setMethod(method);
    webPageHit.setPagePath(pagePath);
    if (webPage != null) {
      webPageHit.setWebPageId(webPage.getId());
    }
    if (userSession != null) {
      webPageHit.setSessionId(userSession.getSessionId());
      if (userSession.isLoggedIn()) {
        webPageHit.setLoggedIn(true);
      }
    }
    webPageHit.setHitDate(new Timestamp(System.currentTimeMillis()));
    queue.offer(webPageHit);
  }

  public static void saveHit(String ipAddress, String method, String pagePath, User user) {
    WebPageHit webPageHit = new WebPageHit();
    webPageHit.setIpAddress(ipAddress);
    webPageHit.setMethod(method);
    webPageHit.setPagePath(pagePath);
    if (user != null) {
      webPageHit.setLoggedIn(true);
    }
    webPageHit.setHitDate(new Timestamp(System.currentTimeMillis()));
    queue.offer(webPageHit);
  }

  public static WebPageHit getHitFromQueue() {
    return queue.poll();
  }
}
