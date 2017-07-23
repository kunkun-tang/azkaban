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
 *
 */

package azkaban;

import static azkaban.ServiceProvider.SERVICE_PROVIDER;
import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import azkaban.db.DatabaseOperator;
import azkaban.project.JdbcProjectImpl;
import azkaban.spi.Storage;
import azkaban.storage.DatabaseStorage;
import azkaban.storage.LocalStorage;
import azkaban.storage.StorageManager;
import azkaban.utils.Props;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.io.File;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Test;


public class ServiceProviderTest {

  public static final String AZKABAN_LOCAL_TEST_STORAGE = "AZKABAN_LOCAL_TEST_STORAGE";

  // Test if one class is singletonly guiced. could be called by
  // AZ Common, Web, or Exec Modules.
  public static void assertSingletons(final Class azkabanClass, final Injector injector) {
    final Object azkabanObj1 = requireNonNull(injector.getInstance(azkabanClass));
    final Object azkabanObj2 = requireNonNull(injector.getInstance(azkabanClass));
    assertTrue(azkabanObj1 == azkabanObj2);
  }

  @After
  public void tearDown() throws Exception {
    FileUtils.deleteDirectory(new File(AZKABAN_LOCAL_TEST_STORAGE));
  }

  @Test
  public void testInjections() throws Exception {
    final Props props = new Props();
    props.put("database.type", "h2");
    props.put("h2.path", "h2");
    props
        .put(Constants.ConfigurationKeys.AZKABAN_STORAGE_LOCAL_BASEDIR, AZKABAN_LOCAL_TEST_STORAGE);

    final Injector injector = Guice.createInjector(
        new AzkabanCommonModule(props)
    );
    SERVICE_PROVIDER.unsetInjector();
    SERVICE_PROVIDER.setInjector(injector);

    assertNotNull(SERVICE_PROVIDER.getInstance(JdbcProjectImpl.class));
    assertNotNull(SERVICE_PROVIDER.getInstance(StorageManager.class));
    assertNotNull(SERVICE_PROVIDER.getInstance(DatabaseStorage.class));
    assertNotNull(SERVICE_PROVIDER.getInstance(LocalStorage.class));
    assertNotNull(SERVICE_PROVIDER.getInstance(Storage.class));
    assertNotNull(SERVICE_PROVIDER.getInstance(DatabaseOperator.class));
  }
}
