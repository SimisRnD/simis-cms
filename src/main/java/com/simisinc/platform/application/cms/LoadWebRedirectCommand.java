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

package com.simisinc.platform.application.cms;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.domain.model.cms.WebRedirect;
import com.simisinc.platform.infrastructure.cache.CacheManager;

/**
 * Loads an admin-managed, database-backed URL redirect from cache or storage (issue #408). Mirrors
 * {@code LoadContentCommand}/{@code LoadStylesheetCommand}'s shape: a thin delegation to the
 * {@code CacheManager.WEB_REDIRECT_CACHE} {@code LoadingCache}, whose loader is
 * {@code WebRedirectRepository::findByFromPath} -- so the row is returned whether it is enabled or
 * not (see {@link #matchByFromPath}), and only a genuine miss (no row at all) comes back as
 * {@code null}. {@code WebRequestFilter} is the one that checks {@code enabled}, precisely so it can
 * tell "disabled" apart from "missing" -- see its class doc.
 *
 * @author SimIS Inc.
 */
public class LoadWebRedirectCommand {

  private static final Log LOG = LogFactory.getLog(LoadWebRedirectCommand.class);

  private LoadWebRedirectCommand() {
    // Static utility, not instantiated
  }

  /**
   * @param fromPath the incoming request path, e.g. {@code HttpServletRequest.getRequestURI()}
   *        relative to the context path
   * @return the {@code web_redirects} row for that path -- enabled or disabled -- or {@code null} if
   *         there isn't one at all. Callers that only want to actually redirect must check
   *         {@link WebRedirect#getEnabled()} themselves.
   */
  public static WebRedirect matchByFromPath(String fromPath) {
    if (StringUtils.isBlank(fromPath)) {
      return null;
    }
    // Use the cache; translate the cache-only "no row at all" sentinel back to null (see
    // CacheManager.WEB_REDIRECT_CACHE / WebRedirect.NONE)
    WebRedirect result = (WebRedirect) CacheManager.getLoadingCache(CacheManager.WEB_REDIRECT_CACHE).get(fromPath);
    return result == WebRedirect.NONE ? null : result;
  }
}
