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

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;

import org.apache.commons.lang3.SystemUtils;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Initializes and saves system properties to the database
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class V71110__set_filepath extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {

    Connection connection = context.getConnection();

    // A runtime environment variable is used in place of the database value, if not found, this database value is used
    // Determine the OS, set a default path for files

    File basePath = null;
    if (SystemUtils.IS_OS_LINUX) {
      basePath = new File("/opt/simis");
    } else {
      File userHome = SystemUtils.getUserHome();
      File webPath = new File(userHome, "Web");
      File cmsPath = new File(webPath, "simis-cms");
      basePath = cmsPath;
    }

    {
      // Set the configuration path
      File configPath = new File(basePath, "config");
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, configPath.getPath());
        pst.setString(2, "system.configpath");
        pst.execute();
      } finally {
        pst.close();
      }
    }

    {
      // Set the file path
      File filesPath = new File(basePath, "files");
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, filesPath.getPath());
        pst.setString(2, "system.filepath");
        pst.execute();
      } finally {
        pst.close();
      }
    }

    {
      // Set the customizations path
      File customizationPath = new File(basePath, "customization");
      PreparedStatement pst = connection.prepareStatement(
          "UPDATE site_properties SET property_value = ? WHERE property_name = ?");
      try {
        pst.setString(1, customizationPath.getPath());
        pst.setString(2, "system.customizations.filepath");
        pst.execute();
      } finally {
        pst.close();
      }
    }
  }
}
