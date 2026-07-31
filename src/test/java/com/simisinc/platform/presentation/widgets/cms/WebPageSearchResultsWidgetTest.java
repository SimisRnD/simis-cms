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

package com.simisinc.platform.presentation.widgets.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageSpecification;
import com.simisinc.platform.presentation.controller.DataConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author matt rajkowski
 * @created 5/7/2022 8:30 AM
 */
class WebPageSearchResultsWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // No query parameters
    WebPageSearchResultsWidget widget = new WebPageSearchResultsWidget();
    Assertions.assertNull(widget.execute(widgetContext));

    // Set widget preferences
    preferences.put("query", "test");

    // Expect no results
    widget.execute(widgetContext);
    Assertions.assertNull(widgetContext.getJsp());

    // Mock the results
//    Assertions.assertEquals(JSP, widgetContext.getJsp());
  }

  // --- buildWebPageSearchSpecification: draft must never be filtered at the specification level.
  // A page with draft=true can still be published -- WebPageRepository.publish() is the only thing
  // that clears page_xml, and it always runs alongside draft going back to false, so an
  // already-published page's page_xml stays valid regardless of a later pending draft edit. Excluding
  // draft=true here silently hid a live, published page the moment an editor made any layout tweak to
  // it. The existing blank-pageXml check further down execute() already excludes pages that have
  // genuinely never been published, so no replacement filter is needed -- see PR #768/#770 for the
  // same conflation fixed elsewhere in this codebase. ---

  @Test
  void doesNotFilterByDraftForANonPrivilegedViewer() {
    WebPageSpecification specification = WebPageSearchResultsWidget.buildWebPageSearchSpecification(widgetContext);
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getDraft(),
        "a published page can have draft=true (a pending edit) and must still be searchable");
  }

  @Test
  void doesNotFilterByDraftForAGuest() {
    logout(widgetContext);
    WebPageSpecification specification = WebPageSearchResultsWidget.buildWebPageSearchSpecification(widgetContext);
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getDraft());
  }

  @Test
  void stillRestrictsToSearchablePagesForANonPrivilegedViewer() {
    WebPageSpecification specification = WebPageSearchResultsWidget.buildWebPageSearchSpecification(widgetContext);
    Assertions.assertEquals(DataConstants.TRUE, specification.getSearchable());
  }

  @Test
  void doesNotRestrictSearchableForAnAdmin() {
    setRoles(widgetContext, ADMIN);
    WebPageSpecification specification = WebPageSearchResultsWidget.buildWebPageSearchSpecification(widgetContext);
    Assertions.assertEquals(DataConstants.UNDEFINED, specification.getSearchable());
  }

  @Test
  void alwaysExcludesRedirects() {
    setRoles(widgetContext, ADMIN);
    WebPageSpecification specification = WebPageSearchResultsWidget.buildWebPageSearchSpecification(widgetContext);
    Assertions.assertEquals(DataConstants.FALSE, specification.getHasRedirect());
  }
}