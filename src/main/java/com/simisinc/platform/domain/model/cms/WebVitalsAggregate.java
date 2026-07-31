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

package com.simisinc.platform.domain.model.cms;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import com.simisinc.platform.domain.model.Entity;

/**
 * A single day's p50/p75/p95 Core Web Vitals aggregate for one URL and metric, as computed
 * nightly by WebVitalsAggregationJob into the web_vitals_aggregates table. Used to plot a
 * multi-day trend (issue #762), one row per (url, metric_type, day).
 *
 * @author claude
 * @created 7/31/26
 */
public class WebVitalsAggregate extends Entity {

  private String url;
  private String metricType;
  private double p50Value;
  private double p75Value;
  private double p95Value;
  private long sampleCount;
  private Timestamp aggregatedAt;

  public WebVitalsAggregate() {
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getMetricType() {
    return metricType;
  }

  public void setMetricType(String metricType) {
    this.metricType = metricType;
  }

  public double getP50Value() {
    return p50Value;
  }

  public void setP50Value(double p50Value) {
    this.p50Value = p50Value;
  }

  public double getP75Value() {
    return p75Value;
  }

  public void setP75Value(double p75Value) {
    this.p75Value = p75Value;
  }

  public double getP95Value() {
    return p95Value;
  }

  public void setP95Value(double p95Value) {
    this.p95Value = p95Value;
  }

  public long getSampleCount() {
    return sampleCount;
  }

  public void setSampleCount(long sampleCount) {
    this.sampleCount = sampleCount;
  }

  public Timestamp getAggregatedAt() {
    return aggregatedAt;
  }

  public void setAggregatedAt(Timestamp aggregatedAt) {
    this.aggregatedAt = aggregatedAt;
  }

  /**
   * Day-granularity label (yyyy-MM-dd) for chart axes and the screen-reader data table -- matches
   * the aggregation job's one-row-per-day grain, so this is always stable regardless of the time
   * component NOW()/date_trunc('day', ...) happens to store.
   */
  public String getDateLabel() {
    if (aggregatedAt == null) {
      return "";
    }
    return new SimpleDateFormat("yyyy-MM-dd").format(aggregatedAt);
  }
}
