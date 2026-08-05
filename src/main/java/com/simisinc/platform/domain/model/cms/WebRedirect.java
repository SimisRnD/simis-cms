/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import com.simisinc.platform.domain.model.Entity;

/**
 * An admin-managed URL redirect rule (issue #408), replacing the legacy
 * CMS_PATH/config/cms/redirects.csv file. {@code fromPath} is the incoming request path
 * WebRequestFilter matches against (via a Caffeine-cached lookup -- see
 * {@code LoadWebRedirectCommand}); {@code toUrl} is either a relative path or an absolute URL to
 * redirect to; {@code statusCode} is either 301 (permanent, the default) or 302 (temporary).
 *
 * @author SimIS Inc.
 */
public class WebRedirect extends Entity {

  public static final int PERMANENT = 301;
  public static final int TEMPORARY = 302;

  /**
   * A cache-only sentinel meaning "no {@code web_redirects} row exists for this from_path at all"
   * (as opposed to {@code null}, which a Caffeine {@code LoadingCache} loader can return but will
   * never actually cache -- see {@code CacheManager.WEB_REDIRECT_CACHE}). Never persisted, never
   * returned by a repository method; {@code LoadWebRedirectCommand} translates it back to
   * {@code null} before anything outside the cache sees it.
   */
  public static final WebRedirect NONE = new WebRedirect();

  private Long id = -1L;
  private String fromPath = null;
  private String toUrl = null;
  private int statusCode = PERMANENT;
  private boolean enabled = true;
  private long createdBy = -1;
  private long modifiedBy = -1;
  private Timestamp created = null;
  private Timestamp modified = null;

  public WebRedirect() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getFromPath() {
    return fromPath;
  }

  public void setFromPath(String fromPath) {
    this.fromPath = fromPath;
  }

  public String getToUrl() {
    return toUrl;
  }

  public void setToUrl(String toUrl) {
    this.toUrl = toUrl;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  public boolean getEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(long createdBy) {
    this.createdBy = createdBy;
  }

  public long getModifiedBy() {
    return modifiedBy;
  }

  public void setModifiedBy(long modifiedBy) {
    this.modifiedBy = modifiedBy;
  }

  public Timestamp getCreated() {
    return created;
  }

  public void setCreated(Timestamp created) {
    this.created = created;
  }

  public Timestamp getModified() {
    return modified;
  }

  public void setModified(Timestamp modified) {
    this.modified = modified;
  }
}
