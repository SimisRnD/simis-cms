/*
 * Copyright 2023 SimIS Inc. (https://www.simiscms.com)
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
package com.simisinc.platform.infrastructure.distributedlock;

import java.time.Duration;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.infrastructure.database.SqlValue;

/**
 * A distributed lock implementation
 *
 * @author matt rajkowski
 * @created 3/26/23 5:00 PM
 */
public class LockManager {

  private static Log LOG = LogFactory.getLog(LockManager.class);

  private static String TABLE_NAME = "distributed_lock";

  public static String lock(String name, Duration duration) {

    String uuid = UUID.randomUUID().toString();

    // INSERT or UPDATE
    SqlUtils insertValues = new SqlUtils()
        .add("name", name)
        .add(new SqlValue("locked_at", SqlValue.AS_IS, "CURRENT_TIMESTAMP"))
        .add(new SqlValue("lock_until", SqlValue.AS_IS, "CURRENT_TIMESTAMP - INTERVAL '10 SECONDS' + INTERVAL '" + duration.toString() + "'"))
        .add("uuid", uuid);

    String onConflict = "ON CONFLICT (name) " +
        "DO UPDATE SET " +
        "locked_at = EXCLUDED.locked_at, " +
        "lock_until = EXCLUDED.lock_until, " +
        "uuid = EXCLUDED.uuid " +
        "WHERE distributed_lock.name = EXCLUDED.name AND CURRENT_TIMESTAMP >= distributed_lock.lock_until";

    if (DB.insertIntoWithSuccess(TABLE_NAME, insertValues, onConflict)) {
      LOG.debug("Lock succeeded: " + name);
      return uuid;
    }
    return null;
  }

  public static boolean unlock(String name, String uuid) {
    // Expire right away
    SqlUtils updateValues = new SqlUtils()
        .add(new SqlValue("lock_until", SqlValue.AS_IS, "CURRENT_TIMESTAMP"));

    SqlUtils where = new SqlUtils()
        .add("name = ?", name)
        .add("uuid = ?", uuid);
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return true;
    }
    return false;
  }
}
