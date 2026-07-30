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

package com.simisinc.platform.infrastructure.persistence;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists role_capabilities join rows (issue #704) - the only place role_capabilities is ever
 * mutated at runtime. Everything seeded by the #701 walking-skeleton migration was a one-time
 * INSERT with no equivalent runtime path until this repository.
 *
 * @author elizabeth houser
 */
public class RoleCapabilityRepository {

  private static String TABLE_NAME = "role_capabilities";

  public static boolean grant(long roleId, long capabilityId) {
    SqlUtils insertValues = new SqlUtils()
        .add("role_id", roleId)
        .add("capability_id", capabilityId);
    return DB.insertIntoWithConflict(TABLE_NAME, insertValues, "ON CONFLICT DO NOTHING");
  }

  public static boolean revoke(long roleId, long capabilityId) {
    SqlUtils where = new SqlUtils()
        .add("role_id = ?", roleId)
        .add("capability_id = ?", capabilityId);
    return DB.deleteFrom(TABLE_NAME, where) > 0;
  }

  /**
   * How many roles (system-wide, not just one) currently grant this capability. Used by
   * SaveRoleCapabilitiesCommand's self-lockout guard before revoking admin:manage.
   */
  public static long countRolesGrantedCapability(long capabilityId) {
    return DB.selectCountFrom(TABLE_NAME, new SqlUtils().add("capability_id = ?", capabilityId));
  }
}
