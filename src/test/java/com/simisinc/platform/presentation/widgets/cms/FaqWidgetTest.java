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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.domain.model.cms.FaqQuestion;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * @author elizabeth houser
 */
class FaqWidgetTest extends WidgetBase {

  @Test
  @SuppressWarnings("unchecked")
  void executeParsesQuestionsAndBridgesThemForJsonLd() {
    preferences.put("questions",
        "question|What is a widget?||answer|A <strong>widget</strong> is a small thing.|||"
            + "question|How much do they cost?||answer|Prices vary.");

    WidgetContext result = new FaqWidget().execute(widgetContext);

    assertEquals("/cms/faq.jsp", result.getJsp());
    List<FaqQuestion> requestList = (List<FaqQuestion>) result.getRequest().getAttribute("faqQuestionList");
    assertEquals(2, requestList.size());
    assertEquals("What is a widget?", requestList.get(0).getQuestion());
    assertEquals("A <strong>widget</strong> is a small thing.", requestList.get(0).getAnswerHtml(),
        "the visible rendering keeps the answer's HTML");
    assertEquals("A widget is a small thing.", requestList.get(0).getAnswerText(),
        "the JSON-LD-bound text has HTML stripped");

    // The same list is bridged onto the WidgetContext for PageServlet to pick up
    assertEquals(2, result.getFaqQuestions().size());
    assertEquals(requestList.get(1).getQuestion(), result.getFaqQuestions().get(1).getQuestion());
  }

  @Test
  @SuppressWarnings("unchecked")
  void executeSkipsAnEntryWithABlankAnswerButKeepsTheOthers() {
    preferences.put("questions", "question|Has both||answer|Yes it does|||question|Blank one||answer|");

    WidgetContext result = new FaqWidget().execute(widgetContext);

    List<FaqQuestion> requestList = (List<FaqQuestion>) result.getRequest().getAttribute("faqQuestionList");
    assertEquals(1, requestList.size());
    assertEquals("Has both", requestList.get(0).getQuestion());
  }

  @Test
  void executeReturnsNullWhenQuestionsPreferenceIsEmpty() {
    WidgetContext result = new FaqWidget().execute(widgetContext);

    assertNull(result);
  }

  @Test
  void executeReturnsNullWhenTheOnlyEntryHasNoAnswer() {
    preferences.put("questions", "question|Only a question||answer|");

    WidgetContext result = new FaqWidget().execute(widgetContext);

    assertNull(result);
  }
}
