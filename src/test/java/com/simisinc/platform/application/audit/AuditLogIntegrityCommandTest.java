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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.application.audit.AuditLogIntegrityCommand.AuditIntegrityResult;
import com.simisinc.platform.domain.model.audit.AuditLog;

/**
 * Tests the tamper-evidence hash chain: the hashing is deterministic and field-sensitive, and verification
 * detects altered, deleted, reordered, inserted, and unhashed records while tolerating a legacy prefix and a
 * retention purge of the oldest records.
 *
 * @author SimIS Inc.
 */
class AuditLogIntegrityCommandTest {

  private static final Instant BASE = Instant.parse("2026-07-20T12:00:00Z");

  private static AuditLog event(long id, String eventType) {
    AuditLog e = new AuditLog();
    e.setId(id);
    // Whole-second precision, so the value is already millisecond-exact (no normalization needed here).
    e.setOccurred(Timestamp.from(BASE.plusSeconds(id)));
    e.setEventCategory("authentication");
    e.setEventType(eventType);
    e.setOutcome("success");
    e.setActorUserId(id);
    e.setActorUsername("user" + id);
    e.setSourceIp("10.0.0." + id);
    e.setSessionId("sess" + id);
    return e;
  }

  /** Links each record to the one before it, starting from the genesis hash, exactly as the repository does. */
  private static List<AuditLog> chain(AuditLog... records) {
    String previousHash = AuditLogIntegrityCommand.GENESIS_HASH;
    for (AuditLog r : records) {
      r.setPreviousHash(previousHash);
      r.setRecordHash(AuditLogIntegrityCommand.computeRecordHash(r, previousHash));
      previousHash = r.getRecordHash();
    }
    return new ArrayList<>(Arrays.asList(records));
  }

  @Test
  void computeRecordHashIsDeterministicAndSensitive() {
    AuditLog e = event(1, "login.success");
    String h1 = AuditLogIntegrityCommand.computeRecordHash(e, AuditLogIntegrityCommand.GENESIS_HASH);
    String h2 = AuditLogIntegrityCommand.computeRecordHash(e, AuditLogIntegrityCommand.GENESIS_HASH);
    assertEquals(h1, h2, "same record + same previous hash must give the same hash");
    assertEquals(64, h1.length(), "SHA-256 hex is 64 characters");
    // A different previous hash yields a different record hash (that is what chains the records)
    assertNotEquals(h1, AuditLogIntegrityCommand.computeRecordHash(e, h1));
    // A changed field yields a different record hash
    e.setOutcome("failure");
    assertNotEquals(h1, AuditLogIntegrityCommand.computeRecordHash(e, AuditLogIntegrityCommand.GENESIS_HASH));
  }

  @Test
  void canonicalEncodingIsInjective() {
    // ("ab","c") and ("a","bc") must not collide -- the length prefix disambiguates the field boundary.
    AuditLog a = event(1, "x");
    a.setTargetType("ab");
    a.setTargetId("c");
    AuditLog b = event(1, "x");
    b.setTargetType("a");
    b.setTargetId("bc");
    assertNotEquals(
        AuditLogIntegrityCommand.computeRecordHash(a, AuditLogIntegrityCommand.GENESIS_HASH),
        AuditLogIntegrityCommand.computeRecordHash(b, AuditLogIntegrityCommand.GENESIS_HASH));
  }

  @Test
  void intactChainVerifies() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"), event(3, "c"));
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(rows);
    assertTrue(result.isIntact());
    assertEquals(3, result.getCheckedCount());
  }

  @Test
  void detectsAnAlteredRecord() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"), event(3, "c"));
    // Change a field on the middle record without recomputing its hash -- an in-place edit.
    rows.get(1).setDetails("tampered");
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(rows);
    assertFalse(result.isIntact());
    assertEquals(2L, result.getFirstInvalidAuditId());
    assertEquals(1, result.getCheckedCount(), "the record before the tampered one still verified");
    assertTrue(result.getReason().contains("record_hash"));
  }

  @Test
  void detectsABrokenLink() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"), event(3, "c"));
    // Repoint the last record's previous_hash so it no longer links to the prior record.
    rows.get(2).setPreviousHash("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef0");
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(rows);
    assertFalse(result.isIntact());
    assertEquals(3L, result.getFirstInvalidAuditId());
    assertTrue(result.getReason().contains("previous_hash"));
  }

  @Test
  void detectsADeletedMiddleRecord() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"), event(3, "c"));
    // Remove the middle record: record 3 now links to a record that is no longer present.
    rows.remove(1);
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(rows);
    assertFalse(result.isIntact());
    assertEquals(3L, result.getFirstInvalidAuditId());
  }

  @Test
  void toleratesARetentionPurgeOfOldestRecords() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"), event(3, "c"), event(4, "d"));
    // Simulate the retention job deleting the two oldest records; the chain must still verify from the
    // oldest surviving record (anchored on its own stored previous_hash).
    List<AuditLog> survivors = new ArrayList<>(rows.subList(2, 4));
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(survivors);
    assertTrue(result.isIntact());
    assertEquals(2, result.getCheckedCount());
  }

  @Test
  void skipsLegacyPrefixThenVerifiesFromFirstHashedRecord() {
    // Records written before Phase 4 have no hash; they form a contiguous prefix that is skipped.
    AuditLog legacy1 = event(1, "a");
    AuditLog legacy2 = event(2, "b");
    List<AuditLog> hashed = chain(event(3, "c"), event(4, "d"));
    List<AuditLog> all = new ArrayList<>();
    all.add(legacy1);
    all.add(legacy2);
    all.addAll(hashed);
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(all);
    assertTrue(result.isIntact());
    assertEquals(2, result.getCheckedCount());
  }

  @Test
  void detectsAnUnhashedRecordAfterTheChainStarts() {
    List<AuditLog> rows = chain(event(1, "a"), event(2, "b"));
    AuditLog stripped = event(3, "c");
    // A record with no hash appearing after the chain has started is a gap, not a legacy prefix.
    rows.add(stripped);
    AuditIntegrityResult result = AuditLogIntegrityCommand.verifyChain(rows);
    assertFalse(result.isIntact());
    assertEquals(3L, result.getFirstInvalidAuditId());
    assertTrue(result.getReason().contains("unhashed"));
  }
}
