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

package com.simisinc.platform.application.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.infrastructure.distributedlock.LockManager;
import com.simisinc.platform.infrastructure.instance.InstanceManager;
import com.simisinc.platform.infrastructure.persistence.DatabaseVersionRepository;

/**
 * Covers the fix for issue #396: {@link DatabaseCommand#initialize} used to fall back to running
 * Flyway migrations unprotected -- without ever re-acquiring the lock -- after a single failed
 * lock attempt and a fixed 30-second sleep. That defeated the lock's entire purpose: two nodes
 * booting at the same time could both end up migrating concurrently.
 *
 * <p>
 * Also covers a round-2 review finding on that same fix: {@code isWebNode} only gated lock
 * <em>acquisition</em> -- the subsequent {@code isInstalled()}/{@code installDatabase()}/{@code
 * upgrade()} call ran unconditionally for every node, so a web-only node still migrated with zero
 * lock protection, not even an attempt. Web nodes must skip running migrations entirely and
 * instead wait for the primary node to finish.
 * </p>
 *
 * <p>
 * {@link LockManager} and {@link InstanceManager} are statically mocked throughout, so nothing
 * here touches a real database. Durations passed in are small so the retry/timeout tests run
 * quickly instead of waiting out the real multi-minute production values.
 * </p>
 *
 * @author SimIS
 * @created 8/1/2026
 */
class DatabaseCommandLockTest {

  private static final Duration LOCK_DURATION = Duration.ofMinutes(5);
  private static final Duration RETRY_INTERVAL = Duration.ofMillis(20);
  private static final Duration TOTAL_TIMEOUT = Duration.ofMillis(150);

  // -- acquireMigrationLock: the extracted retry loop --------------------------------------

  @Test
  void acquireMigrationLockSucceedsImmediatelyWhenUncontended() {
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class)) {
      lockManager.when(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class))).thenReturn("uuid-1");

      String result = DatabaseCommand.acquireMigrationLock("flyway_migration", LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);

      assertEquals("uuid-1", result);
      // No contention: exactly one attempt, no retries needed
      lockManager.verify(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class)), times(1));
    }
  }

  @Test
  void acquireMigrationLockRetriesAndSucceedsOnceTheLockBecomesAvailable() {
    // Simulates the scenario the original code got wrong: the lock is held by another node when
    // this node first tries, but is released partway through the retry window.
    AtomicInteger callCount = new AtomicInteger(0);
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class)) {
      lockManager.when(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class)))
          .thenAnswer(invocation -> callCount.incrementAndGet() < 3 ? null : "uuid-after-retry");

      String result = DatabaseCommand.acquireMigrationLock("flyway_migration", LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);

      assertEquals("uuid-after-retry", result, "should succeed once the lock is actually acquired, not just after waiting");
      lockManager.verify(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class)), times(3));
    }
  }

  @Test
  void acquireMigrationLockGivesUpAndReturnsNullWhenNeverAvailable() {
    // Simulates a node that is stuck holding the lock forever. The retry loop must give up after
    // totalTimeout rather than retrying indefinitely, and must NOT fabricate a lock uuid.
    try (MockedStatic<LockManager> lockManager = mockStatic(LockManager.class)) {
      lockManager.when(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class))).thenReturn(null);

      String result = DatabaseCommand.acquireMigrationLock("flyway_migration", LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);

      assertNull(result);
      // Bounded: with a 150ms timeout and a 20ms retry interval, this must not spin forever, but
      // it must have actually retried (more than the single initial attempt).
      lockManager.verify(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class)),
          org.mockito.Mockito.atLeast(2));
    }
  }

  // -- initialize: the end-to-end fail-loud-not-proceed behavior ---------------------------

  @Test
  void initializeFailsWithoutRunningMigrationsWhenTheLockIsNeverAcquired() {
    // This is the exact defect from #396: previously, exhausting the wait would fall through and
    // run Flyway anyway. Now it must return false and never even ask whether the database needs
    // installing or upgrading.
    try (MockedStatic<InstanceManager> instanceManager = mockStatic(InstanceManager.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<DatabaseVersionRepository> repository = mockStatic(DatabaseVersionRepository.class)) {
      instanceManager.when(InstanceManager::isWebNodeOnly).thenReturn(false);
      lockManager.when(LockManager::lockTableExists).thenReturn(true);
      lockManager.when(() -> LockManager.lock(eq("flyway_migration"), any(Duration.class))).thenReturn(null);

      boolean result = DatabaseCommand.initialize(new Properties(), LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);

      assertFalse(result, "initialize() must fail loudly (return false) rather than proceed unprotected");
      repository.verifyNoInteractions();
      lockManager.verify(() -> LockManager.unlock(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()),
          org.mockito.Mockito.never());
    }
  }

  @Test
  void initializeProceedsWithoutALockWhenTheLockTableDoesNotExistYet() {
    // A database that has never completed a first install has no distributed_lock table yet --
    // that table is created by the very install migration this lock would otherwise guard. In
    // that specific case LockManager.lock() would always return null for a reason unrelated to
    // contention, so initialize() must recognize it via lockTableExists() and proceed rather than
    // waiting out the full timeout (which would be a startup regression for every fresh install).
    try (MockedStatic<InstanceManager> instanceManager = mockStatic(InstanceManager.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class)) {
      instanceManager.when(InstanceManager::isWebNodeOnly).thenReturn(false);
      lockManager.when(LockManager::lockTableExists).thenReturn(false);

      long start = System.currentTimeMillis();
      try {
        // Past the lock phase, isInstalled()/installDatabase() run for real against no
        // configured DataSource and will fail -- irrelevant here, only the lock phase (before
        // that point) is under test, so the resulting exception is expected and discarded.
        DatabaseCommand.initialize(new Properties(), LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);
      } catch (Throwable expected) {
        // ignored: no real database is configured in this unit test
      }
      long elapsed = System.currentTimeMillis() - start;

      assertTrue(elapsed < TOTAL_TIMEOUT.toMillis(),
          "should not wait out the lock-acquire timeout when the lock table doesn't exist yet");
      lockManager.verify(() -> LockManager.lock(org.mockito.ArgumentMatchers.anyString(), any(Duration.class)),
          org.mockito.Mockito.never());
    }
  }

  @Test
  void webOnlyNodeNeverTouchesTheLockAtAll() {
    // Unrelated to this fix, but a regression guard: the web-only-node optimization must keep
    // working untouched by the lock-retry changes.
    try (MockedStatic<InstanceManager> instanceManager = mockStatic(InstanceManager.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class)) {
      instanceManager.when(InstanceManager::isWebNodeOnly).thenReturn(true);

      try {
        // Past the lock phase, waitForPrimaryMigration() attempts a real (fast-failing) Flyway
        // call against no configured DataSource -- isMigrationUpToDate() catches that internally
        // and just keeps retrying, so this returns false rather than throwing; irrelevant here,
        // only the lock phase (before that point) is under test.
        DatabaseCommand.initialize(new Properties(), LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);
      } catch (Throwable unexpected) {
        // tolerated: environment-dependent Flyway/driver behavior against a bogus URL
      }

      lockManager.verifyNoInteractions();
    }
  }

  // -- initialize: a web-only node must skip migration EXECUTION, not just lock acquisition ----

  @Test
  void webOnlyNodeNeverCallsIsInstalledInstallOrUpgrade() {
    // This is the round-2 defect: isWebNode previously only gated lock acquisition, so a web node
    // still ran isInstalled()/installDatabase()/upgrade() -- unprotected Flyway migration on every
    // web node. DatabaseVersionRepository is the only thing isInstalled()/installDatabase() ever
    // touch, so zero interactions with it is direct proof that path was never entered; the node
    // must go through waitForPrimaryMigration() instead.
    try (MockedStatic<InstanceManager> instanceManager = mockStatic(InstanceManager.class);
        MockedStatic<LockManager> lockManager = mockStatic(LockManager.class);
        MockedStatic<DatabaseVersionRepository> repository = mockStatic(DatabaseVersionRepository.class)) {
      instanceManager.when(InstanceManager::isWebNodeOnly).thenReturn(true);

      // No real primary is migrating in this test, so waiting for one to finish times out against
      // the small TOTAL_TIMEOUT/RETRY_INTERVAL budget; that's the expected outcome here -- what's
      // under test is which code path ran, not whether it succeeded.
      boolean result = DatabaseCommand.initialize(new Properties(), LOCK_DURATION, TOTAL_TIMEOUT, RETRY_INTERVAL);

      assertFalse(result, "should refuse to start rather than fall through to running migrations itself");
      repository.verifyNoInteractions();
      lockManager.verifyNoInteractions();
    }
  }

  // -- waitForPrimaryMigration: the extracted retry loop (BooleanSupplier overload) ------------

  @Test
  void waitForPrimaryMigrationReturnsTrueImmediatelyWhenAlreadyComplete() {
    AtomicInteger callCount = new AtomicInteger(0);
    BooleanSupplier migrationComplete = () -> {
      callCount.incrementAndGet();
      return true;
    };

    boolean result = DatabaseCommand.waitForPrimaryMigration(migrationComplete, TOTAL_TIMEOUT, RETRY_INTERVAL);

    assertTrue(result);
    assertEquals(1, callCount.get(), "no contention: exactly one check, no retries needed");
  }

  @Test
  void waitForPrimaryMigrationRetriesAndSucceedsOnceThePrimaryFinishes() {
    // Simulates the real deployment scenario: the primary is still migrating when the web node
    // first checks, and finishes partway through the web node's retry window.
    AtomicInteger callCount = new AtomicInteger(0);
    BooleanSupplier migrationComplete = () -> callCount.incrementAndGet() >= 3;

    boolean result = DatabaseCommand.waitForPrimaryMigration(migrationComplete, TOTAL_TIMEOUT, RETRY_INTERVAL);

    assertTrue(result, "should succeed once the primary's migrations actually complete, not just after waiting");
    assertEquals(3, callCount.get());
  }

  @Test
  void waitForPrimaryMigrationGivesUpAndReturnsFalseWhenThePrimaryNeverFinishes() {
    // The primary is stuck or never started. The web node must give up after timeout rather than
    // waiting forever, and must not proceed as if migrations were complete.
    AtomicInteger callCount = new AtomicInteger(0);
    BooleanSupplier migrationComplete = () -> {
      callCount.incrementAndGet();
      return false;
    };

    boolean result = DatabaseCommand.waitForPrimaryMigration(migrationComplete, TOTAL_TIMEOUT, RETRY_INTERVAL);

    assertFalse(result);
    // Bounded: with a 150ms timeout and a 20ms retry interval, this must not spin forever, but it
    // must have actually retried (more than the single initial check).
    assertTrue(callCount.get() >= 2, "should have retried at least once before giving up");
  }
}
