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

import java.io.Serializable;
import java.util.HashMap;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 7/17/18 2:30 PM
 */
public class ServiceResponse implements Serializable {

  final static long serialVersionUID = 215434482513634196L;

  private int status = -1;
  private HashMap<String, Object> meta = new HashMap<>();
  private Object data = null;
  private HashMap<String, String> links = new HashMap<>();
  private HashMap<String, String> error = new HashMap<>();

  public ServiceResponse(int status) {
    this.status = status;
  }

  public int getStatus() {
    return status;
  }

  public HashMap<String, Object> getMeta() {
    return meta;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public HashMap<String, String> getLinks() {
    return links;
  }

  public HashMap<String, String> getError() {
    return error;
  }
}
