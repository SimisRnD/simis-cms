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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.ContentImageSrcsetCommand;
import com.simisinc.platform.application.cms.HtmlCommand;
import com.simisinc.platform.domain.model.cms.FaqQuestion;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * Renders a list of question/answer pairs as an accessible disclosure list (issue #416) and
 * bridges the same data into WidgetContext so PageServlet can emit a FAQPage JSON-LD block
 * alongside it.
 *
 * @author elizabeth houser
 */
public class FaqWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908893L;

  static String JSP = "/cms/faq.jsp";

  public WidgetContext execute(WidgetContext context) {

    PreferenceEntriesList entriesList = context.getPreferenceAsDataList("questions");
    if (entriesList.isEmpty()) {
      LOG.debug("Questions preference is empty");
      return null;
    }

    List<FaqQuestion> faqQuestionList = new ArrayList<>();
    for (Map<String, String> valueMap : entriesList) {
      String question = valueMap.get("question");
      String answer = valueMap.get("answer");
      if (StringUtils.isBlank(question) || StringUtils.isBlank(answer)) {
        continue;
      }
      FaqQuestion faqQuestion = new FaqQuestion();
      faqQuestion.setQuestion(question);
      faqQuestion.setAnswerHtml(ContentImageSrcsetCommand.enhanceImageTags(answer));
      faqQuestion.setAnswerText(HtmlCommand.text(answer));
      faqQuestionList.add(faqQuestion);
    }

    if (faqQuestionList.isEmpty()) {
      return null;
    }

    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));
    context.getRequest().setAttribute("faqQuestionList", faqQuestionList);

    // Bridged into pageRenderInfo by WebContainerCommand for the FAQPage JSON-LD schema
    context.setFaqQuestions(faqQuestionList);

    context.setJsp(JSP);
    return context;
  }
}
