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

package com.simisinc.platform.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.WidgetBase;

/**
 * Verifies the URL-building engine extracted from ItemsSearchResultsWidget (issue #634), which
 * CalendarSearchResultsWidget and WikiSearchResultsWidget now also depend on.
 *
 * @author SimIS Inc.
 */
class FacetUrlCommandTest extends WidgetBase {

  @Test
  void buildFacetLinkUrlSetsTheParamAndResetsPaging() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "page", "3");

    String url = FacetUrlCommand.buildFacetLinkUrl(widgetContext, "wikiId", "5");

    assertTrue(url.contains("wikiId=5"));
    assertTrue(url.contains("query=widgets"));
    assertFalse(url.contains("page="), "a facet selection changes the result set, so paging must reset");
  }

  @Test
  void buildFacetLinkUrlReplacesAnExistingValueForTheSameParam() {
    addQueryParameter(widgetContext, "wikiId", "5");

    String url = FacetUrlCommand.buildFacetLinkUrl(widgetContext, "wikiId", "6");

    assertTrue(url.contains("wikiId=6"));
    assertFalse(url.contains("wikiId=5"));
  }

  @Test
  void buildFacetLinkUrlPreservesOtherRepeatedParams() {
    widgetContext.getParameterMap().put("categoryId", new String[] { "1", "2" });

    String url = FacetUrlCommand.buildFacetLinkUrl(widgetContext, "dateFacet", "last7");

    assertTrue(url.contains("categoryId=1"));
    assertTrue(url.contains("categoryId=2"));
    assertTrue(url.contains("dateFacet=last7"));
  }

  @Test
  void buildClearFilterUrlDropsOnlyTheGivenParam() {
    addQueryParameter(widgetContext, "query", "widgets");
    addQueryParameter(widgetContext, "wikiId", "5");

    String url = FacetUrlCommand.buildClearFilterUrl(widgetContext, "wikiId");

    assertTrue(url.contains("query=widgets"));
    assertFalse(url.contains("wikiId="));
  }

  @Test
  void buildClearFilterUrlOnAnUnsetParamLeavesEverythingElseIntact() {
    addQueryParameter(widgetContext, "query", "widgets");

    String url = FacetUrlCommand.buildClearFilterUrl(widgetContext, "wikiId");

    assertTrue(url.contains("query=widgets"));
    assertFalse(url.contains("wikiId="));
  }
}
