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

package com.simisinc.platform.infrastructure.scheduler.admin;

import java.time.Duration;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.text.StringEscapeUtils;
import org.jobrunr.jobs.annotations.Job;

import com.simisinc.platform.application.admin.SendAdminEmailCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.domain.model.CapabilityGrant;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.persistence.CapabilityGrantRepository;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.scheduler.SchedulerManager;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * Enforces expiration of direct capability grants (issue #702) via a scheduled sweep rather than
 * a live per-check evaluation - the same staleness the rest of the permission model already
 * accepts (a role or grant change only takes effect at a user's next login). Two independent
 * passes: revoke anything already past its expiresAt, and send one batched admin-email digest
 * for grants expiring soon so an admin can renew or deliberately let them lapse.
 *
 * @author elizabeth houser
 */
public class CapabilityGrantExpirationJob {

  private static Log LOG = LogFactory.getLog(CapabilityGrantExpirationJob.class);

  private static final int EXPIRING_SOON_DAYS = 7;

  @Job(name = "Expire and warn on capability grants")
  public static void execute() {
    // Distributed lock so only one node runs the sweep
    String lock = LockManager.lock(SchedulerManager.CAPABILITY_GRANT_EXPIRATION_JOB, Duration.ofHours(1));
    if (lock == null) {
      return;
    }

    revokeExpiredGrants();
    notifyExpiringGrants();
  }

  private static void revokeExpiredGrants() {
    List<CapabilityGrant> expiredGrantList = CapabilityGrantRepository.findExpired();
    if (expiredGrantList == null || expiredGrantList.isEmpty()) {
      return;
    }
    int revokedCount = 0;
    for (CapabilityGrant capabilityGrant : expiredGrantList) {
      if (!CapabilityGrantRepository.revoke(capabilityGrant.getId())) {
        continue;
      }
      ++revokedCount;
      User user = UserRepository.findByUserId(capabilityGrant.getUserId());
      SaveAuditEventCommand.recordAdminEvent(AuditEventCommand.AUTHORIZATION, "capability_grant.expire",
          AuditEventCommand.SUCCESS, -1L, "system", null, null, "capability_grant",
          capabilityGrant.getCapabilityCode(), user != null ? user.getUsername() : null,
          "Grant expired at " + capabilityGrant.getExpiresAt());
    }
    if (revokedCount > 0) {
      LOG.info("Capability grant expiration: revoked " + revokedCount + " expired grant(s)");
    }
  }

  private static void notifyExpiringGrants() {
    List<CapabilityGrant> expiringGrantList =
        CapabilityGrantRepository.findExpiringWithinDaysNotYetNotified(EXPIRING_SOON_DAYS);
    if (expiringGrantList == null || expiringGrantList.isEmpty()) {
      return;
    }

    StringBuilder html = new StringBuilder(
        "<p>The following capability grants expire within " + EXPIRING_SOON_DAYS + " days:</p><ul>");
    StringBuilder text = new StringBuilder(
        "The following capability grants expire within " + EXPIRING_SOON_DAYS + " days:\n\n");
    for (CapabilityGrant capabilityGrant : expiringGrantList) {
      User user = UserRepository.findByUserId(capabilityGrant.getUserId());
      String username = user != null ? user.getUsername() : ("user #" + capabilityGrant.getUserId());
      String line = username + " - " + capabilityGrant.getCapabilityCode() +
          " (expires " + capabilityGrant.getExpiresAt() + ")";
      html.append("<li>").append(StringEscapeUtils.escapeHtml4(line)).append("</li>");
      text.append(line).append("\n");
    }
    html.append("</ul>");

    // One digest for the whole batch, not one email per grant
    SendAdminEmailCommand.sendMessage("Capability grants expiring soon", html.toString(), text.toString());

    for (CapabilityGrant capabilityGrant : expiringGrantList) {
      CapabilityGrantRepository.markExpirationNotified(capabilityGrant.getId());
    }
  }
}
