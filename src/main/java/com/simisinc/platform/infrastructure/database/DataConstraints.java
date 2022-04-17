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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * The record paging and column sorting context
 *
 * @author matt rajkowski
 * @created 1/22/19 12:12 PM
 */
public class DataConstraints implements Serializable {

  final static long serialVersionUID = 8345648404174283569L;

  private int pageNumber = 1;
  private int pageSize = -1;
  private long totalRecordCount = -1L;
  private int maxPageNumber = 1;

  private String defaultColumnToSortBy = null;
  private String[] columnsToSortBy = null;
  private String[] sortOrder = null;

  private boolean useCount = true;

  public DataConstraints() {
  }

  public DataConstraints(int pageNumber, int pageSize) {
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
  }

  public DataConstraints(int pageNumber, int pageSize, String columnToSortBy) {
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
    setColumnToSortBy(columnToSortBy, null);
  }

  public DataConstraints(int pageNumber, int pageSize, String columnToSortBy, String ascOrDesc) {
    this.pageNumber = pageNumber;
    this.pageSize = pageSize;
    setColumnToSortBy(columnToSortBy, ascOrDesc);
  }

  public int getPageNumber() {
    return pageNumber;
  }

  public void setPageNumber(int pageNumber) {
    this.pageNumber = pageNumber;
  }

  public String getPageNumberAsString() {
    return String.valueOf(pageNumber);
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }

  public long getTotalRecordCount() {
    return totalRecordCount;
  }

  public void setTotalRecordCount(long totalRecordCount) {
    this.totalRecordCount = totalRecordCount;
    if (totalRecordCount > 0 && pageSize > 0) {
      maxPageNumber = (int) (totalRecordCount + pageSize - 1) / pageSize;
      if (maxPageNumber < 1) {
        maxPageNumber = 1;
      }
    }
  }

  public int getMaxPageNumber() {
    return maxPageNumber;
  }

  public String getDefaultColumnToSortBy() {
    return defaultColumnToSortBy;
  }

  /**
   * Used by the repository objects to define a define
   *
   * @param columnToSortBy
   * @return
   */
  public DataConstraints setDefaultColumnToSortBy(String columnToSortBy) {
    this.defaultColumnToSortBy = columnToSortBy;
    return this;
  }

  /**
   * Used by the application to override the default sort
   *
   * @param name
   */
  public void setColumnToSortBy(String name) {
    columnsToSortBy = new String[]{name};
  }

  /**
   * Used by the application to override the default sort
   *
   * @param name
   * @param ascOrDesc
   */
  public void setColumnToSortBy(String name, String ascOrDesc) {
    columnsToSortBy = new String[]{name};
    if ("desc".equals(ascOrDesc)) {
      sortOrder = new String[]{"desc"};
    }
  }

  public String[] getColumnsToSortBy() {
    return columnsToSortBy;
  }

  /**
   * Used by the application to override the default sort
   *
   * @param columnsToSortBy
   */
  public void setColumnsToSortBy(String[] columnsToSortBy) {
    this.columnsToSortBy = columnsToSortBy;
  }

  public String[] getSortOrder() {
    return sortOrder;
  }

  public void setSortOrder(String[] sortOrder) {
    this.sortOrder = sortOrder;
  }

  public boolean containsColumnToSortBy(String name) {
    if (columnsToSortBy == null) {
      return false;
    }
    for (String column : columnsToSortBy) {
      if (name.equals(column)) {
        return true;
      }
    }
    return false;
  }

  public boolean hasSortOrder() {
    return defaultColumnToSortBy != null || columnsToSortBy != null;
  }

  public List getPageList() {
    int current = pageNumber,
        last = maxPageNumber,
        delta = 2,
        left = current - delta,
        right = current + delta + 1;
    List<String> range = new ArrayList<>();
    List<String> rangeWithDots = new ArrayList<>();
    int l = 0;
    for (int i = 1; i <= last; i++) {
      if (i == 1 || i == last || i >= left && i < right) {
        range.add("" + i);
      }
    }
    for (String i : range) {
      if (l > 0) {
        if (Integer.parseInt(i) - l == 2) {
          rangeWithDots.add("" + (l + 1));
        } else if (Integer.parseInt(i) - l != 1) {
          rangeWithDots.add("...");
        }
      }
      rangeWithDots.add(i);
      l = Integer.parseInt(i);
    }
    return rangeWithDots;
  }

  public boolean useCount() {
    return useCount;
  }

  public DataConstraints setUseCount(boolean useCount) {
    this.useCount = useCount;
    return this;
  }
}
