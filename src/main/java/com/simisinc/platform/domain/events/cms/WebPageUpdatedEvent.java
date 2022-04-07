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

package com.simisinc.platform.domain.events.cms;

import com.simisinc.platform.domain.events.Event;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/9/21 4:33 PM
 */
@NoArgsConstructor
public class WebPageUpdatedEvent extends Event {

  public static final String ID = "web-page-updated";

  private WebPage webPage = null;

  public WebPageUpdatedEvent(WebPage webPage) {
    this.webPage = webPage;
  }

  @Override
  public String getDomainEventType() {
    return ID;
  }

  public User getUser() {
    return UserRepository.findByUserId(webPage.getModifiedBy());
  }

  public void setWebPage(WebPage webPage) {
    this.webPage = webPage;
  }

  public WebPage getWebPage() {
    return webPage;
  }

  public String getTitle() {
    if (StringUtils.isNotBlank(webPage.getTitle())) {
      return webPage.getTitle();
    } else if ("/".equals(webPage.getLink())) {
      return "Home page";
    } else {
      // The user may not have set a title
      return webPage.getLink();
    }
  }
}
