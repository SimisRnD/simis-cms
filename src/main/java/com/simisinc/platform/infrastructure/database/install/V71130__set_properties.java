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

package com.simisinc.platform.infrastructure.database.install;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Initializes and saves system properties to the database
 *
 * @author matt rajkowski
 * @created 4/21/22 5:12 PM
 */
public class V71130__set_properties extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    // See if the SSL override is specified
    if (System.getenv().containsKey("CMS_FORCE_SSL")) {
      boolean forceSSL = !"false".equals(System.getenv("CMS_FORCE_SSL"));
      if (!forceSSL) {
        Connection connection = context.getConnection();
        try (PreparedStatement pst = connection.prepareStatement(
            "UPDATE site_properties SET property_value = ? WHERE property_name = ?")) {
          pst.setString(1, "false");
          pst.setString(2, "system.ssl");
          pst.execute();
        }
      }
    }
  }
}
