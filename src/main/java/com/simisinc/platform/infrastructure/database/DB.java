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

package com.simisinc.platform.infrastructure.database;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.postgresql.util.PGInterval;

import com.simisinc.platform.domain.model.Entity;
import com.univocity.parsers.csv.CsvRoutines;
import com.univocity.parsers.csv.CsvWriterSettings;

/**
 * Functions to interface with the database and handle special cases
 *
 * @author matt rajkowski
 * @created 1/13/22 7:56 AM
 */
public class DB {

  private static Log LOG = LogFactory.getLog(DB.class);
  private static long LONG_QUERY_MS = 20;

  public static Connection getConnection() throws SQLException {
    return DataSource.getDataSource().getConnection();
  }

  private static PreparedStatement createPreparedStatement(Connection connection, String sqlQuery, SqlUtils where)
      throws SQLException {
    PreparedStatement pst = connection.prepareStatement(sqlQuery);
    prepareValues(pst, where);
    return pst;
  }

  private static PreparedStatement createPreparedStatement(Connection connection, String sqlQuery,
      SqlUtils insertValues, String[] primaryKey) throws SQLException {
    PreparedStatement pst = connection.prepareStatement(sqlQuery, primaryKey);
    prepareValues(pst, insertValues);
    return pst;
  }

  private static int prepareValues(PreparedStatement pst, SqlUtils sqlUtils) throws SQLException {
    return prepareValues(pst, sqlUtils, 0);
  }

  private static int prepareValues(PreparedStatement pst, SqlUtils sqlUtils, int fieldIdx) throws SQLException {
    if (sqlUtils == null || sqlUtils.getValues().isEmpty()) {
      return fieldIdx;
    }
    for (SqlValue sqlValue : sqlUtils.getValues()) {
      if (!sqlValue.hasValue()) {
        continue;
      }
      if (sqlValue.getSqlType() == Types.VARCHAR) {
        if (sqlValue.getStringValues() != null) {
          for (String value : sqlValue.getStringValues()) {
            pst.setString(++fieldIdx, value);
          }
        } else {
          pst.setString(++fieldIdx, sqlValue.getStringValue());
        }
      } else if (sqlValue.getSqlType() == Types.BIGINT) {
        if (sqlValue.getLongValues() != null) {
          for (Long value : sqlValue.getLongValues()) {
            pst.setLong(++fieldIdx, value);
          }
        } else {
          if (sqlValue.isNull()) {
            pst.setNull(++fieldIdx, Types.BIGINT);
          } else {
            pst.setLong(++fieldIdx, sqlValue.getLongValue());
          }
        }
      } else if (sqlValue.getSqlType() == Types.INTEGER) {
        if (sqlValue.isNull()) {
          pst.setNull(++fieldIdx, Types.INTEGER);
        } else {
          pst.setInt(++fieldIdx, sqlValue.getIntValue());
        }
      } else if (sqlValue.getSqlType() == Types.DOUBLE) {
        if (sqlValue.isNull()) {
          pst.setNull(++fieldIdx, Types.DOUBLE);
        } else {
          pst.setDouble(++fieldIdx, sqlValue.getDoubleValue());
        }
      } else if (sqlValue.getSqlType() == Types.NUMERIC) {
        if (sqlValue.isNull()) {
          pst.setNull(++fieldIdx, Types.NUMERIC);
        } else {
          pst.setBigDecimal(++fieldIdx, sqlValue.getBigDecimalValue());
        }
      } else if (sqlValue.getSqlType() == Types.TIMESTAMP) {
        if (sqlValue.getTimestampValues() != null) {
          for (Timestamp value : sqlValue.getTimestampValues()) {
            pst.setTimestamp(++fieldIdx, value);
          }
        } else {
          if (sqlValue.isNull()) {
            pst.setNull(++fieldIdx, Types.TIMESTAMP);
          } else {
            pst.setTimestamp(++fieldIdx, sqlValue.getTimestampValue());
          }
        }
      } else if (sqlValue.getSqlType() == Types.JAVA_OBJECT) {
        // Set multiple java objects
        if (sqlValue.getObjectValues() != null) {
          for (Object value : sqlValue.getObjectValues()) {
            // Determine the type and value
            if (value instanceof String) {
              pst.setString(++fieldIdx, (String) value);
            } else if (value instanceof Integer) {
              pst.setInt(++fieldIdx, (Integer) value);
            } else if (value instanceof Long) {
              pst.setLong(++fieldIdx, (Long) value);
            } else if (value instanceof Double) {
              pst.setDouble(++fieldIdx, (Double) value);
            } else if (value instanceof Boolean) {
              pst.setBoolean(++fieldIdx, (Boolean) value);
            } else if (value instanceof Timestamp) {
              pst.setTimestamp(++fieldIdx, (Timestamp) value);
            } else if (value instanceof BigDecimal) {
              pst.setBigDecimal(++fieldIdx, (BigDecimal) value);
            } else {
              throw new SQLException("Type not implemented for " + value.getClass());
            }
          }
        }
      } else if (sqlValue.getSqlType() == Types.BOOLEAN) {
        if (sqlValue.isNull()) {
          pst.setNull(++fieldIdx, Types.BOOLEAN);
        } else {
          pst.setBoolean(++fieldIdx, sqlValue.getBooleanValue());
        }
      } else if (sqlValue.getSqlType() == Types.OTHER && sqlValue.getCastType() == SqlValue.INTERVAL_TYPE) {
        if (sqlValue.isNull()) {
          pst.setNull(++fieldIdx, Types.OTHER);
        } else {
          pst.setObject(++fieldIdx, new PGInterval(sqlValue.getStringValue()));
        }
      }
    }
    return fieldIdx;
  }

  public static long selectCountFrom(String tableName) {
    return selectCountFrom(tableName, null);
  }

  public static long selectCountFrom(String tableName, SqlUtils where) {
    return selectFunction("COUNT(*)", tableName, where);
  }

  public static long selectNextSequenceValue(String sequenceName) {
    long nextVal = -1;
    String sqlNextVal = "SELECT nextval('" + sequenceName + "')";
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sqlNextVal, null);
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        nextVal = rs.getLong(1);
      }
    } catch (SQLException se) {
      LOG.error(sqlNextVal);
      LOG.error("Next Sequence Value SQLException: " + se.getMessage());
    }
    return nextVal;
  }

  public static long resetSequence(String sequenceName, long value) {
    long nextVal = -1;
    String sqlRestartSequence = "ALTER SEQUENCE " + sequenceName + " RESTART WITH " + value;
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sqlRestartSequence, null)) {
      pst.execute();
    } catch (SQLException se) {
      LOG.error(sqlRestartSequence);
      LOG.error("Reset Sequence SQLException: " + se.getMessage());
    }
    return nextVal;
  }

  public static long selectFunction(String sql, String tableName, SqlUtils where) {
    // Construct the where clause
    StringBuilder whereSb = createWhereClause(where);
    long value = 0;
    String selectFunction = "SELECT " + sql + " FROM " + tableName + whereSb.toString();
    long startQueryTime = System.currentTimeMillis();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, selectFunction, where);
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        value = rs.getLong(1);
      }
    } catch (SQLException se) {
      LOG.error(selectFunction);
      LOG.error("Count SQLException: " + se.getMessage());
    }
    if (LOG.isDebugEnabled()) {
      long endQueryTime = System.currentTimeMillis();
      long totalTime = endQueryTime - startQueryTime;
      if (totalTime > LONG_QUERY_MS) {
        LOG.debug(selectFunction);
        LOG.debug("Query took " + totalTime + "ms");
      }
    }
    return value;
  }

  public static List<Long> selectFunctionAsLongList(String sqlFields, String tableName, SqlUtils where,
      SqlUtils orderBy) {

    // Prepare the query
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ").append(sqlFields).append(" ");
    sb.append("FROM ").append(tableName);
    sb.append(createWhereClause(where));

    // Apply sorting, paging
    if (orderBy != null) {
      sb.append(appendSortClause(orderBy, null));
    }
    String sql = sb.toString();

    // Construct the where clause
    List<Long> records = new ArrayList<>();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sql, where);
        ResultSet rs = pst.executeQuery()) {
      while (rs.next()) {
        long value = rs.getLong(1);
        records.add(value);
      }
    } catch (SQLException se) {
      LOG.error(sql);
      LOG.error("selectFunctionAsLongList SQLException: " + se.getMessage());
    }
    return records;
  }

  public static String selectStringValue(String sql, String tableName, SqlUtils where) {
    // Construct the where clause
    StringBuilder whereSb = createWhereClause(where);
    String value = null;
    String sqlCount = "SELECT " + sql + " FROM " + tableName + whereSb.toString();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sqlCount, where);
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        value = rs.getString(1);
      }
    } catch (SQLException se) {
      LOG.error(sqlCount);
      LOG.error("Count SQLException: " + se.getMessage());
    }
    return value;
  }

  public static Object selectRecordFrom(String tableName, SqlUtils where, Function<ResultSet, Entity> buildRecord) {
    return selectRecordFrom(tableName, null, null, where, buildRecord);
  }

  public static Object selectRecordFrom(String tableName, SqlUtils select, SqlJoins joins, SqlUtils where,
      Function<ResultSet, Entity> buildRecord) {
    // Construct the where clause
    StringBuilder joinsSb = createJoins(joins);
    StringBuilder whereSb = createWhereClause(where);

    // Prepare the query
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ").append(tableName).append(".*");
    if (select != null) {
      sb.append(createAdditionalSelectFields(select));
    }
    sb.append(" FROM ").append(tableName);
    sb.append(joinsSb);
    sb.append(whereSb);

    // Prepare the query
    long startQueryTime = System.currentTimeMillis();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sb.toString(), where);
        ResultSet rs = pst.executeQuery()) {
      if (rs.next()) {
        return (buildRecord.apply(rs));
      }
    } catch (SQLException se) {
      LOG.debug(sb.toString());
      LOG.error("SQLException: " + se.getMessage());
    } finally {
      if (LOG.isDebugEnabled()) {
        long endQueryTime = System.currentTimeMillis();
        long totalTime = endQueryTime - startQueryTime;
        if (totalTime > LONG_QUERY_MS) {
          LOG.debug(sb.toString());
          LOG.debug("Query took " + totalTime + "ms");
        }
      }
    }
    return null;
  }

  public static DataResult selectAllFrom(String tableName, SqlUtils where, DataConstraints constraints,
      Function<ResultSet, Entity> buildRecord) {
    return selectAllFrom(tableName, null, null, where, null, constraints, buildRecord);
  }

  public static DataResult selectAllFrom(String tableName, SqlJoins joins, SqlUtils where, DataConstraints constraints,
      Function<ResultSet, Entity> buildRecord) {
    return selectAllFrom(tableName, null, joins, where, null, constraints, buildRecord);
  }

  public static DataResult selectAllFrom(String tableName, SqlUtils select, SqlUtils where, SqlUtils orderBy,
      DataConstraints constraints, Function<ResultSet, Entity> buildRecord) {
    return selectAllFrom(tableName, select, null, where, orderBy, constraints, buildRecord);
  }

  public static DataResult selectAllFrom(String tableName, SqlUtils select, SqlJoins joins, SqlUtils where,
      SqlUtils orderBy, DataConstraints constraints, Function<ResultSet, Entity> buildRecord) {

    // Determine the max records based on the where conditions
    DataResult dataResult = new DataResult();

    StringBuilder joinsSb = createJoins(joins);
    StringBuilder whereSb = createWhereClause(where);

    // Count the max records
    if (constraints == null || constraints.useCount()) {
      long recordCount = 0;
      String sqlCount = "SELECT COUNT(*) FROM " + tableName + joinsSb.toString() + whereSb.toString();
      long startQueryTime = System.currentTimeMillis();
      try (Connection connection = getConnection();
          PreparedStatement pst = createPreparedStatement(connection, sqlCount, where);
          ResultSet rs = pst.executeQuery()) {
        if (rs.next()) {
          recordCount = rs.getLong(1);
        }
      } catch (SQLException se) {
        LOG.error(sqlCount);
        LOG.error("Count SQLException: " + se.getMessage());
      }
      if (LOG.isDebugEnabled()) {
        long endQueryTime = System.currentTimeMillis();
        long totalTime = endQueryTime - startQueryTime;
        if (totalTime > LONG_QUERY_MS) {
          LOG.debug(sqlCount);
          LOG.debug("Query took " + totalTime + "ms");
        }
      }
      dataResult.setTotalRecordCount(recordCount);
      if (constraints != null) {
        constraints.setTotalRecordCount(recordCount);
      }
      if (recordCount == 0) {
        List<Entity> records = new ArrayList<>();
        dataResult.setRecords(records);
        return dataResult;
      }
    }

    // Prepare the query
    StringBuilder sb = new StringBuilder();
    sb.append("SELECT ").append(tableName).append(".*");
    if (select != null) {
      sb.append(createAdditionalSelectFields(select));
    }
    sb.append(" FROM ").append(tableName);
    sb.append(joinsSb);
    sb.append(whereSb);

    // Apply sorting, paging
    if (constraints != null) {
      if (constraints.hasSortOrder()) {
        sb.append(appendSortClause(orderBy, constraints));
      }

      // Apply the constraints
      if (constraints.getPageNumber() > 1) {
        sb.append(" OFFSET ").append((constraints.getPageNumber() - 1) * constraints.getPageSize());
      }
      if (constraints.getPageSize() > 0) {
        sb.append(" LIMIT ").append(constraints.getPageSize());
      }
    }

    // Get a connection, execute the query, return the data
    List<Entity> records = null;
    long startQueryTime = System.currentTimeMillis();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sb.toString(), select, where, orderBy);
        ResultSet rs = pst.executeQuery()) {
      records = new ArrayList<>();
      while (rs.next()) {
        records.add(buildRecord.apply(rs));
      }
    } catch (SQLException se) {
      LOG.debug(sb.toString());
      LOG.error("List SQLException: " + se.getMessage());
    }
    if (LOG.isDebugEnabled()) {
      long endQueryTime = System.currentTimeMillis();
      long totalTime = endQueryTime - startQueryTime;
      if (totalTime > LONG_QUERY_MS) {
        LOG.debug(sb.toString());
        LOG.debug("Query took " + totalTime + "ms");
      }
    }
    dataResult.setRecords(records);
    return dataResult;
  }

  private static StringBuilder createAdditionalSelectFields(SqlUtils select) {
    StringBuilder sb = new StringBuilder();
    if (select != null && !select.getValues().isEmpty()) {
      for (SqlValue sqlValue : select.getValues()) {
        sb.append(", ").append(sqlValue.getFieldOrClause());
      }
    }
    return sb;
  }

  private static StringBuilder createSelectFields(SqlUtils select) {
    boolean foundFirst = false;
    StringBuilder sb = new StringBuilder();
    if (select != null && !select.getValues().isEmpty()) {
      for (SqlValue sqlValue : select.getValues()) {
        if (foundFirst) {
          sb.append(", ");
        }
        sb.append(sqlValue.getFieldOrClause());
        if (!foundFirst) {
          foundFirst = true;
        }
      }
    }
    return sb;
  }

  private static StringBuilder createJoins(SqlJoins joins) {
    StringBuilder joinsSb = new StringBuilder();
    if (joins != null && !joins.getValues().isEmpty()) {
      for (String join : joins.getValues()) {
        joinsSb.append(" ");
        joinsSb.append(join);
      }
    }
    return joinsSb;
  }

  private static StringBuilder createWhereClause(SqlUtils where) {
    StringBuilder whereSb = new StringBuilder();
    if (where != null && !where.getValues().isEmpty()) {
      whereSb.append(" WHERE ");
      boolean isFirst = true;
      for (SqlValue sqlValue : where.getValues()) {
        if (!isFirst) {
          whereSb.append(" AND ");
        }
        // Add the clause, but make sure to isolate the ANDs with ORs
        String clause = sqlValue.getFieldOrClause();
        if (clause.contains(" OR ") && !clause.contains("(")) {
          whereSb.append("(").append(clause).append(")");
        } else {
          whereSb.append(clause);
        }
        if (isFirst) {
          isFirst = false;
        }
      }
    }
    return whereSb;
  }

  private static StringBuilder appendSortClause(SqlUtils orderBy, DataConstraints constraints) {
    StringBuilder sb = new StringBuilder(" ORDER BY ");
    if (orderBy != null && !orderBy.getValues().isEmpty()) {
      // Use the SqlValue when a prepared statement is needed
      boolean isFirst = true;
      for (SqlValue sqlValue : orderBy.getValues()) {
        if (!isFirst) {
          sb.append(", ");
        }
        sb.append(sqlValue.getFieldOrClause());
        if (isFirst) {
          isFirst = false;
        }
      }
    } else if (constraints.getColumnsToSortBy() != null) {
      // Use the specified columns
      int idx = 0;
      for (String a : constraints.getColumnsToSortBy()) {
        if (idx > 0) {
          sb.append(", ");
        }
        sb.append(a);
        if (constraints.getSortOrder() != null && idx < constraints.getSortOrder().length) {
          sb.append(" ").append(constraints.getSortOrder()[idx]);
        }
        ++idx;
      }
    } else if (constraints.getDefaultColumnToSortBy() != null) {
      // Use the default columns
      sb.append(constraints.getDefaultColumnToSortBy());
    }
    return sb;
  }

  public static long executeInsert(PreparedStatement pst) throws SQLException {
    long id = -1;
    if (pst.executeUpdate() > 0) {
      ResultSet generatedKeys = pst.getGeneratedKeys();
      if (generatedKeys.next()) {
        id = generatedKeys.getLong(1);
      }
      generatedKeys.close();
    }
    if (id == -1) {
      throw new SQLException("Id not found");
    }
    return id;
  }

  public static long insertInto(String tableName, SqlUtils sqlUtils, String onConflict) {
    return insertInto(tableName, sqlUtils, null, onConflict);
  }

  public static long insertInto(String tableName, SqlUtils sqlUtils, String[] primaryKey) {
    return insertInto(tableName, sqlUtils, primaryKey, null);
  }

  public static long insertInto(String tableName, SqlUtils insertValues, String[] primaryKey, String onConflict) {
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatementForInsert(connection, tableName, insertValues, primaryKey,
            onConflict)) {
      return executeInsert(pst);
    } catch (SQLException se) {
      LOG.error("SQLException: " + se.getMessage());
    }
    return -1;
  }

  public static long insertInto(Connection connection, String tableName, SqlUtils insertValues, String[] primaryKey)
      throws SQLException {
    try (PreparedStatement pst = createPreparedStatementForInsert(connection, tableName, insertValues, primaryKey,
        null)) {
      return executeInsert(pst);
    } catch (SQLException se) {
      throw new SQLException("insertInto record failed [" + tableName + "]: " + se.getMessage(), se);
    }
  }

  private static PreparedStatement createPreparedStatementForInsert(Connection connection, String tableName,
      SqlUtils insertValues, String[] primaryKey, String onConflict) throws SQLException {
    String SQL_INSERT_QUERY = "INSERT INTO " + tableName + " " +
        createInsertValues(insertValues) + (onConflict != null ? " " + onConflict : "");
    return createPreparedStatement(connection, SQL_INSERT_QUERY, insertValues, primaryKey);
  }

  private static String createInsertValues(SqlUtils insertValues) {
    StringBuilder fieldNamesSb = new StringBuilder();
    StringBuilder valuesSb = new StringBuilder();
    boolean isFirst = true;
    for (SqlValue sqlValue : insertValues.getValues()) {
      if (!isFirst) {
        fieldNamesSb.append(", ");
        valuesSb.append(", ");
      }
      fieldNamesSb.append(sqlValue.getFieldOrClause());
      if (sqlValue.hasValue()) {
        valuesSb.append("?");
        if (sqlValue.getCastType() == SqlValue.JSONB_TYPE) {
          valuesSb.append("::jsonb");
        }
      } else {
        valuesSb.append(sqlValue.getStringValue());
      }
      if (isFirst) {
        isFirst = false;
      }
    }
    return "(" + fieldNamesSb.toString() + ") VALUES (" + valuesSb.toString() + ")";
  }

  public static boolean update(String tableName, SqlUtils updateValues, SqlUtils where) {
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatementForUpdate(connection, tableName, updateValues, where)) {
      if (pst.executeUpdate() > 0) {
        return true;
      }
    } catch (SQLException se) {
      LOG.error("Update SQLException: [" + tableName + "]: " + se.getMessage());
    }
    return false;
  }

  public static boolean update(Connection connection, String tableName, SqlUtils updateValues, SqlUtils where)
      throws SQLException {
    try (PreparedStatement pst = createPreparedStatementForUpdate(connection, tableName, updateValues, where)) {
      if (pst.executeUpdate() > 0) {
        return true;
      }
    } catch (SQLException se) {
      throw new SQLException("Update SQLException: [" + tableName + "]: " + se.getMessage(), se);
    }
    return false;
  }

  public static boolean update(String tableName, String statement, SqlUtils where) {
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatementForUpdate(connection, tableName, statement, where)) {
      if (pst.executeUpdate() > 0) {
        return true;
      }
    } catch (SQLException se) {
      LOG.error("Update SQLException: [" + tableName + "]: " + se.getMessage());
    }
    return false;
  }

  private static PreparedStatement createPreparedStatementForUpdate(Connection connection, String tableName,
      String statement, SqlUtils where) throws SQLException {
    if (statement == null) {
      return null;
    }
    String SQL_UPDATE_QUERY = "UPDATE " + tableName +
        " SET " +
        statement +
        createWhereClause(where);
    //LOG.debug("SQL_UPDATE_QUERY: " + SQL_UPDATE_QUERY);
    return createPreparedStatement(connection, SQL_UPDATE_QUERY, where);
  }

  private static PreparedStatement createPreparedStatementForUpdate(Connection connection, String tableName,
      SqlUtils updateValues, SqlUtils where) throws SQLException {
    if (updateValues == null) {
      return null;
    }
    String SQL_UPDATE_QUERY = "UPDATE " + tableName +
        " SET " +
        createUpdateValues(updateValues) +
        createWhereClause(where);
    if (LOG.isDebugEnabled()) {
      LOG.debug("SQL_UPDATE_QUERY: " + SQL_UPDATE_QUERY);
    }
    return createPreparedStatement(connection, SQL_UPDATE_QUERY, updateValues, where, null);
  }

  private static PreparedStatement createPreparedStatement(Connection connection, String sqlQuery,
      SqlUtils selectOrUpdate, SqlUtils where, SqlUtils orderBy) throws SQLException {
    PreparedStatement pst = connection.prepareStatement(sqlQuery);
    int fieldIdx = prepareValues(pst, selectOrUpdate);
    fieldIdx = prepareValues(pst, where, fieldIdx);
    prepareValues(pst, orderBy, fieldIdx);
    return pst;
  }

  private static String createUpdateValues(SqlUtils sqlUtils) {
    StringBuilder fieldNamesSb = new StringBuilder();
    boolean isFirst = true;
    for (SqlValue sqlValue : sqlUtils.getValues()) {
      if (!isFirst) {
        fieldNamesSb.append(", ");
      }
      if (sqlValue.hasValue()) {
        String updateString = sqlValue.getFieldOrClause();
        fieldNamesSb.append(updateString);
        if (!updateString.contains("?")) {
          fieldNamesSb.append(" = ?");
        }
        if (sqlValue.getCastType() == SqlValue.JSONB_TYPE) {
          fieldNamesSb.append("::jsonb");
        }
      } else {
        if (sqlValue.getFieldOrClause().contains("=")) {
          fieldNamesSb.append(sqlValue.getFieldOrClause());
        } else {
          fieldNamesSb.append(sqlValue.getFieldOrClause()).append(" = ").append(sqlValue.getStringValue());
        }
      }
      if (isFirst) {
        isFirst = false;
      }
    }
    return fieldNamesSb.toString();
  }

  public static int deleteFrom(String tableName, SqlUtils where) {
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatementForDelete(connection, tableName, where)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      LOG.error("SQLException deleteFrom failed: " + se.getMessage());
    }
    return -1;
  }

  public static int deleteFrom(Connection connection, String tableName, SqlUtils where) throws SQLException {
    try (PreparedStatement pst = createPreparedStatementForDelete(connection, tableName, where)) {
      return pst.executeUpdate();
    } catch (SQLException se) {
      throw new SQLException("deleteFrom failed [" + tableName + "] " + se.getMessage(), se);
    }
  }

  private static PreparedStatement createPreparedStatementForDelete(Connection connection, String tableName,
      SqlUtils where) throws SQLException {

    // Construct the where clause
    StringBuilder whereSb = createWhereClause(where);

    // Prepare the query
    StringBuilder sb = new StringBuilder();
    sb.append("DELETE FROM ").append(tableName);
    sb.append(whereSb);

    return createPreparedStatement(connection, sb.toString(), where);
  }

  public static long getLong(ResultSet rs, String field, long valueWhenNull) throws SQLException {
    long value = rs.getLong(field);
    if (rs.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  public static double getDouble(ResultSet rs, String field, double valueWhenNull) throws SQLException {
    double value = rs.getDouble(field);
    if (rs.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  public static int getInt(ResultSet rs, String field, int valueWhenNull) throws SQLException {
    int value = rs.getInt(field);
    if (rs.wasNull()) {
      return valueWhenNull;
    }
    return value;
  }

  public static String getPeriod(ResultSet rs, String field) throws SQLException {
    PGInterval pgi = (PGInterval) rs.getObject(field);
    if (rs.wasNull()) {
      return null;
    }
    // convert to ISO 8601
    StringBuilder sb = new StringBuilder("P");
    if (pgi.getYears() > 0) {
      sb.append(pgi.getYears()).append("Y");
    }
    if (pgi.getMonths() > 0) {
      sb.append(pgi.getMonths()).append("M");
    }
    if (pgi.getDays() > 0) {
      sb.append(pgi.getDays()).append("D");
    }
    // Determine if just the period is shown
    if (sb.length() > 1 && pgi.getHours() == 0 && pgi.getMinutes() == 0 && pgi.getWholeSeconds() == 0) {
      return sb.toString();
    }
    // Show the time, even if it's 0
    sb.append("T");
    boolean foundTime = false;
    if (pgi.getHours() > 0) {
      foundTime = true;
      sb.append(pgi.getHours()).append("H");
    }
    if (pgi.getMinutes() > 0) {
      foundTime = true;
      sb.append(pgi.getMinutes()).append("M");
    }
    // Show 0 if there's no other time
    if (!foundTime || pgi.getWholeSeconds() > 0) {
      sb.append(pgi.getWholeSeconds()).append("S");
    }
    return sb.toString();
  }

  public static void exportToCsvAllFrom(String tableName, SqlUtils selectFields, SqlJoins joins, SqlUtils where,
      SqlUtils orderBy, DataConstraints constraints, File file) {

    StringBuilder joinsSb = createJoins(joins);
    StringBuilder whereSb = createWhereClause(where);

    // Prepare the query
    StringBuilder sb = new StringBuilder();
    if (selectFields == null) {
      sb.append("SELECT ").append(tableName).append(".*");
    } else {
      sb.append("SELECT ").append(createSelectFields(selectFields));
    }
    sb.append(" FROM ").append(tableName);
    sb.append(joinsSb);
    sb.append(whereSb);

    // Apply sorting, paging
    if (constraints != null) {
      if (constraints.hasSortOrder()) {
        sb.append(appendSortClause(orderBy, constraints));
      }

      // Apply the constraints
      if (constraints.getPageNumber() > 1) {
        sb.append(" OFFSET ").append((constraints.getPageNumber() - 1) * constraints.getPageSize());
      }
      if (constraints.getPageSize() > 0) {
        sb.append(" LIMIT ").append(constraints.getPageSize());
      }
    }

    // Prepare the writer
    CsvWriterSettings writerSettings = new CsvWriterSettings();
    writerSettings.getFormat().setLineSeparator("\r\n");
    writerSettings.getFormat().setDelimiter(',');
    writerSettings.setQuoteAllFields(true);
    writerSettings.setHeaderWritingEnabled(true);
    //    writerSettings.setHeaders("email", "created", "master_unsub", "unsubscribed", "is_valid");
    CsvRoutines routines = new CsvRoutines(writerSettings);

    // Get a connection, execute the query, return the data
    long startQueryTime = System.currentTimeMillis();
    try (Connection connection = getConnection();
        PreparedStatement pst = createPreparedStatement(connection, sb.toString(), selectFields, where, orderBy);
        ResultSet rs = pst.executeQuery()) {
      // Stream the result set to the file
      routines.write(rs, file);
    } catch (SQLException se) {
      LOG.debug(sb.toString());
      LOG.error("Export SQLException", se);
    }
    if (LOG.isDebugEnabled()) {
      long endQueryTime = System.currentTimeMillis();
      long totalTime = endQueryTime - startQueryTime;
      if (totalTime > LONG_QUERY_MS) {
        LOG.debug(sb.toString());
        LOG.debug("Query took " + totalTime + "ms");
      }
    }
  }

  public static boolean hasColumn(ResultSet rs, String column) {
    try {
      rs.findColumn(column);
      return true;
    } catch (SQLException ex) {
    }
    return false;
  }
}
