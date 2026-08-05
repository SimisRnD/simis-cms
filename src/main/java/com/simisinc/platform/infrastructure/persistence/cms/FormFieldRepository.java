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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.FormDefinition;
import com.simisinc.platform.domain.model.cms.FormField;
import com.simisinc.platform.infrastructure.database.AutoRollback;
import com.simisinc.platform.infrastructure.database.AutoStartTransaction;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.database.DataResult;
import com.simisinc.platform.infrastructure.database.SqlUtils;

/**
 * Persists and retrieves the fields belonging to a database-backed {@link FormDefinition} (issue
 * #409).
 *
 * @author SimIS Inc.
 */
public class FormFieldRepository {

  private static Log LOG = LogFactory.getLog(FormFieldRepository.class);

  private static final String TABLE_NAME = "form_fields";
  private static final String[] PRIMARY_KEY = new String[] { "form_field_id" };

  public static FormField findById(long id) {
    if (id == -1) {
      return null;
    }
    return (FormField) DB.selectRecordFrom(
        TABLE_NAME,
        new SqlUtils().add("form_field_id = ?", id),
        FormFieldRepository::buildRecord);
  }

  public static List<FormField> findAllByFormDefinitionId(long formDefinitionId) {
    DataResult result = DB.selectAllFrom(
        TABLE_NAME,
        new SqlUtils().add("form_definition_id = ?", formDefinitionId),
        new DataConstraints().setDefaultColumnToSortBy("field_order, form_field_id").setUseCount(false),
        FormFieldRepository::buildRecord);
    return (List<FormField>) result.getRecords();
  }

  public static FormField save(FormField record) {
    if (record.getId() > -1) {
      return update(record);
    }
    return add(record);
  }

  private static FormField add(FormField record) {
    SqlUtils insertValues = new SqlUtils()
        .add("form_definition_id", record.getFormDefinitionId())
        // Omit field_order entirely when the caller left it at the domain default (-1) rather than
        // binding SQL NULL, so the column's own "DEFAULT 100" applies and a newly-added field sorts
        // to the end -- matching how menu_items.item_order/menu_tabs.tab_order behave.
        .addIfExists("field_order", record.getFieldOrder(), -1)
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("label", StringUtils.trimToNull(record.getLabel()))
        .addIfExists("field_type", StringUtils.trimToNull(record.getType()))
        .add("required", record.isRequired())
        .addIfExists("placeholder", StringUtils.trimToNull(record.getPlaceholder()))
        .addIfExists("default_value", StringUtils.trimToNull(record.getDefaultValue()))
        .addIfExists("options", formatOptions(record.getListOfOptions()));
    record.setId(DB.insertInto(TABLE_NAME, insertValues, PRIMARY_KEY));
    if (record.getId() == -1) {
      LOG.error("An id was not set!");
      return null;
    }
    return record;
  }

  private static FormField update(FormField record) {
    SqlUtils updateValues = new SqlUtils()
        .add("field_order", record.getFieldOrder())
        .add("name", StringUtils.trimToNull(record.getName()))
        .add("label", StringUtils.trimToNull(record.getLabel()))
        .add("field_type", StringUtils.trimToNull(record.getType()))
        .add("required", record.isRequired())
        .add("placeholder", StringUtils.trimToNull(record.getPlaceholder()))
        .add("default_value", StringUtils.trimToNull(record.getDefaultValue()))
        .add("options", formatOptions(record.getListOfOptions()))
        .add("modified", new Timestamp(System.currentTimeMillis()));
    SqlUtils where = new SqlUtils()
        .add("form_field_id = ?", record.getId());
    if (DB.update(TABLE_NAME, updateValues, where)) {
      return record;
    }
    LOG.error("The update failed!");
    return null;
  }

  public static boolean remove(FormField record) {
    return (DB.deleteFrom(TABLE_NAME, new SqlUtils().add("form_field_id = ?", record.getId())) > 0);
  }

  private static PreparedStatement createPreparedStatementNextFieldOrder(Connection connection, long formDefinitionId) throws SQLException {
    String SQL_QUERY =
        "SELECT max(field_order) " +
            "FROM form_fields " +
            "WHERE form_definition_id = ?";
    PreparedStatement pst = connection.prepareStatement(SQL_QUERY);
    pst.setLong(1, formDefinitionId);
    return pst;
  }

  /**
   * The next field_order for a new field on the given form (issue #409), mirroring
   * MenuItemRepository#getNextTabOrder/MenuTabRepository#getNextTabOrder exactly -- one more than the
   * current max, defaulting to 1 when the form has no fields yet. SaveFormFieldCommand calls this for
   * every newly-created field so add() always receives an explicit, distinct order rather than
   * relying on the field_order column's own "DEFAULT 100", which produces duplicate values (and thus
   * an order that depends only on insertion order, not intent) whenever two or more fields are added
   * without an intervening drag-and-drop reorder.
   */
  public static int getNextFieldOrder(long formDefinitionId) {
    int maxOrder = 0;
    try (Connection connection = DB.getConnection();
        PreparedStatement pst = createPreparedStatementNextFieldOrder(connection, formDefinitionId);
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        maxOrder = rs.getInt(1) + 1;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return maxOrder;
  }

  public static void removeAll(Connection connection, FormDefinition record) throws SQLException {
    DB.deleteFrom(connection, TABLE_NAME, new SqlUtils().add("form_definition_id = ?", record.getId()));
  }

  /**
   * Persists a new display order for a form's fields in one transaction -- the drag-and-drop
   * reorder pattern this codebase already uses for menu items (SaveMenuTabCommand#updateTabOrder),
   * adapted to a single flat list since a form has only one level of fields (unlike the sitemap's
   * tabs-then-items). {@code orderedFieldIds} is the field id list in its new top-to-bottom order,
   * e.g. as parsed from a hidden input populated by a dragula drop handler. Each id is required to
   * already belong to {@code formDefinitionId} -- an id for a different form is skipped rather than
   * applied, so a stale or tampered request cannot reorder (or reveal the existence of) another
   * form's fields.
   */
  public static boolean reorderFields(long formDefinitionId, List<Long> orderedFieldIds) {
    if (orderedFieldIds == null || orderedFieldIds.isEmpty()) {
      return false;
    }
    try {
      try (Connection connection = DB.getConnection();
          AutoStartTransaction a = new AutoStartTransaction(connection);
          AutoRollback transaction = new AutoRollback(connection)) {
        int order = 10;
        for (Long fieldId : orderedFieldIds) {
          if (fieldId == null) {
            continue;
          }
          FormField field = findById(fieldId);
          if (field == null || field.getFormDefinitionId() != formDefinitionId) {
            continue;
          }
          SqlUtils updateValues = new SqlUtils().add("field_order", order);
          SqlUtils where = new SqlUtils().add("form_field_id = ?", fieldId);
          DB.update(connection, TABLE_NAME, updateValues, where);
          order += 10;
        }
        transaction.commit();
        return true;
      }
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return false;
  }

  /**
   * Serializes a field's options using the same comma-separated "key=value,key2=value2" format the
   * XML {@code <field list="..."/>} preference already uses (FormFieldCommand#parseFieldContent), so
   * a future admin-form save path can share one options format across both configuration sources.
   */
  private static String formatOptions(Map<String, String> options) {
    if (options == null || options.isEmpty()) {
      return null;
    }
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry : options.entrySet()) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(entry.getKey()).append("=").append(entry.getValue());
    }
    return sb.toString();
  }

  /**
   * The inverse of {@link #formatOptions}. An entry without an "=" is stored as both its own key and
   * value verbatim -- unlike FormFieldCommand's XML-preference parsing, this does not slugify a
   * bare option into an html-safe name, since the admin form builder (a later stage) is expected to
   * always submit explicit key/value pairs rather than relying on that XML-authoring convenience.
   */
  private static Map<String, String> parseOptions(String options) {
    if (StringUtils.isBlank(options)) {
      return null;
    }
    Map<String, String> optionsMap = new LinkedHashMap<>();
    for (String option : options.split(",")) {
      if (StringUtils.isBlank(option)) {
        continue;
      }
      int idx = option.indexOf('=');
      if (idx > -1) {
        optionsMap.put(option.substring(0, idx), option.substring(idx + 1));
      } else {
        optionsMap.put(option, option);
      }
    }
    return optionsMap;
  }

  private static FormField buildRecord(ResultSet rs) {
    try {
      FormField record = new FormField();
      record.setId(rs.getLong("form_field_id"));
      record.setFormDefinitionId(rs.getLong("form_definition_id"));
      record.setFieldOrder(rs.getInt("field_order"));
      record.setName(rs.getString("name"));
      record.setLabel(rs.getString("label"));
      record.setType(rs.getString("field_type"));
      record.setRequired(rs.getBoolean("required"));
      record.setPlaceholder(rs.getString("placeholder"));
      record.setDefaultValue(rs.getString("default_value"));
      record.setListOfOptions(parseOptions(rs.getString("options")));
      return record;
    } catch (SQLException se) {
      LOG.error("buildRecord", se);
      return null;
    }
  }
}
