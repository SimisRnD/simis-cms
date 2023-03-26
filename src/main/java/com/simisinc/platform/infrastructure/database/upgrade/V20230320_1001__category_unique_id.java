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
import com.simisinc.platform.domain.model.items.Category;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Updates existing category records with a unique id
 *
 * @author matt rajkowski
 * @created 3/19/23 5:00 PM
 */
public class V20230320_1001__category_unique_id extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    Connection connection = context.getConnection();
    List<Category> categoryList = new ArrayList<>();

    {
      // Get a list of all users (user_id, first_name, last_name)
      try (Statement st = connection.createStatement()) {
        ResultSet rs = st.executeQuery("SELECT category_id, collection_id, name FROM categories WHERE unique_id IS NULL");
        while (rs.next()) {
          Category category = new Category();
          category.setId(rs.getLong("category_id"));
          category.setCollectionId(rs.getLong("collection_id"));
          category.setName(rs.getString("name"));
          categoryList.add(category);
        }
        rs.close();
      }
    }

    // Save a unique_user_id
    for (Category category : categoryList) {
      // Create a uniqueId
      String name = category.getName();
      String value = MakeContentUniqueIdCommand.parseToValidValue(name);
      String uniqueId = generateUniqueId(connection, category.getCollectionId(), value);
      updateUniqueId(connection, category.getId(), uniqueId);
    }
  }

  private String generateUniqueId(Connection connection, long collectionId, String baseValue) throws Exception {
    String uniqueId = baseValue;
    try (PreparedStatement pst = connection.prepareStatement("SELECT * FROM categories WHERE collection_id = ? AND unique_id = ?")) {
      int count = 1;
      while (!testUniqueId(pst, collectionId, uniqueId)) {
        ++count;
        uniqueId = baseValue + "-" + count;
      }
    }
    return uniqueId;
  }

  private boolean testUniqueId(PreparedStatement pst, long collectionId, String uniqueId) throws Exception {
    boolean result = true;
    pst.setLong(1, collectionId);
    pst.setString(2, uniqueId);
    ResultSet rs = pst.executeQuery();
    if (rs.next()) {
      result = false;
    }
    rs.close();
    return result;
  }

  private void updateUniqueId(Connection connection, long userId, String uniqueId) throws Exception {
    try (PreparedStatement pst = connection.prepareStatement("UPDATE categories SET unique_id = ? WHERE category_id = ?")) {
      int i = 0;
      pst.setString(++i, uniqueId);
      pst.setLong(++i, userId);
      pst.execute();
    }
  }
}
