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

package com.simisinc.platform.domain.model.maps;

import com.simisinc.platform.domain.model.Entity;

/**
 * Assigned credentials used to access mapping services
 *
 * @author matt rajkowski
 * @created 4/26/18 8:20 AM
 */
public class MapCredentials extends Entity {

  private String service = null;
  private String accessToken = null;

  public MapCredentials() {
  }

  public MapCredentials(String service, String accessToken) {
    this.service = service;
    this.accessToken = accessToken;
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }
}
