/*
 * Copyright 2026 SimIS Inc. (https://www.simiscms.com)
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

import java.io.Serializable;
import java.time.LocalDate;

/**
 * A United States federal holiday, carrying both the date the statute names and the date it is
 * actually observed.
 *
 * <p>The two differ whenever a fixed-date holiday lands on a weekend, and the observed date is the
 * one a person cares about -- it is the day the office is shut. Both are kept because a list that
 * says only "Friday, July 3rd" next to "Independence Day" invites the reader to think it is wrong.
 *
 * @author SimIS Inc.
 */
public class FederalHoliday implements Serializable, Comparable<FederalHoliday> {

  static final long serialVersionUID = 8675309105061980L;

  private final String name;
  private final LocalDate date;
  private final LocalDate observedDate;

  public FederalHoliday(String name, LocalDate date, LocalDate observedDate) {
    this.name = name;
    this.date = date;
    this.observedDate = observedDate;
  }

  public String getName() {
    return name;
  }

  /** The date named by 5 U.S.C. 6103 */
  public LocalDate getDate() {
    return date;
  }

  /** The date it is actually taken, which is what a reader is looking for */
  public LocalDate getObservedDate() {
    return observedDate;
  }

  /** True when the weekend rule moved it, so a caller can show the statutory date as well */
  public boolean getObservedOnDifferentDay() {
    return !date.equals(observedDate);
  }

  /** Ordered by the date people actually take off, which is how a list of them should read */
  @Override
  public int compareTo(FederalHoliday other) {
    return observedDate.compareTo(other.observedDate);
  }
}
