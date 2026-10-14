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

package com.simisinc.platform.presentation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.ConnectException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.presentation.controller.DeferredStartup.DatabaseProbe;
import com.simisinc.platform.presentation.controller.DeferredStartup.Outcome;

/**
 * How startup decides between waiting for the database and failing for good, and that the
 * background retry stops as soon as it has an answer. The same flow against a real PostgreSQL that
 * comes up after the pool was created is in DeferredStartupDatabaseRecoveryTest.
 */
class DeferredStartupTest {

  private static final DatabaseProbe UP = () -> {
  };

  private static final DatabaseProbe DOWN = () -> {
    throw new SQLException("Connection is not available, request timed out after 30000ms",
        new ConnectException("Connection refused"));
  };

  @Test
  void waitsWithoutRunningTheStepWhileTheDatabaseIsUnreachable() {
    AtomicBoolean ran = new AtomicBoolean(false);
    assertEquals(Outcome.WAITING_FOR_DATABASE, DeferredStartup.attempt(DOWN, () -> ran.getAndSet(true)));
    assertFalse(ran.get());
  }

  @Test
  void startsWhenTheDatabaseIsReachableAndTheStepSucceeds() {
    assertEquals(Outcome.STARTED, DeferredStartup.attempt(UP, () -> true));
  }

  @Test
  void aStepThatFailsWithTheDatabaseStillReachableIsFinal() {
    // A failed migration or a missing system.filepath: retrying would not fix it, and must not
    // turn a startup that always failed for good into an endless retry loop
    assertEquals(Outcome.FAILED, DeferredStartup.attempt(UP, () -> false));
    assertEquals(Outcome.FAILED, DeferredStartup.attempt(UP, () -> {
      throw new IllegalStateException("migration failed");
    }));
  }

  @Test
  void aStepThatFailsBecauseTheDatabaseWentAwayAgainKeepsWaiting() {
    AtomicInteger probes = new AtomicInteger();
    DatabaseProbe upThenDown = () -> {
      if (probes.incrementAndGet() > 1) {
        DOWN.check();
      }
    };
    assertEquals(Outcome.WAITING_FOR_DATABASE, DeferredStartup.attempt(upThenDown, () -> {
      throw new SQLException("An I/O error occurred while sending to the backend");
    }));
  }

  @Test
  void retriesUntilTheDatabaseAnswersThenRunsTheStepOnce() throws Exception {
    AtomicInteger probes = new AtomicInteger();
    DatabaseProbe downThreeTimes = () -> {
      if (probes.incrementAndGet() <= 3) {
        DOWN.check();
      }
    };
    AtomicInteger steps = new AtomicInteger();
    DeferredStartup deferredStartup = DeferredStartup.retry(downThreeTimes, () -> steps.incrementAndGet() > 0,
        Duration.ofMillis(10));
    try {
      assertEquals(Outcome.STARTED, deferredStartup.awaitOutcome(Duration.ofSeconds(10)));
      assertEquals(4, probes.get());
      assertEquals(1, steps.get());
    } finally {
      deferredStartup.stop();
    }
  }

  @Test
  void stopsRetryingOnceStartupHasFailedForGood() throws Exception {
    AtomicInteger steps = new AtomicInteger();
    DeferredStartup deferredStartup = DeferredStartup.retry(UP, () -> {
      steps.incrementAndGet();
      return false;
    }, Duration.ofMillis(10));
    try {
      assertEquals(Outcome.FAILED, deferredStartup.awaitOutcome(Duration.ofSeconds(10)));
      Thread.sleep(100);
      assertEquals(1, steps.get());
    } finally {
      deferredStartup.stop();
    }
  }

  @Test
  void anUnexpectedErrorEndsTheRetriesInsteadOfSilentlyCancellingThem() throws Exception {
    DeferredStartup deferredStartup = DeferredStartup.retry(UP, () -> {
      throw new NoClassDefFoundError("com/example/Missing");
    }, Duration.ofMillis(10));
    try {
      assertEquals(Outcome.FAILED, deferredStartup.awaitOutcome(Duration.ofSeconds(10)));
    } finally {
      deferredStartup.stop();
    }
  }

  @Test
  void keepsWaitingWhileTheDatabaseStaysDown() throws Exception {
    DeferredStartup deferredStartup = DeferredStartup.retry(DOWN, () -> true, Duration.ofMillis(10));
    try {
      assertNull(deferredStartup.awaitOutcome(Duration.ofMillis(200)));
    } finally {
      deferredStartup.stop();
    }
  }
}
