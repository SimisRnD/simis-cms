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

package com.simisinc.platform.presentation.widgets.admin.cms;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListMemberRepository;
import com.simisinc.platform.presentation.widgets.admin.SiteStatsWidget;

/**
 * Covers {@link CommunityStatsWidget} -- specifically that the "Mailing List Subscribers" tile
 * (issue #562, formerly the mislabeled "Total Sign-ups") reads from the new distinct-subscriber
 * count rather than the old, non-decrementing member_count sum.
 *
 * @author SimIS Inc.
 * @created 7/28/2026
 */
class CommunityStatsWidgetTest extends WidgetBase {

  @Test
  void totalMailingListMembersUsesTheDistinctSubscriberCount() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"communityStats\">\n" +
            "  <icon>fa-mail-bulk</icon>\n" +
            "  <title>Mailing List Subscribers</title>\n" +
            "  <report>total-mailing-list-members</report>\n" +
            "</widget>");

    try (MockedStatic<MailingListMemberRepository> repository = mockStatic(MailingListMemberRepository.class)) {
      repository.when(MailingListMemberRepository::countDistinctSubscribers).thenReturn(331L);

      new CommunityStatsWidget().execute(widgetContext);

      repository.verify(MailingListMemberRepository::countDistinctSubscribers);
    }

    Assertions.assertEquals(SiteStatsWidget.CARD_JSP, widgetContext.getJsp());
    Assertions.assertEquals("Mailing List Subscribers", request.getAttribute("title"));
    Assertions.assertEquals("331", request.getAttribute("numberValue"));
  }

  @Test
  void anUnrecognizedReportIsIgnored() {
    addPreferencesFromWidgetXml(widgetContext,
        "<widget name=\"communityStats\">\n" +
            "  <report>something-else</report>\n" +
            "</widget>");

    Assertions.assertNull(new CommunityStatsWidget().execute(widgetContext));
  }
}
