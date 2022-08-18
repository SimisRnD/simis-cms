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

package com.simisinc.platform.infrastructure.persistence.cms;

import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import com.simisinc.platform.domain.model.cms.WebSearch;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Persists and retrieves web search objects
 *
 * @author matt rajkowski
 * @created 3/5/2021 2:00 PM
 */
public class WebSearchRepository {

  private static Log LOG = LogFactory.getLog(WebSearchRepository.class);

  private static String TABLE_NAME = "web_searches";
  private static String[] PRIMARY_KEY = new String[]{"search_id"};


  public static WebSearch save(WebSearch record) {
    return add(record);
  }

  private static WebSearch add(WebSearch record) {
    SqlUtils insertValues = new SqlUtils()
        .add("page_path", record.getPagePath(), 255)
        .add("query", record.getQuery(), 255)
        .add("ip_address", record.getIpAddress())
        .add("session_id", record.getSessionId())
        .add("is_logged_in", record.getIsLoggedIn());
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static boolean remove(WebSearch record) {
    try {
      try (Connection connection = DB.getConnection();
           PreparedStatement pst = createPreparedStatementForDelete(connection, record)) {
        pst.execute();
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    LOG.error("The delete failed!");
    return false;
  }

  private static PreparedStatement createPreparedStatementForDelete(Connection connection, WebSearch record) throws SQLException {
    String SQL_QUERY =
        "DELETE FROM web_searches " +
            "WHERE search_id = ?";
    int i = 0;
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(++i, record.getId());
    return pst;
  }
}
