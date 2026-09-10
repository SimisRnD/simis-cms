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

package com.simisinc.platform.presentation.controller;

import com.simisinc.platform.application.LoadUserCommand;
import com.simisinc.platform.domain.model.Capability;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.Role;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.ecommerce.Cart;
import com.simisinc.platform.domain.model.maps.GeoIP;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/18 8:32 AM
 */
public class UserSession implements Serializable {

  public static final long GUEST_ID = 0;

  public static final String API_SOURCE = "api";
  public static final String WEB_SOURCE = "web";
  public static final String OAUTH_SOURCE = "oauth";

  final static long serialVersionUID = 8345648404174283570L;
  protected static Log LOG = LogFactory.getLog(UserSession.class);

  private String sessionId = null;
  private String ipAddress = null;
  private GeoIP geoIP = null;
  private String source = null;
  private long appId = -1L;
  private String userAgent = null;
  private String referer = null;
  private String host = null;
  private long visitorId = -1;
  private long userId = GUEST_ID;
  private List<Role> roleList = null;
  private List<Capability> capabilityList = null;
  private List<Group> groupList = null;
  private long loginTime = -1;
  // The calendar day (site timezone) a user_logins row was last written for this session, so
  // WebRequestFilter can write one row per day for an authenticated session rather than only once
  // per HttpSession lifetime -- see WebRequestFilter.trackDailyLogin(). Session timeout refreshes
  // on activity, so a continuously-active session can otherwise live for days without a fresh
  // authentication event to trigger a new row, undercounting the Daily/Monthly Active Users tiles.
  private LocalDate lastLoginTrackedDate = null;
  private long stepUpExpiresAt = 0L;
  private String formToken = UUID.randomUUID().toString();
  private boolean cookieChecked = false;
  private Cart cart = null;
  private long lastOrderId = -1;
  private boolean showSiteConfirmation = true;
  private boolean showSiteNewsletterSignup = true;

  public UserSession() {
  }

  public UserSession(String source, String sessionId, String ipAddress) {
    this.source = source;
    this.sessionId = sessionId;
    this.ipAddress = ipAddress;
  }

  public void login(User user) {
    userId = user.getId();
    roleList = user.getRoleList();
    capabilityList = user.getCapabilityList();
    groupList = user.getGroupList();
    loginTime = System.currentTimeMillis();
  }

  public void setRoleList(List<Role> roleList) {
    this.roleList = roleList;
  }

  public void setCapabilityList(List<Capability> capabilityList) {
    this.capabilityList = capabilityList;
  }

  public void setGroupList(List<Group> groupList) {
    this.groupList = groupList;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public GeoIP getGeoIP() {
    return geoIP;
  }

  public void setGeoIP(GeoIP geoIP) {
    this.geoIP = geoIP;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public long getAppId() {
    return appId;
  }

  public void setAppId(long appId) {
    this.appId = appId;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getReferer() {
    return referer;
  }

  public void setReferer(String referer) {
    this.referer = referer;
  }

  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public long getVisitorId() {
    return visitorId;
  }

  public void setVisitorId(long visitorId) {
    this.visitorId = visitorId;
  }

  public long getUserId() {
    return userId;
  }

  public User getUser() {
    if (isLoggedIn()) {
      return LoadUserCommand.loadUser(userId);
    }
    return null;
  }

  public boolean hasRole(String code) {
    if (roleList == null) {
      return false;
    }
    if (StringUtils.isBlank(code)) {
      return false;
    }
    for (Role role : roleList) {
      if (role.getCode().equals(code)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Checks the capability set resolved from this session's roles at login (see
   * LoadUserCommand.populateUserRecord). Added alongside hasRole() (issue #701's walking
   * skeleton) - like roleList, capabilityList is cached for the session lifetime and does not
   * refresh until re-login.
   */
  public boolean hasPermission(String code) {
    if (capabilityList == null) {
      return false;
    }
    if (StringUtils.isBlank(code)) {
      return false;
    }
    for (Capability capability : capabilityList) {
      if (capability.getCode().equals(code)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasGroup(String uniqueId) {
    if (groupList == null) {
      return false;
    }
    if (StringUtils.isBlank(uniqueId)) {
      return false;
    }
    for (Group group : groupList) {
      if (group.getUniqueId().equals(uniqueId)) {
        return true;
      }
    }
    return false;
  }

  public long getLoginTime() {
    return loginTime;
  }

  public LocalDate getLastLoginTrackedDate() {
    return lastLoginTrackedDate;
  }

  public void setLastLoginTrackedDate(LocalDate lastLoginTrackedDate) {
    this.lastLoginTrackedDate = lastLoginTrackedDate;
  }

  public long getStepUpExpiresAt() {
    return stepUpExpiresAt;
  }

  public void setStepUpExpiresAt(long stepUpExpiresAt) {
    this.stepUpExpiresAt = stepUpExpiresAt;
  }

  public String getFormToken() {
    return formToken;
  }

  public boolean isLoggedIn() {
    return userId > GUEST_ID;
  }

  public void renewFormToken() {
    formToken = UUID.randomUUID().toString();
  }

  public boolean isCookieChecked() {
    return cookieChecked;
  }

  public void setCookieChecked(boolean cookieChecked) {
    this.cookieChecked = cookieChecked;
  }

  public Cart getCart() {
    return cart;
  }

  public void setCart(Cart cart) {
    this.cart = cart;
  }

  public long getLastOrderId() {
    return lastOrderId;
  }

  public void setLastOrderId(long lastOrderId) {
    this.lastOrderId = lastOrderId;
  }

  public boolean getShowSiteConfirmation() {
    return showSiteConfirmation;
  }

  public void setShowSiteConfirmation(boolean showSiteConfirmation) {
    this.showSiteConfirmation = showSiteConfirmation;
  }

  public boolean getShowSiteNewsletterSignup() {
    return showSiteNewsletterSignup;
  }

  public void setShowSiteNewsletterSignup(boolean showSiteNewsletterSignup) {
    this.showSiteNewsletterSignup = showSiteNewsletterSignup;
  }
}
