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

import java.sql.Timestamp;

/**
 * A web page referer
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class Referer {

  Long id = null;
  String urlFrom = null;
  String urlTo = null;
  String ipAddress = null;
  String userAgent = null;
  Timestamp refererDate = null;
  boolean isLoggedIn = false;

  public Referer() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUrlFrom() {
    return urlFrom;
  }

  public void setUrlFrom(String urlFrom) {
    this.urlFrom = urlFrom;
  }

  public String getUrlTo() {
    return urlTo;
  }

  public void setUrlTo(String urlTo) {
    this.urlTo = urlTo;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Timestamp getRefererDate() {
    return refererDate;
  }

  public void setRefererDate(Timestamp refererDate) {
    this.refererDate = refererDate;
  }

  public boolean isLoggedIn() {
    return isLoggedIn;
  }

  public void setLoggedIn(boolean loggedIn) {
    isLoggedIn = loggedIn;
  }
}
