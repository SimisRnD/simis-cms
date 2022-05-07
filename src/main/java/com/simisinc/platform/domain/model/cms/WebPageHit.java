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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A web page hit
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class WebPageHit extends Entity {

  private long id = -1;
  private long webPageId = -1;
  private String ipAddress = null;
  private String method = null;
  private String pagePath = null;
  private Timestamp hitDate = null;
  private String sessionId = null;
  private boolean isLoggedIn = false;

  public WebPageHit() {
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public long getWebPageId() {
    return webPageId;
  }

  public void setWebPageId(long webPageId) {
    this.webPageId = webPageId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getPagePath() {
    return pagePath;
  }

  public void setPagePath(String pagePath) {
    this.pagePath = pagePath;
  }

  public Timestamp getHitDate() {
    return hitDate;
  }

  public void setHitDate(Timestamp hitDate) {
    this.hitDate = hitDate;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public boolean isLoggedIn() {
    return isLoggedIn;
  }

  public void setLoggedIn(boolean loggedIn) {
    isLoggedIn = loggedIn;
  }
}
