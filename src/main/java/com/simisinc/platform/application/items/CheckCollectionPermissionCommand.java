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

package com.simisinc.platform.application.items;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Methods to check collection object permissions
 *
 * @author matt rajkowski
 * @created 9/28/18 10:30 AM
 */
public class CheckCollectionPermissionCommand {

  private static Log LOG = LogFactory.getLog(CheckCollectionPermissionCommand.class);

  public static boolean userHasAddPermission(long collectionId, long userId) {
    // SELECT COUNT(*) FROM user_groups WHERE user_id = 2 AND group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = 14 AND add_permission = true);
    SqlUtils where = new SqlUtils();
    where.add("user_id = ?", userId);
    where.add("group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = ? AND add_permission = true)", collectionId);
    long count = DB.selectCountFrom("user_groups", where);
    return (count > 0);
  }

  public static boolean userHasViewPermission(long collectionId, long userId) {
    // SELECT COUNT(*) FROM user_groups WHERE user_id = 2 AND group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = 14 AND view_permission = true);
    SqlUtils where = new SqlUtils();
    where.add("user_id = ?", userId);
    where.add("group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = ? AND view_permission = true)", collectionId);
    long count = DB.selectCountFrom("user_groups", where);
    return (count > 0);
  }

  public static boolean userHasEditPermission(long collectionId, long userId) {
    // SELECT COUNT(*) FROM user_groups WHERE user_id = 2 AND group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = 14 AND edit_permission = true);
    SqlUtils where = new SqlUtils();
    where.add("user_id = ?", userId);
    where.add("group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = ? AND edit_permission = true)", collectionId);
    long count = DB.selectCountFrom("user_groups", where);
    return (count > 0);
  }

  public static boolean userHasDeletePermission(long collectionId, long userId) {
    // SELECT COUNT(*) FROM user_groups WHERE user_id = 2 AND group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = 14 AND delete_permission = true);
    SqlUtils where = new SqlUtils();
    where.add("user_id = ?", userId);
    where.add("group_id IN (SELECT group_id FROM collection_groups WHERE collection_id = ? AND delete_permission = true)", collectionId);
    long count = DB.selectCountFrom("user_groups", where);
    return (count > 0);
  }
}
