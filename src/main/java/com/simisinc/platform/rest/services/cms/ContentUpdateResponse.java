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

package com.simisinc.platform.rest.services.cms;

/**
 * The response body for {@code POST /api/content/{contentUniqueId}}. {@code published} reflects
 * what actually happened, not what the caller asked for -- governed publishing (#406-era
 * {@code content.review.required}) silently downgrades a requested publish to a draft save, and a
 * headless API caller has no UI to notice that the way an editor would, so {@code gated} makes it
 * explicit when that happened.
 *
 * @author SimIS Inc.
 */
public class ContentUpdateResponse {

  private String uniqueId;
  private boolean published;
  private boolean gated;

  public ContentUpdateResponse(String uniqueId, boolean published, boolean gated) {
    this.uniqueId = uniqueId;
    this.published = published;
    this.gated = gated;
  }

  public String getUniqueId() {
    return uniqueId;
  }

  public void setUniqueId(String uniqueId) {
    this.uniqueId = uniqueId;
  }

  public boolean isPublished() {
    return published;
  }

  public void setPublished(boolean published) {
    this.published = published;
  }

  public boolean isGated() {
    return gated;
  }

  public void setGated(boolean gated) {
    this.gated = gated;
  }
}
