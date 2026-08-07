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
import com.simisinc.platform.domain.model.AllowedIP;
import com.simisinc.platform.infrastructure.persistence.AllowedIPRepository;

import java.util.List;

/**
 * Validates and saves allowed IP objects
 *
 * @author elizabeth houser
 */
public class SaveAllowedIPCommand {

  private static Log LOG = LogFactory.getLog(SaveAllowedIPCommand.class);

  // Carries a non-blocking warning about the record just saved on this thread, e.g. when this
  // Allowed IP entry also happens to cover an address that is on the Blocked IP list (which it
  // now silently overrides, since Allowed always wins -- see BlockedIPListCommand.passesCheck).
  // Cleared at the start of every save() call so a stale warning can never leak into an unrelated
  // later request on a reused worker thread.
  private static final ThreadLocal<String> lastConflictWarning = new ThreadLocal<>();

  public static String getLastConflictWarning() {
    return lastConflictWarning.get();
  }

  public static AllowedIP save(AllowedIP allowedIPBean) throws DataException {
    lastConflictWarning.remove();

    // Trim before validation (not just before the eventual SQL write) so a pasted value with
    // stray leading/trailing whitespace isn't rejected even though the stored value would be fine
    String submittedIpAddress = allowedIPBean.getIpAddress();
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
    AllowedIP allowedIP;
    String previousIpAddress = null;
    boolean isUpdate = allowedIPBean.getId() > -1;
    if (isUpdate) {
      LOG.debug("Saving an existing record... ");
      allowedIP = AllowedIPRepository.findById(allowedIPBean.getId());
      if (allowedIP == null) {
        throw new DataException("The existing record could not be found");
      }
      previousIpAddress = allowedIP.getIpAddress();
    } else {
      LOG.debug("Saving a new record... ");
      allowedIP = new AllowedIP();
    }

    // Case-insensitive duplicate check against the live list, e.g. "2001:DB8::1" and "2001:db8::1"
    // are the same address and shouldn't both be addable. Skipped when this save is simply
    // re-saving the record's own current address (unchanged, aside from case) -- otherwise editing
    // a record without changing its IP address would always incorrectly flag it as a duplicate of
    // itself.
    if (previousIpAddress == null || !ipAddress.equalsIgnoreCase(previousIpAddress)) {
      boolean isDuplicate = LoadAllowedIPListCommand.retrieveCachedIpAddressList().stream()
          .anyMatch(existing -> existing.equalsIgnoreCase(ipAddress));
      if (isDuplicate) {
        throw new DataException("This IP address or range is already on the Allowed IP list: " + ipAddress);
      }
    }

    allowedIP.setIpAddress(ipAddress);
    allowedIP.setReason(allowedIPBean.getReason());
    if (allowedIPBean.getCreated() != null) {
      allowedIP.setCreated(allowedIPBean.getCreated());
    }
    allowedIP = AllowedIPRepository.save(allowedIP);
    if (allowedIP != null) {
      // If this update changed the address, drop the OLD value from the live cache first --
      // addIpToCache() below only ever appends, it never replaces, so without this the old,
      // broader value would keep silently bypassing IP blocking until a restart
      if (isUpdate && previousIpAddress != null && !previousIpAddress.equals(allowedIP.getIpAddress())) {
        LoadAllowedIPListCommand.removeIpFromCache(previousIpAddress);
      }
      LoadAllowedIPListCommand.addIpToCache(allowedIP);

      // An Allowed IP entry always wins over a Blocked IP entry (see
      // BlockedIPListCommand.passesCheck), so adding one here can silently un-block an address the
      // admin previously blocked. That's a legitimate, intentional precedence rule -- but it
      // should never happen without the admin being told about it.
      String shadowedBlockedEntry = findCoveringEntry(allowedIP.getIpAddress(),
          LoadBlockedIPListCommand.retrieveCachedIpAddressList());
      if (shadowedBlockedEntry != null) {
        String warning = "Saved. Note: this address is also covered by a Blocked IP entry (" + shadowedBlockedEntry
            + "), which will now be silently bypassed and NOT actually blocked until that entry is removed.";
        LOG.warn("Allowed IP " + allowedIP.getIpAddress() + " overrides Blocked IP entry " + shadowedBlockedEntry);
        lastConflictWarning.set(warning);
      }
    }
    return allowedIP;
  }

  /**
   * Finds an entry in otherListEntries that overlaps candidatePattern -- either one covers the
   * other's network address, or they're an exact match. Both candidatePattern and each entry in
   * otherListEntries may be a plain address or a CIDR range (e.g. "203.0.113.0/24"). Since two
   * CIDR ranges are always either nested (one fully contains the other) or disjoint, never
   * partially overlapping, checking each side's network address against the other pattern is
   * sufficient to detect any overlap between them.
   *
   * @param candidatePattern  a plain IP address or CIDR range just saved to one list
   * @param otherListEntries  the cached entries of the other (allow/block) list
   * @return the first overlapping entry found, or null if there is no overlap
   */
  static String findCoveringEntry(String candidatePattern, List<String> otherListEntries) {
    if (StringUtils.isBlank(candidatePattern) || otherListEntries == null) {
      return null;
    }
    String candidateNetwork = candidatePattern.contains("/") ? candidatePattern.split("/", 2)[0] : candidatePattern;
    for (String otherPattern : otherListEntries) {
      String otherNetwork = otherPattern.contains("/") ? otherPattern.split("/", 2)[0] : otherPattern;
      if (IpRangeCommand.matches(otherPattern, candidateNetwork) || IpRangeCommand.matches(candidatePattern, otherNetwork)) {
        return otherPattern;
      }
    }
    return null;
  }

}
