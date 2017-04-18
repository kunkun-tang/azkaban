/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package azkaban.db;

import java.sql.Connection;
import java.sql.SQLException;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.log4j.Logger;


public class MySQLDataSource extends AzkabanDataSource {

  private static Logger logger = Logger.getLogger(MySQLDataSource.class);

  private static volatile MySQLDataSource instance = null;

  private MySQLDataSource(String host, int port, String dbName,
      String user, String password, int numConnections) {
    super();

    String url = "jdbc:mysql://" + (host + ":" + port + "/" + dbName);
    addConnectionProperty("useUnicode", "yes");
    addConnectionProperty("characterEncoding", "UTF-8");
    setDriverClassName("com.mysql.jdbc.Driver");
    setUsername(user);
    setPassword(password);
    setUrl(url);
    setMaxTotal(numConnections);
    setValidationQuery("/* ping */ select 1");
    setTestOnBorrow(true);
  }

  /**
   * Get a singleton object for MySQL BasicDataSource
   */
  public static MySQLDataSource getInstance(String host, int port, String dbName,
      String user, String password, int numConnections) {
    if (instance == null) {
      synchronized (MySQLDataSource.class) {
        if (instance == null) {
          logger.info("Instantiating MetricReportManager");
          instance = new MySQLDataSource(host, port, dbName, user, password, numConnections);
        }
      }
    }
    return instance;
  }

  /**
   * This method overrides {@link BasicDataSource#getConnection()}, in order to have retry logics.
   *
   */
  @Override
  public synchronized Connection getConnection() throws SQLException {

      /*
        getInitialSize() returns the initial size of the connection pool.

        Note: The connection pool is only initialized the first time one of the
        following methods is invoked: <code>getConnection, setLogwriter,
        setLoginTimeout, getLoginTimeout, getLogWriter.</code>
       */
    if (getInitialSize() == 0) {
      return createDataSource().getConnection();
    }

    Connection connection = null;
    int retryAttempt = 0;
    while (retryAttempt < AzDBUtil.MAX_DB_RETRY_COUNT) {
      try {
          /*
           * when DB connection could not be fetched here, dbcp library will keep searching until a timeout defined in
           * its code hardly.
           */
        connection = createDataSource().getConnection();
        if(connection != null)
          return connection;
      } catch (SQLException ex) {
        logger.error("Failed to find DB connection. waits 1 minutes and retry. No.Attempt = " + retryAttempt, ex);
      } finally {
        retryAttempt ++;
      }
    }
    return null;
  }

  @Override
  public String getDBType() {
    return "mysql";
  }
}
