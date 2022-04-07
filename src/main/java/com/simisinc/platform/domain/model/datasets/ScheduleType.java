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

package com.simisinc.platform.domain.model.datasets;

import java.util.Arrays;

/**
 * An interval for how often a dataset should be refreshed
 *
 * @author matt rajkowski
 * @created 1/24/20 2:00 PM
 */
public class ScheduleType {

  public static final int UNDEFINED = -1;
  public static final int HOURLY = 1000;
  public static final int DAILY = 2000;
  public static final int WEEKLY = 3000;
  public static final int BI_WEEKLY = 4000;
  public static final int MONTHLY = 5000;
  public static final int QUARTERLY = 6000;
  public static final int YEARLY = 7000;

  public static final String HOURLY_VALUE = "hourly";
  public static final String DAILY_VALUE = "daily";
  public static final String WEEKLY_VALUE = "weekly";
  public static final String BI_WEEKLY_VALUE = "bi-weekly";
  public static final String MONTHLY_VALUE = "monthly";
  public static final String QUARTERLY_VALUE = "quarterly";
  public static final String YEARLY_VALUE = "yearly";

  public static boolean isValid(int id) {
    return Arrays.asList(new Integer[]{HOURLY, DAILY, WEEKLY, BI_WEEKLY, MONTHLY, QUARTERLY, YEARLY}).contains(id);
  }

  public static int getTypeIdFromString(String name) {
    if (HOURLY_VALUE.equals(name)) {
      return HOURLY;
    } else if (DAILY_VALUE.equals(name)) {
      return DAILY;
    } else if (WEEKLY_VALUE.equals(name)) {
      return WEEKLY;
    } else if (BI_WEEKLY_VALUE.equals(name)) {
      return BI_WEEKLY;
    } else if (MONTHLY_VALUE.equals(name)) {
      return MONTHLY;
    } else if (QUARTERLY_VALUE.equals(name)) {
      return QUARTERLY;
    } else if (YEARLY_VALUE.equals(name)) {
      return YEARLY;
    }
    return UNDEFINED;
  }

  public static String getStringFromTypeId(int id) {
    if (id == HOURLY) {
      return HOURLY_VALUE;
    } else if (id == DAILY) {
      return DAILY_VALUE;
    } else if (id == WEEKLY) {
      return WEEKLY_VALUE;
    } else if (id == BI_WEEKLY) {
      return BI_WEEKLY_VALUE;
    } else if (id == MONTHLY) {
      return MONTHLY_VALUE;
    } else if (id == QUARTERLY) {
      return QUARTERLY_VALUE;
    } else if (id == YEARLY) {
      return YEARLY_VALUE;
    }
    return null;
  }
}
