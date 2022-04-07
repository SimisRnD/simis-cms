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

import com.simisinc.platform.domain.model.Entity;

import java.io.Serializable;
import java.util.List;

/**
 * Encapsulates the records being returned and the record count for paging
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class DataResult implements Serializable {

  final static long serialVersionUID = 8345648404174283569L;

  private List<? extends Entity> records = null;
  private long totalRecordCount = -1;

  public DataResult() {
  }

  public List<? extends Entity> getRecords() {
    return records;
  }

  public void setRecords(List<? extends Entity> records) {
    this.records = records;
  }

  public boolean hasRecords() {
    return records != null && !records.isEmpty();
  }

  public long getTotalRecordCount() {
    return totalRecordCount;
  }

  public void setTotalRecordCount(long totalRecordCount) {
    this.totalRecordCount = totalRecordCount;
  }
}
