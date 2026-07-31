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

package com.simisinc.platform.infrastructure.scheduler;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Covers only the {@link SchedulerManager#getStorageProvider()} accessor added for the Job Queue
 * Dashboard (issue #464). {@link SchedulerManager#startup} itself is not exercised here -- it reads
 * a real ServletContext resource stream and stands up a live JobRunr configuration (storage
 * provider, background job server, recurring job schedules), which is integration-test territory,
 * not something worth mocking piece by piece for a single field assignment.
 *
 * @author SimIS
 * @created 7/30/2026
 */
class SchedulerManagerTest {

  @Test
  void getStorageProviderIsNullBeforeStartupHasRun() {
    // shutdown() is also what resets the field, so this doubles as coverage of that reset -- neither
    // startup() nor shutdown() have run in this JVM in a way that would leave storageProvider set to
    // anything else, but shutdown() first makes the assertion deterministic regardless of test order.
    SchedulerManager.shutdown();
    assertNull(SchedulerManager.getStorageProvider());
  }
}
