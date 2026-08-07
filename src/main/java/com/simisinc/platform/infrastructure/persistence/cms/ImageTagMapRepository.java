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

package com.simisinc.platform.infrastructure.persistence.cms;

import java.sql.Connection;
import java.sql.SQLException;

import com.simisinc.platform.domain.model.cms.Image;
import com.simisinc.platform.domain.model.cms.ImageTag;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves image-to-tag assignment rows (the {@code image_tag_map} join table).
 * Mirrors items' ItemTagRepository, minus the collectionId column items carry.
 *
 * @author SimIS
 * @created 8/5/2026
 */
public class ImageTagMapRepository {

  private static String TABLE_NAME = "image_tag_map";
  private static String[] PRIMARY_KEY = new String[] { "id" };

  public static void insertImageTagId(Connection connection, Image image, long imageTagId) throws SQLException {
    if (image == null) {
      return;
    }
    SqlUtils insertValues = new SqlUtils()
        .add("image_id", image.getId())
        .add("image_tag_id", imageTagId);
    DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY);
  }

  public static void removeAll(Connection connection, Image image) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("image_id = ?", image.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }

  public static void removeAll(Connection connection, ImageTag imageTag) throws SQLException {
    SqlUtils where = new SqlUtils();
    where.add("image_tag_id = ?", imageTag.getId());
    DB.deleteFrom(connection, TABLE_NAME, where);
  }
}
