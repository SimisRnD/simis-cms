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

package com.simisinc.platform.application.audit;

import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.audit.AuditLog;
import com.simisinc.platform.infrastructure.persistence.audit.AuditLogRepository;

/**
 * Tamper-evidence for the audit trail (NIST 800-53 AU-9). Every audit record carries a SHA-256 hash that
 * is chained to the record before it: {@code record_hash = SHA-256(previous_hash || canonical(record))},
 * where {@code previous_hash} is the {@code record_hash} of the row inserted immediately before (a genesis
 * constant for the very first row). Because each hash depends on the entire prior history, editing a record,
 * deleting a record from the middle or tail, reordering records, or inserting a record changes every hash
 * after it and is detectable by {@link #verify()}.
 *
 * <p><b>What this does not, by itself, detect: deletion of the oldest contiguous records.</b> {@code verify()}
 * anchors on the oldest record still present, so removing the genuine genesis (and any oldest-first run)
 * leaves a shorter but self-consistent chain -- this is the same shape as a legitimate retention purge and
 * cannot be distinguished from it by the database alone. Two things cover that gap: retention only ever
 * removes an oldest-first prefix and records an audited {@code audit.retention.purge} event of its own, and
 * the hashes are also emitted to the out-of-band SIEM witness below, which retains the earlier records. (A
 * verify-side high-water record count, so any oldest-record deletion NOT made by the retention job is caught
 * on-box too, is a planned follow-up.)
 *
 * <p>The chain is unkeyed, so on its own it proves only that the database has not been edited <i>in place</i>
 * -- an attacker who can rewrite the whole table could recompute a consistent chain. That is why the hashes
 * are also emitted to the structured JSON sink (see {@link SaveAuditEventCommand}): a SIEM (Log Analytics /
 * Microsoft Sentinel) then holds an independent copy of the chain, so a rewritten database no longer matches
 * the out-of-band record. Keying the hash with a Key Vault secret (tamper-<i>resistance</i>) is a planned
 * hardening once a key custody service is available.
 *
 * <p>The canonical encoding is length-prefixed so that no combination of field values can be confused with
 * another (e.g. {@code ("ab","c")} and {@code ("a","bc")} encode differently). It must be computed over the
 * values exactly as they are stored, so callers normalize the record (millisecond-precision timestamp,
 * column-length truncation) before hashing and inserting -- see AuditLogRepository.
 *
 * @author SimIS Inc.
 */
public class AuditLogIntegrityCommand {

  /** The previous_hash of the first record in the chain (there is no earlier record to chain to). */
  public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

  private static Log LOG = LogFactory.getLog(AuditLogIntegrityCommand.class);

  private AuditLogIntegrityCommand() {
    // Static utility
  }

  /**
   * Computes a record's chained hash from the record's stored fields and the hash of the prior record.
   * Pure and deterministic; the record must already be normalized to its stored form.
   */
  public static String computeRecordHash(AuditLog record, String previousHash) {
    String prior = (previousHash != null ? previousHash : GENESIS_HASH);
    // previousHash is a fixed 64-character hex string, so its boundary with the canonical payload is
    // unambiguous without a separator.
    return DigestUtils.sha256Hex(prior + canonical(record));
  }

  /**
   * A length-prefixed, injective serialization of the immutable business fields. The surrogate key
   * (audit_id) is deliberately excluded because it is assigned by the database during the insert, after the
   * hash is computed; ordering is instead enforced by the previous_hash links. occurred is encoded as its
   * local-date-time WALL CLOCK, not as an absolute epoch: the column is TIMESTAMP WITHOUT TIME ZONE, so the
   * value that is actually stored and read back is the wall clock, and it round-trips identically regardless
   * of the JVM/session time zone in effect at verification time (an absolute epoch would not -- verifying a
   * restored copy under a different time zone would re-hash to a different value and falsely report tamper).
   */
  static String canonical(AuditLog r) {
    StringBuilder sb = new StringBuilder();
    appendField(sb, r.getEventCategory());
    appendField(sb, r.getEventType());
    appendField(sb, r.getOutcome());
    appendField(sb, Long.toString(r.getActorUserId()));
    appendField(sb, r.getActorUsername());
    appendField(sb, r.getSourceIp());
    appendField(sb, r.getTargetType());
    appendField(sb, r.getTargetId());
    appendField(sb, r.getTargetLabel());
    appendField(sb, r.getDetails());
    appendField(sb, r.getSessionId());
    appendField(sb, Integer.toString(r.getSchemaVersion()));
    appendField(sb, r.getOccurred() != null ? r.getOccurred().toLocalDateTime().toString() : null);
    return sb.toString();
  }

  /** Encodes one field as {@code length:value}; a null value is a distinct {@code -1:} (never {@code 0:}). */
  private static void appendField(StringBuilder sb, String value) {
    if (value == null) {
      sb.append("-1:");
    } else {
      sb.append(value.length()).append(':').append(value);
    }
  }

  /**
   * Walks the entire audit_log chain in ascending order and reports the first record that fails
   * verification, or that the chain is intact. Reads in bounded pages so a large trail does not have to be
   * held in memory. Records written before Phase 4 (no hash) are treated as the pre-chain prefix and skipped
   * until the first hashed record, which becomes the chain's anchor.
   */
  public static AuditIntegrityResult verify() {
    ChainVerifier verifier = new ChainVerifier();
    long afterAuditId = 0L;
    while (true) {
      List<AuditLog> page = AuditLogRepository.findChainPage(afterAuditId, PAGE_SIZE);
      if (page.isEmpty()) {
        break;
      }
      for (AuditLog row : page) {
        afterAuditId = row.getId();
        AuditIntegrityResult broken = verifier.accept(row);
        if (broken != null) {
          LOG.warn("Audit chain integrity FAILED at audit_id=" + broken.getFirstInvalidAuditId()
              + " after " + broken.getCheckedCount() + " valid records: " + broken.getReason());
          return broken;
        }
      }
      if (page.size() < PAGE_SIZE) {
        break;
      }
    }
    return verifier.result();
  }

  private static final int PAGE_SIZE = 1000;

  /**
   * The pure chain-checking algorithm, factored out so it can be unit tested without a database. Feed it the
   * records in ascending audit_id order; it returns intact or the first failure.
   */
  static AuditIntegrityResult verifyChain(List<AuditLog> orderedRecords) {
    ChainVerifier verifier = new ChainVerifier();
    for (AuditLog row : orderedRecords) {
      AuditIntegrityResult broken = verifier.accept(row);
      if (broken != null) {
        return broken;
      }
    }
    return verifier.result();
  }

  /** Incremental verifier so {@link #verify()} can stream pages while sharing logic with the unit test. */
  private static class ChainVerifier {
    private long checked = 0L;
    private boolean chainStarted = false;
    private String expectedPreviousHash = null; // the record_hash the next row's previous_hash must equal

    /** Returns a broken result, or null to keep going. */
    AuditIntegrityResult accept(AuditLog row) {
      String storedHash = row.getRecordHash();
      if (storedHash == null) {
        // A record with no hash. Legacy (pre-Phase 4) rows form a contiguous prefix and are skipped; a
        // missing hash appearing AFTER the chain has started is a gap and is reported.
        if (chainStarted) {
          return AuditIntegrityResult.broken(row.getId(), checked, "unhashed record after the chain start");
        }
        return null;
      }
      String storedPrevious = row.getPreviousHash();
      if (chainStarted) {
        // Continuity: this row must link to the immediately prior verified row.
        if (storedPrevious == null || !storedPrevious.equals(expectedPreviousHash)) {
          return AuditIntegrityResult.broken(row.getId(), checked,
              "previous_hash does not match the prior record (a record was inserted, removed, or reordered)");
        }
      }
      // Self-integrity: recompute this row's hash from its own fields and its stored previous_hash.
      String recomputed = computeRecordHash(row, storedPrevious);
      if (!recomputed.equals(storedHash)) {
        return AuditIntegrityResult.broken(row.getId(), checked, "record_hash mismatch (the record was altered)");
      }
      expectedPreviousHash = storedHash;
      chainStarted = true;
      checked++;
      return null;
    }

    AuditIntegrityResult result() {
      return AuditIntegrityResult.intact(checked);
    }
  }

  /** The outcome of a chain verification: intact, or the audit_id and reason of the first bad record. */
  public static class AuditIntegrityResult {
    private final boolean intact;
    private final long checkedCount;
    private final long firstInvalidAuditId;
    private final String reason;

    private AuditIntegrityResult(boolean intact, long checkedCount, long firstInvalidAuditId, String reason) {
      this.intact = intact;
      this.checkedCount = checkedCount;
      this.firstInvalidAuditId = firstInvalidAuditId;
      this.reason = reason;
    }

    static AuditIntegrityResult intact(long checkedCount) {
      return new AuditIntegrityResult(true, checkedCount, -1L, null);
    }

    static AuditIntegrityResult broken(long firstInvalidAuditId, long checkedCount, String reason) {
      return new AuditIntegrityResult(false, checkedCount, firstInvalidAuditId, reason);
    }

    public boolean isIntact() {
      return intact;
    }

    public long getCheckedCount() {
      return checkedCount;
    }

    public long getFirstInvalidAuditId() {
      return firstInvalidAuditId;
    }

    public String getReason() {
      return reason;
    }
  }
}
