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

package com.simisinc.platform.application.cms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.domain.model.BlockedIP;
import com.simisinc.platform.infrastructure.persistence.BlockedIPRepository;

/**
 * Validates and saves blocked IP objects
 *
 * @author matt rajkowski
 * @created 3/25/20 10:10 AM
 */
public class SaveBlockedIPCommand {

  private static Log LOG = LogFactory.getLog(SaveBlockedIPCommand.class);

  // Carries a non-blocking warning about the record just saved on this thread, e.g. when this
  // Blocked IP entry is already covered by an existing Allowed IP entry (which always wins -- see
  // BlockedIPListCommand.passesCheck), so the new block will never actually fire. Cleared at the
  // start of every save() call so a stale warning can never leak into an unrelated later request
  // on a reused worker thread.
  private static final ThreadLocal<String> lastConflictWarning = new ThreadLocal<>();

  public static String getLastConflictWarning() {
    return lastConflictWarning.get();
  }

  public static BlockedIP save(BlockedIP blockedIPBean) throws DataException {
    lastConflictWarning.remove();

    // Trim before validation (not just before the eventual SQL write) so a pasted value with
    // stray leading/trailing whitespace isn't rejected even though the stored value would be fine
    String submittedIpAddress = blockedIPBean.getIpAddress();
    String ipAddress = submittedIpAddress != null ? submittedIpAddress.trim() : submittedIpAddress;

    // Validate the required fields
    StringBuilder errorMessages = new StringBuilder();
    if (StringUtils.isBlank(ipAddress)) {
      errorMessages.append("An IP address is required");
    } else if (!IpRangeCommand.isValidAddressOrCidr(ipAddress)) {
      errorMessages.append("A valid IPv4 or IPv6 address or CIDR range (e.g. 203.0.113.0/24) is required");
    }
    if (errorMessages.length() > 0) {
      throw new DataException("Please check the form and try again:\n" + errorMessages.toString());
    }

    // Transform the fields and store...
    BlockedIP blockedIP;
    String previousIpAddress = null;
    boolean isUpdate = blockedIPBean.getId() > -1;
    if (isUpdate) {
      LOG.debug("Saving an existing record... ");
      blockedIP = BlockedIPRepository.findById(blockedIPBean.getId());
      if (blockedIP == null) {
        throw new DataException("The existing record could not be found");
      }
      previousIpAddress = blockedIP.getIpAddress();
    } else {
      LOG.debug("Saving a new record... ");
      blockedIP = new BlockedIP();
    }

    // Case-insensitive duplicate check against the live list, e.g. "2001:DB8::1" and "2001:db8::1"
    // are the same address and shouldn't both be addable. Skipped when this save is simply
    // re-saving the record's own current address (unchanged, aside from case) -- otherwise editing
    // a record without changing its IP address would always incorrectly flag it as a duplicate of
    // itself.
    if (previousIpAddress == null || !ipAddress.equalsIgnoreCase(previousIpAddress)) {
      boolean isDuplicate = LoadBlockedIPListCommand.retrieveCachedIpAddressList().stream()
          .anyMatch(existing -> existing.equalsIgnoreCase(ipAddress));
      if (isDuplicate) {
        throw new DataException("This IP address or range is already on the Blocked IP list: " + ipAddress);
      }
    }

    blockedIP.setIpAddress(ipAddress);
    blockedIP.setReason(blockedIPBean.getReason());
    if (blockedIPBean.getCreated() != null) {
      blockedIP.setCreated(blockedIPBean.getCreated());
    }
    blockedIP = BlockedIPRepository.save(blockedIP);
    if (blockedIP != null) {
      // If this update changed the address, drop the OLD value from the live cache first --
      // addIpToCache() below only ever appends, it never replaces, so without this the old value
      // would keep being enforced under the (no longer existing) old record until a restart
      if (isUpdate && previousIpAddress != null && !previousIpAddress.equals(blockedIP.getIpAddress())) {
        LoadBlockedIPListCommand.removeIpFromCache(previousIpAddress);
      }
      LoadBlockedIPListCommand.addIpToCache(blockedIP);

      // An Allowed IP entry always wins over a Blocked IP entry (see
      // BlockedIPListCommand.passesCheck), so this new block may never actually fire if it's
      // already covered by an existing Allowed IP entry. That's a legitimate, intentional
      // precedence rule -- but it should never happen without the admin being told about it.
      String shadowingAllowedEntry = SaveAllowedIPCommand.findCoveringEntry(blockedIP.getIpAddress(),
          LoadAllowedIPListCommand.retrieveCachedIpAddressList());
      if (shadowingAllowedEntry != null) {
        String warning = "Saved. Note: this address is also covered by an Allowed IP entry (" + shadowingAllowedEntry
            + "), so it will NOT actually be blocked until that entry is removed.";
        LOG.warn("Blocked IP " + blockedIP.getIpAddress() + " is shadowed by Allowed IP entry " + shadowingAllowedEntry);
        lastConflictWarning.set(warning);
      }
    }
    return blockedIP;
  }

  public static void immediateBlock(String ipAddress, String reason) {
    LoadBlockedIPListCommand.addIpToCache(ipAddress);
    BlockedIP blockedIP = new BlockedIP();
    blockedIP.setIpAddress(ipAddress);
    blockedIP.setReason(reason);
    BlockedIPRepository.save(blockedIP);
  }

}
