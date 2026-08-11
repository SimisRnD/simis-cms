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

package com.simisinc.platform.domain.model.dashboard;

import com.simisinc.platform.domain.model.Entity;

/**
 * Per-bot-identity summary for the Bot Traffic by Identity report
 *
 * @author elizabeth houser
 */
public class BotIdentityStats extends Entity {

  private String identity = null;
  private long sessionCount = 0;
  private String firstSeen = null;
  private String lastSeen = null;
  private String topPage = null;
  private long topPageHits = 0;

  public BotIdentityStats() {
  }

  public String getIdentity() {
    return identity;
  }

  public void setIdentity(String identity) {
    this.identity = identity;
  }

  public long getSessionCount() {
    return sessionCount;
  }

  public void setSessionCount(long sessionCount) {
    this.sessionCount = sessionCount;
  }

  public String getFirstSeen() {
    return firstSeen;
  }

  public void setFirstSeen(String firstSeen) {
    this.firstSeen = firstSeen;
  }

  public String getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(String lastSeen) {
    this.lastSeen = lastSeen;
  }

  public String getTopPage() {
    return topPage;
  }

  public void setTopPage(String topPage) {
    this.topPage = topPage;
  }

  public long getTopPageHits() {
    return topPageHits;
  }

  public void setTopPageHits(long topPageHits) {
    this.topPageHits = topPageHits;
  }
}
