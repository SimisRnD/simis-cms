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

import com.simisinc.platform.domain.model.audit.AuditLog;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the JSON serialization of an audit event (the stream that feeds the SIEM)
 *
 * @author SimIS Inc.
 */
class SaveAuditEventCommandTest {

  private AuditLog baseEvent() {
    AuditLog event = new AuditLog();
    event.setOccurred(Timestamp.from(Instant.parse("2026-07-20T14:19:22Z")));
    event.setEventCategory("authentication");
    event.setEventType("authentication.login.success");
    event.setOutcome("success");
    return event;
  }

  @Test
  void jsonCarriesTheCoreFieldsWithCorrectTypes() {
    AuditLog event = baseEvent();
    event.setActorUserId(42L);
    event.setActorUsername("jdoe@example.com");
    event.setSourceIp("203.0.113.45");
    event.setSessionId("abc123");

    String json = SaveAuditEventCommand.toJson(event);

    assertTrue(json.startsWith("{"));
    assertTrue(json.endsWith("}"));
    assertTrue(json.contains("\"schema\":\"simis.audit.v1\""));
    assertTrue(json.contains("\"ts\":\"2026-07-20T14:19:22Z\""));
    assertTrue(json.contains("\"category\":\"authentication\""));
    assertTrue(json.contains("\"event\":\"authentication.login.success\""));
    assertTrue(json.contains("\"result\":\"success\""));
    // Numeric, unquoted
    assertTrue(json.contains("\"actorUserId\":42"));
    assertTrue(json.contains("\"actorUsername\":\"jdoe@example.com\""));
    assertTrue(json.contains("\"sourceIp\":\"203.0.113.45\""));
    assertTrue(json.contains("\"sessionId\":\"abc123\""));
  }

  @Test
  void nullFieldsAndAnUnknownActorAreOmitted() {
    AuditLog event = baseEvent();
    event.setEventType("authentication.login.failure");
    event.setOutcome("failure");
    event.setActorUserId(-1L); // unknown actor (e.g. a failed login for a non-existent account)
    event.setActorUsername("attacker@example.com");
    // sourceIp, sessionId, targetType, details deliberately left null

    String json = SaveAuditEventCommand.toJson(event);

    assertFalse(json.contains("actorUserId"), "the actor id must be omitted when unknown (-1)");
    assertFalse(json.contains("sourceIp"), "null fields must be omitted");
    assertFalse(json.contains("targetType"));
    assertFalse(json.contains("details"));
    assertTrue(json.contains("\"actorUsername\":\"attacker@example.com\""));
  }

  @Test
  void stringValuesAreJsonEscaped() {
    AuditLog event = baseEvent();
    // A quote in a captured value must be escaped so it cannot break out of the JSON string
    event.setActorUsername("a\"b");

    String json = SaveAuditEventCommand.toJson(event);

    // The embedded quote is escaped (backslash-quote), keeping the document valid
    assertTrue(json.contains("a\\\"b"));
  }

  @Test
  void newlinesInAValueCannotForgeASecondLogLine() {
    AuditLog event = baseEvent();
    // A CR/LF in an attacker-supplied value (e.g. the email on a failed login) must not split the
    // single-line JSON and inject a forged audit line into the stdout stream.
    event.setActorUsername("victim\r\nAUDIT forged line");

    String json = SaveAuditEventCommand.toJson(event);

    assertFalse(json.contains("\n"), "the emitted line must not contain a raw newline (log-forging defense)");
    assertFalse(json.contains("\r"), "the emitted line must not contain a raw carriage return");
    // The value is preserved, just escaped inline
    assertTrue(json.contains("AUDIT forged line"));
  }
}
