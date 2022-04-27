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

package com.simisinc.platform.domain.model.login;

import com.simisinc.platform.domain.model.Entity;

import java.sql.Timestamp;

/**
 * A user's oauth token
 *
 * @author matt rajkowski
 * @created 4/22/22 6:57 AM
 */
public class OAuthToken extends Entity {

  private Long id = -1L;
  private long userId = -1L;
  private long userTokenId = -1L;
  private String provider = null;
  private String accessToken = null;
  private String tokenType = null;
  private int expiresIn = -1;
  private String refreshToken = null;
  private int refreshExpiresIn = -1;
  private String scope = null;
  private Timestamp expires = null;
  private Timestamp refreshExpires = null;
  private Timestamp created = null;
  private boolean enabled = true;
  private String resource = null;

  public OAuthToken() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public long getUserId() {
    return userId;
  }

  public void setUserId(long userId) {
    this.userId = userId;
  }

  public long getUserTokenId() {
    return userTokenId;
  }

  public void setUserTokenId(long userTokenId) {
    this.userTokenId = userTokenId;
  }

  public String getProvider() {
    return provider;
  }

  public void setProvider(String provider) {
    this.provider = provider;
  }

  public String getAccessToken() {
    return accessToken;
  }

  public void setAccessToken(String accessToken) {
    this.accessToken = accessToken;
  }

  public String getTokenType() {
    return tokenType;
  }

  public void setTokenType(String tokenType) {
    this.tokenType = tokenType;
  }

  public int getExpiresIn() {
    return expiresIn;
  }

  public void setExpiresIn(int expiresInAsSeconds) {
    this.expiresIn = expiresInAsSeconds;
    if (expires == null && expiresIn > 0) {
      expires = new Timestamp(System.currentTimeMillis() + (1000L * expiresIn));
    }
  }

  public String getRefreshToken() {
    return refreshToken;
  }

  public void setRefreshToken(String refreshToken) {
    this.refreshToken = refreshToken;
  }

  public int getRefreshExpiresIn() {
    return refreshExpiresIn;
  }

  public void setRefreshExpiresIn(int refreshExpiresInAsSeconds) {
    this.refreshExpiresIn = refreshExpiresInAsSeconds;
    if (refreshExpires == null && refreshExpiresIn > 0) {
      refreshExpires = new Timestamp(System.currentTimeMillis() + (1000L * refreshExpiresIn));
    }
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public Timestamp getExpires() {
    return expires;
  }

  public void setExpires(Timestamp expires) {
    this.expires = expires;
  }

  public Timestamp getRefreshExpires() {
    return refreshExpires;
  }

  public void setRefreshExpires(Timestamp refreshExpires) {
    this.refreshExpires = refreshExpires;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getResource() {
    return resource;
  }

  public void setResource(String resource) {
    this.resource = resource;
  }
}
