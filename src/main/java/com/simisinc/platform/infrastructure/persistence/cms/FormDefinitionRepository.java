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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves database-backed form configurations (issue #409) -- the admin-editable
 * alternative to a {@code form} widget's XML {@code <fields>} preference.
 *
 * @author SimIS Inc.
 */
public class FormDefinitionRepository {

  private static Log LOG = LogFactory.getLog(FormDefinitionRepository.class);

  private static final String TABLE_NAME = "form_definitions";
  private static final String[] PRIMARY_KEY = new String[] { "form_definition_id" };

  public static List<FormDefinition> findAll() {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        null,
        new DataConstraints().setDefaultColumnToSortBy("name").setUseCount(false),
        FormDefinitionRepository::buildRecord);
    return (List<FormDefinition>) result.getRecords();
  }

  public static FormDefinition findById(long id) {
    if (id == -1) {
      return null;
    }
    return (FormDefinition) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("form_definition_id = ?", id),
        FormDefinitionRepository::buildRecord);
  }

  public static FormDefinition findByUniqueId(String uniqueId) {
    if (StringUtils.isBlank(uniqueId)) {
      return null;
    }
    return (FormDefinition) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("unique_id = ?", uniqueId.trim()),
        FormDefinitionRepository::buildRecord);
  }

  public static FormDefinition save(FormDefinition record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static FormDefinition add(FormDefinition record) {
    SqlUtils insertValues = new SqlUtils()
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .addIfExists("title", StringUtils.trimToNull(record.getTitle()))
        .addIfExists("subtitle", StringUtils.trimToNull(record.getSubtitle()))
        .addIfExists("button_name", StringUtils.trimToNull(record.getButtonName()))
        .addIfExists("success_title", StringUtils.trimToNull(record.getSuccessTitle()))
        .addIfExists("success_message", StringUtils.trimToNull(record.getSuccessMessage()))
        .addIfExists("email_to", StringUtils.trimToNull(record.getEmailTo()))
        .add("use_captcha", record.getUseCaptcha())
        .add("check_for_spam", record.getCheckForSpam())
        .add("enabled", record.getEnabled())
        .add("send_confirmation_to_submitter", record.getSendConfirmationToSubmitter())
        .addIfExists("confirmation_subject", StringUtils.trimToNull(record.getConfirmationSubject()))
        .addIfExists("confirmation_message", StringUtils.trimToNull(record.getConfirmationMessage()))
        .add("created_by", record.getCreatedBy(), -1)
        .add("modified_by", record.getModifiedBy(), -1);
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static FormDefinition update(FormDefinition record) {
    SqlUtils updateValues = new SqlUtils()
        .add("unique_id", StringUtils.trimToNull(record.getUniqueId()))
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("title", StringUtils.trimToNull(record.getTitle()))
        .add("subtitle", StringUtils.trimToNull(record.getSubtitle()))
        .add("button_name", StringUtils.trimToNull(record.getButtonName()))
        .add("success_title", StringUtils.trimToNull(record.getSuccessTitle()))
        .add("success_message", StringUtils.trimToNull(record.getSuccessMessage()))
        .add("email_to", StringUtils.trimToNull(record.getEmailTo()))
        .add("use_captcha", record.getUseCaptcha())
        .add("check_for_spam", record.getCheckForSpam())
        .add("enabled", record.getEnabled())
        .add("send_confirmation_to_submitter", record.getSendConfirmationToSubmitter())
        .add("confirmation_subject", StringUtils.trimToNull(record.getConfirmationSubject()))
        .add("confirmation_message", StringUtils.trimToNull(record.getConfirmationMessage()))
        .add("modified_by", record.getModifiedBy(), -1)
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("form_definition_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  /**
   * Deletes a form definition and, in the same transaction, every field that belongs to it.
   * form_fields.form_definition_id has no DB-level ON DELETE CASCADE, so the cascade is performed
   * here explicitly -- the same pattern MenuTabRepository#remove uses for menu_items and
   * MailingListRepository#remove uses for mailing_list_members. Fields have no independent meaning
   * without their parent form, and form_data submissions are matched by form_unique_id (a string),
   * never by a foreign key to this table, so a deletion here can never be blocked by or orphan prior
   * submissions.
   */
  public static boolean remove(FormDefinition record) {
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        // Delete the references
        FormFieldRepository.removeAll(connection, record);
        // Delete the record
        DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("form_definition_id = ?", record.getId()));
        // Finish transaction
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  private static FormDefinition buildRecord(ResultSet rs) {
    try {
      FormDefinition record = new FormDefinition();
      record.setId(rs.getLong("form_definition_id"));
      record.setUniqueId(rs.getString("unique_id"));
      record.setName(rs.getString("name"));
      record.setTitle(rs.getString("title"));
      record.setSubtitle(rs.getString("subtitle"));
      record.setButtonName(rs.getString("button_name"));
      record.setSuccessTitle(rs.getString("success_title"));
      record.setSuccessMessage(rs.getString("success_message"));
      record.setEmailTo(rs.getString("email_to"));
      record.setUseCaptcha(rs.getBoolean("use_captcha"));
      record.setCheckForSpam(rs.getBoolean("check_for_spam"));
      record.setEnabled(rs.getBoolean("enabled"));
      record.setSendConfirmationToSubmitter(rs.getBoolean("send_confirmation_to_submitter"));
      record.setConfirmationSubject(rs.getString("confirmation_subject"));
      record.setConfirmationMessage(rs.getString("confirmation_message"));
      record.setCreatedBy(rs.getLong("created_by"));
      record.setModifiedBy(rs.getLong("modified_by"));
      record.setCreated(rs.getTimestamp("created"));
      record.setModified(rs.getTimestamp("modified"));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
