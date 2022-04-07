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

package com.simisinc.platform.infrastructure.persistence.mailinglists;

import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.mailinglists.Email;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.database.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 3/25/19 9:10 PM
 */
public class MailingListMemberRepository {

  private static Log LOG = LogFactory.getLog(MailingListMemberRepository.class);

  private static String TABLE_NAME = "mailing_list_members";
  private static String JOIN =
      "LEFT JOIN emails ON (mailing_list_members.email_id = emails.email_id) " +
      "LEFT JOIN mailing_lists ON (mailing_list_members.list_id = mailing_lists.list_id)";
  private static String PRIMARY_KEY[] = new String[]{"member_id"};

  public static void addEmailToList(Email email, MailingList mailingList) {
    // Determine if the email is already listed
    SqlUtils insertValues = new SqlUtils()
        .add("list_id", mailingList.getId())
        .add("email_id", email.getId())
        .addIfExists("created_by", email.getCreatedBy(), -1)
        .addIfExists("modified_by", email.getCreatedBy(), -1);
    long memberId = DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY);
    if (memberId > -1) {
      // New member - Update the related count
      String set = "member_count = member_count + 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    } else {
      // Make sure email is set to subscribed
      SqlUtils updateValues = new SqlUtils()
          .add("unsubscribed", (Timestamp) null)
          .add("modified", new Timestamp(System.currentTimeMillis()))
          .addIfExists("modified_by", email.getModifiedBy(), -1)
          .add("is_valid", true);
      SqlUtils where = new SqlUtils()
          .add("list_id = ?", mailingList.getId())
          .add("email_id = ?", email.getId());
      DB.update(TABLE_NAME, updateValues, where);
    }
  }

  public static void remove(Email email, MailingList mailingList) {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("email_id = ?", email.getId());
    deleteWhere.add("list_id = ?", mailingList.getId());
    int count = DB.deleteFrom(TABLE_NAME, deleteWhere);
    if (count > 0) {
      // Update the related count
      String set = "member_count = member_count - 1";
      SqlUtils where = new SqlUtils().add("list_id = ?", mailingList.getId());
      DB.update("mailing_lists", set, where);
    }
  }

  public static void removeAll(Connection connection, MailingList mailingList) throws SQLException {
    SqlUtils deleteWhere = new SqlUtils();
    deleteWhere.add("list_id = ?", mailingList.getId());
    DB.deleteFrom(connection, TABLE_NAME, deleteWhere);
  }

  public static void unsubscribe(MailingList mailingList, Email email, User user) {
    // Make sure email is set to unsubscribed
    SqlUtils updateValues = new SqlUtils()
        .add("unsubscribed", new Timestamp(System.currentTimeMillis()))
        .add("unsubscribed_by", user.getId())
        .add("modified", new Timestamp(System.currentTimeMillis()))
        .add("modified_by", user.getId())
        .add("is_valid", false);
    SqlUtils where = new SqlUtils()
        .add("list_id = ?", mailingList.getId())
        .add("email_id = ?", email.getId());
    DB.update(TABLE_NAME, updateValues, where);
  }

  public static void export(MailingListMemberSpecification specification, DataConstraints constraints, File file) {
    SqlUtils selectFields = new SqlUtils()
        .addNames(
            "mailing_lists.name AS list",
            "email",
            "first_name",
            "last_name",
            "organization",
            "mailing_list_members.created AS subscribed",
            "mailing_list_members.unsubscribed AS unsubscribed",
            "emails.unsubscribed AS ref_unsubscribed",
            "is_valid");
    SqlJoins joins = new SqlJoins().add(JOIN);
    SqlUtils where = new SqlUtils();
    // Use the specification to filter results
    if (specification != null) {
      if (specification.getMailingListId() > -1) {
        where.add("mailing_list_members.list_id = ?", specification.getMailingListId());
      }
    }
    if (constraints == null) {
      constraints = new DataConstraints();
    }
    constraints.setDefaultColumnToSortBy("mailing_list_members.created");
    DB.exportToCsvAllFrom(TABLE_NAME, selectFields, joins, where, null, constraints, file);
  }
}
