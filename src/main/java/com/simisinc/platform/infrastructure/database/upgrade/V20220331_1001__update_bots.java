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

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import com.simisinc.platform.application.SessionCommand;
import com.simisinc.platform.application.filesystem.FileSystemCommand;

/**
 * Updates the session information based on identifying bots
 *
 * @author matt rajkowski
 * @created 3/7/20 4:29 PM
 */
public class V20220331_1001__update_bots extends BaseJavaMigration {

  private static Log LOG = LogFactory.getLog(BaseJavaMigration.class);

  @Override
  public void migrate(Context context) throws Exception {

    File file = FileSystemCommand.getFileServerConfigPath("cms", SessionCommand.BOT_LIST);
    if (file == null || !file.exists()) {
      return;
    }

    List<String> botList = FileSystemCommand.loadFileToList(file);

    StringBuilder where = new StringBuilder();
    for (int i = 0; i < botList.size(); i++) {
      if (where.length() > 0) {
        where.append(" OR ");
      }
      where.append("user_agent LIKE ? ESCAPE '!'");
    }

    Connection connection = context.getConnection();
    PreparedStatement pst = connection.prepareStatement(
        "UPDATE sessions SET is_bot = ? WHERE " + where);
    try {
      int i = 0;
      pst.setBoolean(++i, true);
      for (String bot : botList) {
        String likeValue = bot
            .replace("!", "!!")
            .replace("%", "!%")
            .replace("_", "!_")
            .replace("[", "![");
        pst.setString(++i, "%" + likeValue + "%");
      }
      pst.execute();
    } finally {
      pst.close();
    }
  }
}
