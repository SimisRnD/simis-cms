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

import com.simisinc.platform.domain.model.Entity;

/**
 * One aggregated Content-Security-Policy violation: a directive, the host it refused, and how often.
 *
 * <p>
 * This is a running total rather than an event. The question it answers is "which hosts would a
 * stricter policy block?", which needs one row per (directive, host) with a count, not one row per
 * page view -- and since /csp-report cannot require authentication, aggregating is also what keeps
 * an unauthenticated endpoint from being able to grow a table.
 * </p>
 *
 * <p>
 * Only the host of the blocked URL is held, never its path or query string. A violation report is
 * one of the few places a third party's URL parameters could reach this database, and a source list
 * needs the host anyway.
 * </p>
 *
 * @author SimIS Inc.
 */
public class CspViolation extends Entity {

  private Long id = -1L;
  private String effectiveDirective = null;
  private String blockedHost = null;
  private long occurrences = 0;
  private String sampleDocumentPath = null;
  private Timestamp firstSeen = null;
  private Timestamp lastSeen = null;

  public CspViolation() {
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getEffectiveDirective() {
    return effectiveDirective;
  }

  public void setEffectiveDirective(String effectiveDirective) {
    this.effectiveDirective = effectiveDirective;
  }

  public String getBlockedHost() {
    return blockedHost;
  }

  public void setBlockedHost(String blockedHost) {
    this.blockedHost = blockedHost;
  }

  public long getOccurrences() {
    return occurrences;
  }

  public void setOccurrences(long occurrences) {
    this.occurrences = occurrences;
  }

  public String getSampleDocumentPath() {
    return sampleDocumentPath;
  }

  public void setSampleDocumentPath(String sampleDocumentPath) {
    this.sampleDocumentPath = sampleDocumentPath;
  }

  public Timestamp getFirstSeen() {
    return firstSeen;
  }

  public void setFirstSeen(Timestamp firstSeen) {
    this.firstSeen = firstSeen;
  }

  public Timestamp getLastSeen() {
    return lastSeen;
  }

  public void setLastSeen(Timestamp lastSeen) {
    this.lastSeen = lastSeen;
  }

  /**
   * The CSP source-list entry this violation implies, ready to paste into a policy.
   *
   * @return the host as an https source, or the keyword itself for a non-host violation
   */
  public String getSourceListEntry() {
    if (blockedHost == null) {
      return null;
    }
    if (blockedHost.startsWith("'")) {
      return blockedHost;
    }
    return "https://" + blockedHost;
  }
}
