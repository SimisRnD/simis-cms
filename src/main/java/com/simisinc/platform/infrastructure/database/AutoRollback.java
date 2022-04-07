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

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Part of the auto handling of transactions
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class AutoRollback implements AutoCloseable {

  private Connection connection;
  private boolean committed;

  public AutoRollback(Connection connection) {
    this.connection = connection;
  }

  public void commit() throws SQLException {
    connection.commit();
    committed = true;
  }

  @Override
  public void close() throws SQLException {
    if (!committed) {
      connection.rollback();
    }
  }
}
