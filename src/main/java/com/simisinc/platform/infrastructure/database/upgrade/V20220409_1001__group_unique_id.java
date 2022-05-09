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

package com.simisinc.platform.infrastructure.database.upgrade;

import com.simisinc.platform.application.cms.MakeContentUniqueIdCommand;
import com.simisinc.platform.domain.model.Group;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates existing group records to have a unique id
 *
 * @author matt rajkowski
 * @created 4/9/22 8:45 AM
 */
public class V20220409_1001__group_unique_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    Connection connection = context.getConnection();
    List<Group> groupList = new ArrayList<>();

    {
      // Get a list of all users (user_id, first_name, last_name)
      Statement st = connection.createStatement();
      ResultSet rs = st.executeQuery("SELECT group_id, name FROM groups WHERE unique_id IS NULL");
      while (rs.next()) {
        Group group = new Group();
        group.setId(rs.getLong("group_id"));
        group.setName(rs.getString("name"));
        groupList.add(group);
      }
      rs.close();
      st.close();
    }

    // Save a unique_user_id
    for (Group group : groupList) {
      // Create a uniqueId
      String name = group.getName();
      String value = MakeContentUniqueIdCommand.parseToValidValue(name);
      String uniqueId = generateUniqueId(connection, value);
      updateUniqueId(connection, group.getId(), uniqueId);
    }
  }

  private String generateUniqueId(Connection connection, String baseValue) throws Exception {
    PreparedStatement pst = connection.prepareStatement("SELECT * FROM groups WHERE unique_id = ?");
    int count = 1;
    String uniqueId = baseValue;
    while (!testUniqueId(pst, uniqueId)) {
      ++count;
      uniqueId = baseValue + "-" + count;
    }
    pst.close();
    return uniqueId;
  }

  private boolean testUniqueId(PreparedStatement pst, String uniqueId) throws Exception {
    boolean result = true;
    pst.setString(1, uniqueId);
    ResultSet rs = pst.executeQuery();
    if (rs.next()) {
      result = false;
    }
    rs.close();
    return result;
  }

  private void updateUniqueId(Connection connection, long userId, String uniqueId) throws Exception {
    PreparedStatement pst = connection.prepareStatement("UPDATE groups SET unique_id = ? WHERE group_id = ?");
    int i = 0;
    pst.setString(++i, uniqueId);
    pst.setLong(++i, userId);
    pst.execute();
    pst.close();
  }
}
