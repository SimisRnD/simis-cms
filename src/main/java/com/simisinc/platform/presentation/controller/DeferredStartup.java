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

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;

/**
 * Runs the database-dependent half of startup, and when the database cannot be reached, keeps
 * retrying it in the background until it can.
 *
 * <p>Before this existed, a start while the database was unreachable was permanent: the pool failed
 * fast, ContextListener aborted, Tomcat marked the ROOT context failed, and every path -- /healthz
 * included -- answered 404 until something restarted the process again. Azure's auto-heal recycled
 * the site part-way through both production database outages (2026-09-06 and 2026-10-14), which is
 * exactly that start. Now ContextListener returns normally, WebRequestFilter answers 503 while
 * startup is incomplete, and this finishes startup as soon as the database answers.
 *
 * <p>A normal boot is unchanged: ContextListener makes the first {@link #attempt} itself, on Tomcat's
 * startup thread, so the site is fully started before Tomcat takes its first request. Only a failed
 * first attempt moves to the background.
 *
 * @author SimIS Inc.
 */
final class DeferredStartup {

  private static final Log LOG = LogFactory.getLog(DeferredStartup.class);

  /** The result of one attempt. */
  enum Outcome {
    /** Startup completed; the application is serving. */
    STARTED,
    /** The database could not be reached; try again later. */
    WAITING_FOR_DATABASE,
    /** Startup failed with the database reachable -- a fault a retry would not fix. */
    FAILED
  }

  /** The database-dependent half of startup. */
  @FunctionalInterface
  interface Step {
    /** @return true when startup completed, false when it failed and has already logged why */
    boolean run() throws Exception;
  }

  /** Throws when the database cannot be used right now. */
  @FunctionalInterface
  interface DatabaseProbe {
    void check() throws Exception;
  }

  /** The production probe: a pooled connection can be borrowed and validated. */
  static final DatabaseProbe POOLED_CONNECTION = () -> {
    try (Connection connection = DB.getConnection()) {
      if (!connection.isValid(2)) {
        throw new SQLException("Connection could not be validated");
      }
    }
  };

  private final ScheduledExecutorService executor;
  private final CompletableFuture<Outcome> outcome = new CompletableFuture<>();

  private DeferredStartup(ScheduledExecutorService executor) {
    this.executor = executor;
  }

  /**
   * Runs the step once if the database can be reached. A step that fails is retried only when the
   * database has gone away again since -- any other failure is final, as it always was.
   */
  static Outcome attempt(DatabaseProbe probe, Step step) {
    String unavailable = unavailableReason(probe);
    if (unavailable != null) {
      LOG.warn("Waiting for the database: " + unavailable);
      return Outcome.WAITING_FOR_DATABASE;
    }
    Exception failure = null;
    try {
      if (step.run()) {
        return Outcome.STARTED;
      }
    } catch (Exception e) {
      failure = e;
    }
    unavailable = unavailableReason(probe);
    if (unavailable != null) {
      LOG.warn("Lost the database during startup, will retry: " + unavailable, failure);
      return Outcome.WAITING_FOR_DATABASE;
    }
    if (failure != null) {
      LOG.error("Startup failed", failure);
    }
    return Outcome.FAILED;
  }

  /**
   * Retries {@link #attempt} every {@code retryDelay} on a background thread until it returns
   * anything other than {@link Outcome#WAITING_FOR_DATABASE}.
   *
   * <p>Call from the listener's own thread: the retry thread takes this thread's context class
   * loader, which there is the web application's, so startup code that loads resources by it
   * (JobRunr, the workflow YAML) behaves as it would on Tomcat's startup thread.
   */
  static DeferredStartup retry(DatabaseProbe probe, Step step, Duration retryDelay) {
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "deferred-startup");
      thread.setDaemon(true);
      thread.setContextClassLoader(contextClassLoader);
      return thread;
    });
    DeferredStartup deferredStartup = new DeferredStartup(executor);
    executor.scheduleWithFixedDelay(() -> {
      Outcome result;
      try {
        result = attempt(probe, step);
      } catch (Throwable t) {
        // An exception escaping a scheduled task silently cancels every later run, which would
        // leave the site on 503 with nothing in the log saying why
        LOG.error("Deferred startup failed", t);
        result = Outcome.FAILED;
      }
      if (result != Outcome.WAITING_FOR_DATABASE) {
        deferredStartup.outcome.complete(result);
        executor.shutdown();
      }
    }, retryDelay.toMillis(), retryDelay.toMillis(), TimeUnit.MILLISECONDS);
    return deferredStartup;
  }

  /** Waits up to {@code timeout} for the retries to finish; null if they are still waiting. */
  Outcome awaitOutcome(Duration timeout) throws InterruptedException {
    try {
      return outcome.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      return null;
    } catch (ExecutionException e) {
      return Outcome.FAILED;
    }
  }

  /**
   * Stops retrying, interrupting an attempt in progress and waiting briefly for it to finish so the
   * caller does not shut the scheduler or pool down underneath it.
   */
  void stop() {
    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
        LOG.warn("Deferred startup did not stop within 30 seconds");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Null when the database can be used; otherwise why not, naming the root cause. */
  private static String unavailableReason(DatabaseProbe probe) {
    try {
      probe.check();
      return null;
    } catch (Exception e) {
      // The root cause, because a pool timeout's own message never says why -- "Connection is not
      // available, request timed out after 30000ms" reads the same for a database that is
      // restarting and for a wrong password, and only the cause tells them apart
      return ExceptionUtils.getRootCauseMessage(e);
    }
  }
}
