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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;

import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.application.filesystem.FileSystemCommand;
import com.simisinc.platform.infrastructure.database.DB;
import com.simisinc.platform.presentation.controller.ContextConstants;

/**
 * Tests the readiness checks behind the /healthz probe.
 *
 * @author SimIS Inc.
 */
class HealthCommandTest {

  private ServletContext contextWithStartup(String value) {
    ServletContext context = mock(ServletContext.class);
    when(context.getAttribute(ContextConstants.STARTUP_SUCCESSFUL)).thenReturn(value);
    return context;
  }

  @Test
  void startedUpReflectsTheStartupAttribute() {
    assertTrue(HealthCommand.startedUp(contextWithStartup("true")));
    assertFalse(HealthCommand.startedUp(contextWithStartup(null)));
    assertFalse(HealthCommand.startedUp(contextWithStartup("false")));
    assertFalse(HealthCommand.startedUp(null));
  }

  @Test
  void databaseReachableWhenAPooledConnectionIsValid() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isValid(anyInt())).thenReturn(true);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(connection);
      assertTrue(HealthCommand.databaseReachable());
    }
  }

  @Test
  void databaseNotReachableWhenInvalidOrConnectFails() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isValid(anyInt())).thenReturn(false);
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenReturn(connection);
      assertFalse(HealthCommand.databaseReachable());
    }
    // A failure to obtain a connection is DOWN, never an exception out of the check
    try (MockedStatic<DB> db = mockStatic(DB.class)) {
      db.when(DB::getConnection).thenThrow(new SQLException("db is gone"));
      assertFalse(HealthCommand.databaseReachable());
    }
  }

  @Test
  void fileStoreWritableChecksTheRootDirectory() {
    try (MockedStatic<FileSystemCommand> fs = mockStatic(FileSystemCommand.class)) {
      fs.when(FileSystemCommand::getFileServerRootPath).thenReturn(System.getProperty("java.io.tmpdir"));
      assertTrue(HealthCommand.fileStoreWritable());
      fs.when(FileSystemCommand::getFileServerRootPath).thenReturn("/no/such/simis-health-path-xyz");
      assertFalse(HealthCommand.fileStoreWritable());
      fs.when(FileSystemCommand::getFileServerRootPath).thenReturn("");
      assertFalse(HealthCommand.fileStoreWritable());
    }
  }

  @Test
  void isReadyOnlyWhenEveryCheckPasses() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isValid(anyInt())).thenReturn(true);
    try (MockedStatic<DB> db = mockStatic(DB.class);
        MockedStatic<FileSystemCommand> fs = mockStatic(FileSystemCommand.class)) {
      db.when(DB::getConnection).thenReturn(connection);
      fs.when(FileSystemCommand::getFileServerRootPath).thenReturn(System.getProperty("java.io.tmpdir"));

      assertTrue(HealthCommand.isReady(contextWithStartup("true")));
      // A single failing check (here: startup not complete) makes the whole probe report DOWN
      assertFalse(HealthCommand.isReady(contextWithStartup(null)));
    }
  }
}
