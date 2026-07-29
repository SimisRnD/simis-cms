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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.domain.model.mailinglists.MailingListHistory;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.SqlUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Persists and retrieves mailing list send batch header records.
 *
 * @author SimIS Inc.
 */
public class MailingListHistoryRepository {

  private static Log LOG = LogFactory.getLog(MailingListHistoryRepository.class);

  private static String TABLE_NAME = "mailing_list_history";
  private static String[] PRIMARY_KEY = new String[] { "history_id" };

  public static MailingListHistory add(Connection connection, MailingListHistory record) throws SQLException {
    SqlUtils insertValues = new SqlUtils()
        .add("list_id", record.getListId())
        .add("created_by", record.getCreatedBy(), -1)
        .add("service", record.getService())
        .add("email_count", record.getEmailCount())
        .addIfExists("subject", record.getSubject())
        .add("blog_post_id", record.getBlogPostId(), -1);
    record.setId(DB.insertInto(connection, TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  public static MailingListHistory findById(long id) {
    if (id == -1) {
      return null;
    }
    return (MailingListHistory) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("history_id = ?", id),
        MailingListHistoryRepository::buildRecord);
  }

  private static MailingListHistory buildRecord(ResultSet rs) {
    try {
      MailingListHistory record = new MailingListHistory();
      record.setId(rs.getLong("history_id"));
      record.setListId(rs.getLong("list_id"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setService(rs.getString("service"));
      record.setEmailCount(rs.getInt("email_count"));
      record.setSubject(rs.getString("subject"));
      record.setBlogPostId(DB.getLong(rs, "blog_post_id", -1));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
