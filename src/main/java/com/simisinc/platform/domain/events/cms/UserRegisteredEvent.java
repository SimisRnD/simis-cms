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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.application.maps.GeoIPCommand;
import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import lombok.NoArgsConstructor;

/**
 * Event details for when a user registered
 *
 * @author matt rajkowski
 * @created 4/29/21 5:32 PM
 */
@NoArgsConstructor
public class UserRegisteredEvent extends Event {

  public static final String ID = "user-registered";

  private User user = null;
  private String ipAddress = null;
  private String location = null;

  public UserRegisteredEvent(User user, String ipAddress) {
    this.user = user;
    this.ipAddress = ipAddress;
    this.location = GeoIPCommand.getCityStateCountryLocation(ipAddress);
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public User getUser() {
    return user;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public String getLocation() {
    return location;
  }
}
