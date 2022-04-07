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

import org.apache.commons.lang3.SystemUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class V71110__set_filepath extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    Connection connection = context.getConnection();

    // Determine the OS, set a default path for files
    String basePath = null;
    if (SystemUtils.IS_OS_LINUX) {
      basePath = "/opt/simis";
    } else {
      basePath = SystemUtils.getUserHome() + "/Web/simis-cms";
    }

    {
      // Set the configuration path
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, basePath + "/config");
        pst.setString(2, "system.configpath");
        pst.execute();
      } finally {
        pst.close();
      }
    }

    {
      // Set the file path
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, basePath + "/files");
        pst.setString(2, "system.filepath");
        pst.execute();
      } finally {
        pst.close();
      }
    }

    {
      // Set the customizations path
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, basePath + "/customization");
        pst.setString(2, "system.customizations.filepath");
        pst.execute();
      } finally {
        pst.close();
      }
    }

  }
}
