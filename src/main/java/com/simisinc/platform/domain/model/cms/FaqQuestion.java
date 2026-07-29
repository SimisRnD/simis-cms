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

package com.simisinc.platform.domain.model.cms;

import com.simisinc.platform.domain.model.Entity;

/**
 * A single question/answer pair within a FaqWidget. answerText is the HTML-stripped form of
 * answerHtml, computed once by the widget for the FAQPage JSON-LD schema (issue #416) so it
 * doesn't need to be re-derived wherever the JSON-LD is assembled.
 *
 * @author elizabeth houser
 */
public class FaqQuestion extends Entity {

  private String question = null;
  private String answerHtml = null;
  private String answerText = null;

  public FaqQuestion() {
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }

  public String getAnswerHtml() {
    return answerHtml;
  }

  public void setAnswerHtml(String answerHtml) {
    this.answerHtml = answerHtml;
  }

  public String getAnswerText() {
    return answerText;
  }

  public void setAnswerText(String answerText) {
    this.answerText = answerText;
  }
}
