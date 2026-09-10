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

package com.simisinc.platform.infrastructure.persistence.workflow;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * At-most-once bookkeeping for workflow side effects that cannot be undone.
 *
 * <p>
 * A background job is retryable by design, which is right for work that can be repeated safely and
 * wrong for work that cannot. Sending mail is the second kind: once it has left, running the step
 * again corrects nothing, it delivers a second copy. Issue 1643 is what that costs -- a playbook
 * reported failure after its email had already gone out, the enclosing job retried it whole, and
 * every contact-form submission produced two identical notifications.
 * </p>
 *
 * <p>
 * The claim IS the insert. Two attempts race on the primary key and the database settles it, so
 * there is no read-then-write window for a concurrent retry to slip through. A caller that loses
 * the race is told so and skips its side effect.
 * </p>
 */
public class WorkflowNotificationSentRepository {

  private static Log LOG = LogFactory.getLog(WorkflowNotificationSentRepository.class);

  private static String TABLE_NAME = "workflow_notification_sent";

  /** Matches the column width; a longer key is truncated rather than failing the insert. */
  private static final int KEY_LENGTH = 255;

  private WorkflowNotificationSentRepository() {
    // Static repository, not instantiated
  }

  /**
   * Claims the right to perform a one-time side effect.
   *
   * <p>
   * Fails CLOSED on a database error. An unreachable database means it is not known whether this
   * notification already went out, and sending on that guess is the outcome this class exists to
   * prevent: a missed notification is recoverable by looking at the stored record, a duplicate one
   * is already in someone's inbox.
   * </p>
   *
   * @param key identifies the side effect; must be stable across a retry of the same work
   * @return true when the caller may proceed; false when it has already been done, or when that
   *         could not be determined
   */
  public static boolean claim(String key) {
    if (StringUtils.isBlank(key)) {
      LOG.error("A blank notification key cannot be claimed");
      return false;
    }
    try {
      SqlUtils insertValues = new SqlUtils().add("notification_key", StringUtils.left(key, KEY_LENGTH));
      boolean claimed = DB.insertIntoWithConflict(TABLE_NAME, insertValues,
          "ON CONFLICT (notification_key) DO NOTHING");
      if (!claimed && LOG.isDebugEnabled()) {
        LOG.debug("Notification already sent, skipping: " + key);
      }
      return claimed;
    } catch (Exception e) {
      LOG.error("Could not claim the notification key '" + key
          + "'; skipping the send rather than risking a duplicate", e);
      return false;
    }
  }

  /**
   * Gives a claim back after the side effect did not happen.
   *
   * <p>
   * Without this, claiming before acting would turn every transient failure into a permanently
   * skipped notification: the claim would stand, and the retry the failure exists to trigger would
   * find the work already "done". Releasing on failure keeps the guarantee at at-most-once for
   * effects that succeeded, while leaving a genuine failure free to be retried.
   * </p>
   *
   * @param key the key previously claimed
   */
  public static void release(String key) {
    if (StringUtils.isBlank(key)) {
      return;
    }
    try {
      DB.deleteFrom(TABLE_NAME, new SqlUtils().add("notification_key = ?", StringUtils.left(key, KEY_LENGTH)));
    } catch (Exception e) {
      // The effect did not happen and the claim now blocks a retry of it. Worth a loud log: it is
      // a missed notification, which is the failure direction this class deliberately prefers.
      LOG.error("Could not release the notification key '" + key
          + "'; a retry of this notification will be skipped", e);
    }
  }
}
