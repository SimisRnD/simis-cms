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

package com.simisinc.platform.presentation.widgets.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.TableOfContents;
import com.simisinc.platform.domain.model.cms.TableOfContentsLink;
import com.simisinc.platform.infrastructure.persistence.cms.TableOfContentsRepository;

class TableOfContentsEditorWidgetTest extends WidgetBase {

  @Test
  void blankOrderValueDoesNotJumpTheEntryToTheFront() {
    // getParameterAsInt("orderN") (single-arg form) defaults an unparsable value to -1. Before this
    // fix, that -1 was used directly as the leading digit(s) of the sort key (a string
    // concatenation of order + a zero-padded row number), producing a large *negative* key that
    // sorted ahead of every entry with a real, non-negative Order -- silently moving a row with a
    // blank Order to the very top of the list. It should instead land at the end.
    widgetContext.getPreferences().put("uniqueId", "toc-order-test");

    addQueryParameter(widgetContext, "order1", "1");
    addQueryParameter(widgetContext, "name1", "First");
    addQueryParameter(widgetContext, "link1", "/first");

    addQueryParameter(widgetContext, "order2", "2");
    addQueryParameter(widgetContext, "name2", "Second");
    addQueryParameter(widgetContext, "link2", "/second");

    // Blank Order value on the third row
    addQueryParameter(widgetContext, "order3", "");
    addQueryParameter(widgetContext, "name3", "Third");
    addQueryParameter(widgetContext, "link3", "/third");

    try (MockedStatic<TableOfContentsRepository> repository = mockStatic(TableOfContentsRepository.class)) {
      repository.when(() -> TableOfContentsRepository.findByUniqueId("toc-order-test")).thenReturn(null);
      repository.when(() -> TableOfContentsRepository.save(any(TableOfContents.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      new TableOfContentsEditorWidget().post(widgetContext);

      ArgumentCaptor<TableOfContents> captor = ArgumentCaptor.forClass(TableOfContents.class);
      repository.verify(() -> TableOfContentsRepository.save(captor.capture()));
      List<TableOfContentsLink> entries = captor.getValue().getEntries();

      assertEquals(3, entries.size());
      assertEquals("First", entries.get(0).getName(), "an explicit, valid Order must still sort correctly");
      assertEquals("Second", entries.get(1).getName(), "an explicit, valid Order must still sort correctly");
      assertEquals("Third", entries.get(2).getName(),
          "a blank Order must append the entry to the end, not jump it ahead of entries with explicit, valid Orders");
    }
  }

  @Test
  void nonNumericOrderValueDoesNotJumpTheEntryToTheFront() {
    // Same defect, triggered by a non-numeric value instead of a blank one -- both fail
    // StringUtils.isNumeric() and fall through to the same default.
    widgetContext.getPreferences().put("uniqueId", "toc-order-test");

    addQueryParameter(widgetContext, "order1", "abc");
    addQueryParameter(widgetContext, "name1", "First");
    addQueryParameter(widgetContext, "link1", "/first");

    addQueryParameter(widgetContext, "order2", "1");
    addQueryParameter(widgetContext, "name2", "Second");
    addQueryParameter(widgetContext, "link2", "/second");

    addQueryParameter(widgetContext, "order3", "2");
    addQueryParameter(widgetContext, "name3", "Third");
    addQueryParameter(widgetContext, "link3", "/third");

    try (MockedStatic<TableOfContentsRepository> repository = mockStatic(TableOfContentsRepository.class)) {
      repository.when(() -> TableOfContentsRepository.findByUniqueId("toc-order-test")).thenReturn(null);
      repository.when(() -> TableOfContentsRepository.save(any(TableOfContents.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      new TableOfContentsEditorWidget().post(widgetContext);

      ArgumentCaptor<TableOfContents> captor = ArgumentCaptor.forClass(TableOfContents.class);
      repository.verify(() -> TableOfContentsRepository.save(captor.capture()));
      List<TableOfContentsLink> entries = captor.getValue().getEntries();

      assertEquals(3, entries.size());
      assertEquals("Second", entries.get(0).getName(), "an explicit, valid Order must still sort correctly");
      assertEquals("Third", entries.get(1).getName(), "an explicit, valid Order must still sort correctly");
      assertEquals("First", entries.get(2).getName(),
          "a non-numeric Order must append the entry to the end, not jump it ahead of entries with explicit, valid Orders");
    }
  }
}
