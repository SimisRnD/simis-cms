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

package com.simisinc.platform.rest.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.infrastructure.database.DataConstraints;

import java.io.Serializable;
import java.util.List;

/**
 * Common methods for service response
 *
 * @author matt rajkowski
 * @created 4/10/2022 8:51 AM
 */
public class ServiceResponseCommand implements Serializable {

  static final long serialVersionUID = 536435325324169646L;
  private static Log LOG = LogFactory.getLog(ServiceResponseCommand.class);

  public static void addMeta(ServiceResponse response, String type) {
    response.getMeta().put("type", type);
  }

  public static void addMeta(ServiceResponse response, String type, List recordList, DataConstraints constraints) {
    response.getMeta().put("type", type);
    response.getMeta().put("rows", recordList.size());
    if (constraints != null) {
      response.getMeta().put("pageIndex", constraints.getPageNumber());
      response.getMeta().put("totalPages", constraints.getMaxPageNumber());
      response.getMeta().put("totalItems", constraints.getTotalRecordCount());
    } else {
      response.getMeta().put("totalItems", recordList.size());
    }
  }
}
