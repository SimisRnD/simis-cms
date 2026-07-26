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
import java.util.Map;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/8/18 2:15 PM
 */
public class WidgetRenderInfo implements Serializable {

  static final long serialVersionUID = -8484048371911908893L;

  // Output properties
  private String htmlId = null;
  private String cssClass = null;
  private String cssStyle = null;
  private boolean sticky = false;
  private boolean hr = false;
  private String content = null;
  private String prefsJson = "{}";

  public WidgetRenderInfo() {
  }

  public WidgetRenderInfo(Widget widget, String content) {
    this.htmlId = widget.getHtmlId();
    this.cssClass = widget.getCssClass();
    this.cssStyle = widget.getCssStyle();
    this.sticky = widget.isSticky();
    this.hr = widget.hasHr();
    this.content = content;
    Map<String, String> prefs = widget.getPreferences();
    if (prefs != null && !prefs.isEmpty()) {
      this.prefsJson = prefsToJson(prefs);
    }
  }

  private static String prefsToJson(Map<String, String> prefs) {
    StringBuilder sb = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> e : prefs.entrySet()) {
      if (!first) sb.append(',');
      sb.append('"').append(e.getKey()).append("\":\"");
      String v = e.getValue();
      if (v != null) {
        sb.append(v.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"));
      }
      sb.append('"');
      first = false;
    }
    sb.append('}');
    return sb.toString();
  }

  public String getHtmlId() {
    return htmlId;
  }

  public String getCssClass() {
    return cssClass;
  }

  public String getCssStyle() {
    return cssStyle;
  }

  public boolean isSticky() {
    return sticky;
  }

  public boolean getHr() { return hr; }

  public String getContent() {
    return content;
  }

  public String getPrefsJson() {
    return prefsJson;
  }
}
