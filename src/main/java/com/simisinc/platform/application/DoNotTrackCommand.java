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

package com.simisinc.platform.application;

import com.simisinc.platform.application.admin.LoadSitePropertyCommand;

/**
 * Determines whether a request has asked not to be tracked, via the Do-Not-Track ({@code DNT: 1}) or
 * Global Privacy Control ({@code Sec-GPC: 1}) request headers. Honoring is opt-in through the
 * {@code analytics.honorDnt} site property so existing analytics behavior is unchanged by default; when
 * enabled, visitor and page-hit tracking is skipped for a signaling request.
 *
 * @author elizabeth houser
 */
public class DoNotTrackCommand {

  /**
   * @param dntHeader the value of the {@code DNT} request header, or null
   * @param gpcHeader the value of the {@code Sec-GPC} request header, or null
   * @return true when honoring is enabled and the request signals do-not-track
   */
  public static boolean isDoNotTrack(String dntHeader, String gpcHeader) {
    if (!LoadSitePropertyCommand.loadByNameAsBoolean("analytics.honorDnt")) {
      return false;
    }
    return "1".equals(dntHeader) || "1".equals(gpcHeader);
  }
}
