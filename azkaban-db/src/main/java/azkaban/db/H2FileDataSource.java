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

import java.nio.file.Path;


public class H2FileDataSource extends AzkabanDataSource {

  public H2FileDataSource(final Path filePath) {
    super();
    final String url = "jdbc:h2:file:" + filePath;
    setDriverClassName("org.h2.Driver");
    setUrl(url);
  }

  @Override
  public String getDBType() {
    return "h2";
  }

  @Override
  public boolean allowsOnDuplicateKey() {
    return false;
  }
}
