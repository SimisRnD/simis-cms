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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Properties;

/**
 * Manages the PostgreSQL database connections and generates the SQL statements
 *
 * @author matt rajkowski
 * @created 4/8/18 5:08 PM
 */
public class DataSource {

  private static Log LOG = LogFactory.getLog(DataSource.class);

  private static HikariDataSource ds;

//  static {
//      config.setJdbcUrl( "jdbc_url" );
//      config.setUsername( "database_username" );
//      config.setPassword( "database_password" );
//    config = new HikariConfig("/WEB-INF/database.properties");
//      config.addDataSourceProperty( "cachePrepStmts" , "true" );
//      config.addDataSourceProperty( "prepStmtCacheSize" , "250" );
//      config.addDataSourceProperty( "prepStmtCacheSqlLimit" , "2048" );
//    ds = new HikariDataSource(config);
//  }

  private DataSource() {
  }

  public static void init(Properties properties) {
    HikariConfig config = new HikariConfig(properties);
    config.setMaxLifetime(600000);
    ds = new HikariDataSource(config);
    LOG.info("Max pool size: " + ds.getMaximumPoolSize());
  }

  public static void shutdown() {
    ds.close();
  }

  public static javax.sql.DataSource getDataSource() {
    return ds;
  }

}
