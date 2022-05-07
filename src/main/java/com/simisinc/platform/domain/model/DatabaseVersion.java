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

package com.simisinc.platform.domain.model;

import java.sql.Timestamp;

/**
 * Represents a database version entry
 *
 * @author matt rajkowski
 * @created 4/18/18 4:27 PM
 */
public class DatabaseVersion extends Entity {

  private long id = -1;
  private String file = null;
  private String version = null;
  private Timestamp installed = null;

  public DatabaseVersion() {

  }

  public DatabaseVersion(String file, String version) {
    this.file = file;
    this.version = version;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  public Timestamp getInstalled() {
    return installed;
  }

  public void setInstalled(Timestamp installed) {
    this.installed = installed;
  }
}
