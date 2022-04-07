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

package com.simisinc.platform.presentation.controller;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class XMLPageLoaderFiles {
  private String file = null;
  private long lastModified = -1;

  public XMLPageLoaderFiles() {
  }


  public XMLPageLoaderFiles(String fileName) {
    this(fileName, -1);
  }


  public XMLPageLoaderFiles(String file, long lastModified) {
    this.file = file;
    this.lastModified = lastModified;
  }


  public long getLastModified() {
    return lastModified;
  }


  public String getFile() {
    return file;
  }


  public void setLastModified(long value) {
    lastModified = value;
  }


  public void setFile(String value) {
    file = value;
  }
}
