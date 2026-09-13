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

package com.simisinc.platform.presentation.controller;

import java.io.Serializable;

/**
 * What one widget cost during a page render (issue #2027): its {@code execute()} call plus the
 * include of its JSP, which is where its markup is actually produced.
 *
 * <p>These sum to the figure {@code totalRenderTime} already reports, so the breakdown accounts
 * for that number rather than measuring some adjacent thing. A page showing 183 ms of render with
 * no idea which of its twenty widgets spent it is the gap this closes.
 *
 * @author elizabeth houser
 */
public class WidgetRenderTime implements Serializable, Comparable<WidgetRenderTime> {

  private static final long serialVersionUID = 1L;

  private final String widgetName;
  private final String containerName;
  private final long millis;

  public WidgetRenderTime(String widgetName, String containerName, long millis) {
    this.widgetName = widgetName;
    this.containerName = containerName;
    this.millis = millis;
  }

  public String getWidgetName() {
    return widgetName;
  }

  /** The container this widget rendered in -- a page, the header, or the footer. */
  public String getContainerName() {
    return containerName;
  }

  public long getMillis() {
    return millis;
  }

  /** Slowest first: the reason to look at this list at all is to find what to fix. */
  @Override
  public int compareTo(WidgetRenderTime other) {
    return Long.compare(other.millis, this.millis);
  }
}
