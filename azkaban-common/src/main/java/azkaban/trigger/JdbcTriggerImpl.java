/*
 * Copyright 2012 LinkedIn Corp.
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

package azkaban.trigger;

import azkaban.database.AbstractJdbcLoader;
import azkaban.db.AzDBExceptionWrapper;
import azkaban.db.AzDBOperator;
import azkaban.utils.GZIPUtils;
import azkaban.utils.JSONUtils;
import azkaban.utils.Props;
import com.google.inject.Inject;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;


public class JdbcTriggerImpl extends AbstractJdbcLoader implements
    TriggerLoader {
  private static Logger logger = Logger.getLogger(JdbcTriggerImpl.class);

  private EncodingType defaultEncodingType = EncodingType.GZIP;

  private static final String triggerTblName = "triggers";

  private final AzDBOperator azDBOperator;

  private static final String GET_UPDATED_TRIGGERS =
      "SELECT trigger_id, trigger_source, modify_time, enc_type, data FROM "
          + triggerTblName + " WHERE modify_time>=?";

  private static String GET_ALL_TRIGGERS =
      "SELECT trigger_id, trigger_source, modify_time, enc_type, data FROM "
          + triggerTblName;

  private static String GET_TRIGGER =
      "SELECT trigger_id, trigger_source, modify_time, enc_type, data FROM "
          + triggerTblName + " WHERE trigger_id=?";

  private static String ADD_TRIGGER = "INSERT INTO " + triggerTblName
      + " ( modify_time) values (?)";

  private static String REMOVE_TRIGGER = "DELETE FROM " + triggerTblName
      + " WHERE trigger_id=?";

  private static String UPDATE_TRIGGER =
      "UPDATE "
          + triggerTblName
          + " SET trigger_source=?, modify_time=?, enc_type=?, data=? WHERE trigger_id=?";

  @Inject
  public JdbcTriggerImpl(AzDBOperator azDBOperator, Props props) {
    super(props);
    this.azDBOperator = azDBOperator;
  }

  @Override
  public List<Trigger> getUpdatedTriggers(long lastUpdateTime)
      throws TriggerLoaderException {
    logger.info("Loading triggers changed since "
        + new DateTime(lastUpdateTime).toString());

    ResultSetHandler<List<Trigger>> handler = new TriggerResultHandler();

    try {
      List<Trigger> triggers = azDBOperator.query(GET_UPDATED_TRIGGERS, handler, lastUpdateTime);
      logger.info("Loaded " + triggers.size() + " triggers.");
      return triggers;
    } catch(SQLException ex) {
      throw new TriggerLoaderException("Loading triggers from db failed.", ex);
    }
  }

  @Override
  public List<Trigger> loadTriggers() throws TriggerLoaderException {
    logger.info("Loading all triggers from db.");

    ResultSetHandler<List<Trigger>> handler = new TriggerResultHandler();

    try {
      List<Trigger> triggers = azDBOperator.query(GET_ALL_TRIGGERS, handler);
      logger.info("Loaded " + triggers.size() + " triggers.");
      return triggers;
    } catch(SQLException ex) {
      throw new TriggerLoaderException("Loading triggers from db failed.", ex);
    }
  }

  @Override
  public void removeTrigger(Trigger t) throws TriggerLoaderException {
    logger.info("Removing trigger " + t.toString() + " from db.");

    try {
      int removes = azDBOperator.update(REMOVE_TRIGGER, t.getTriggerId());
      if (removes == 0)
        throw new TriggerLoaderException("No trigger has been removed.");
    } catch(SQLException ex) {
      throw new TriggerLoaderException("Remove trigger " +
          t.getTriggerId() + " from db failed. ", ex);
    }
  }

  /**
   * TODO: Don't understand why we need synchronized here.
   */
  @Override
  public synchronized void addTrigger(Trigger t) throws TriggerLoaderException {
    logger.info("Inserting trigger " + t.toString() + " into db.");
    try{
      azDBOperator.update(ADD_TRIGGER, DateTime.now().getMillis());
      long id = azDBOperator.getLastInsertId();

      if(id == -1L) {
        logger.error("trigger id is not properly created.");
        throw new TriggerLoaderException("trigger id is not properly created.");
      }
      t.setTriggerId((int)id);
      updateTrigger(t);
      logger.info("uploaded trigger " + t.getDescription());
    } catch (SQLException ex) {
      throw new TriggerLoaderException("trigger id is not properly created.");
    }
  }

  @Override
  public void updateTrigger(Trigger t) throws TriggerLoaderException {
    logger.info("Updating trigger " + t.getTriggerId() + " into db.");
    t.setLastModifyTime(System.currentTimeMillis());
    updateTrigger(t, defaultEncodingType);
  }

  private void updateTrigger(Trigger t,
      EncodingType encType) throws TriggerLoaderException {

    String json = JSONUtils.toJSON(t.toJson());
    byte[] data = null;
    try {
      byte[] stringData = json.getBytes("UTF-8");
      data = stringData;

      if (encType == EncodingType.GZIP) {
        data = GZIPUtils.gzipBytes(stringData);
      }
      logger.debug("NumChars: " + json.length() + " UTF-8:" + stringData.length
          + " Gzip:" + data.length);
    } catch (IOException e) {
      logger.error("Trigger encoding fails", e);
      throw new TriggerLoaderException("Error encoding the trigger "
          + t.toString());
    }

    try {
      int updates = azDBOperator.update(UPDATE_TRIGGER, t.getTriggerId());
      if (updates == 0)
        throw new TriggerLoaderException("No trigger has been updated.");
    } catch(SQLException ex) {
      throw new TriggerLoaderException("DB Trigger update failed. ");
    }
  }

  public class TriggerResultHandler implements ResultSetHandler<List<Trigger>> {

    @Override
    public List<Trigger> handle(ResultSet rs) throws SQLException {
      if (!rs.next()) {
        return Collections.<Trigger> emptyList();
      }

      ArrayList<Trigger> triggers = new ArrayList<Trigger>();
      do {
        int triggerId = rs.getInt(1);
        int encodingType = rs.getInt(4);
        byte[] data = rs.getBytes(5);

        Object jsonObj = null;
        if (data != null) {
          EncodingType encType = EncodingType.fromInteger(encodingType);

          try {
            // Convoluted way to inflate strings. Should find common package or
            // helper function.
            if (encType == EncodingType.GZIP) {
              // Decompress the sucker.
              String jsonString = GZIPUtils.unGzipString(data, "UTF-8");
              jsonObj = JSONUtils.parseJSONFromString(jsonString);
            } else {
              String jsonString = new String(data, "UTF-8");
              jsonObj = JSONUtils.parseJSONFromString(jsonString);
            }
          } catch (IOException e) {
            throw new SQLException("Error reconstructing trigger data ");
          }
        }

        Trigger t = null;
        try {
          t = Trigger.fromJson(jsonObj);
          triggers.add(t);
        } catch (Exception e) {
          e.printStackTrace();
          logger.error("Failed to load trigger " + triggerId);
        }
      } while (rs.next());

      return triggers;
    }

  }

  @Override
  public Trigger loadTrigger(int triggerId) throws TriggerLoaderException {
    logger.info("Loading trigger " + triggerId + " from db.");
    ResultSetHandler<List<Trigger>> handler = new TriggerResultHandler();

    try {
      List<Trigger> triggers = azDBOperator.query(GET_TRIGGER, handler, triggerId);

      if (triggers.size() == 0) {
        logger.error("Loaded 0 triggers. Failed to load trigger " + triggerId);
        throw new TriggerLoaderException(
            "Loaded 0 triggers. Failed to load trigger " + triggerId);
      }
      return triggers.get(0);
    } catch(SQLException ex) {
      throw new TriggerLoaderException("Load a specific trigger failed.");
    }
  }
}
