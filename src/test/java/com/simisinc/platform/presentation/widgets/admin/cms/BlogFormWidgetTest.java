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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.cms.SaveBlogCommand;
import com.simisinc.platform.domain.model.cms.Blog;
import com.simisinc.platform.domain.model.mailinglists.MailingList;
import com.simisinc.platform.infrastructure.persistence.mailinglists.MailingListRepository;
import com.simisinc.platform.presentation.controller.WidgetContext;

/**
 * Issue #599 -- the blog edit form's mailing list association picker.
 */
class BlogFormWidgetTest extends WidgetBase {

  private static MailingList mailingList(long id, String title, boolean enabled) {
    MailingList mailingList = new MailingList();
    mailingList.setId(id);
    mailingList.setTitle(title);
    mailingList.setEnabled(enabled);
    return mailingList;
  }

  @Test
  void executeOnlyOffersEnabledMailingLists() {
    List<MailingList> lists = new ArrayList<>();
    lists.add(mailingList(1L, "Active List", true));
    lists.add(mailingList(2L, "Disabled List", false));

    try (MockedStatic<MailingListRepository> listRepo = mockStatic(MailingListRepository.class)) {
      listRepo.when(MailingListRepository::findAll).thenReturn(lists);

      new BlogFormWidget().execute(widgetContext);

      List<MailingList> shown = (List<MailingList>) request.getAttribute("mailingLists");
      Assertions.assertEquals(1, shown.size());
      Assertions.assertEquals("Active List", shown.get(0).getTitle());
    }
  }

  @Test
  void postPopulatesTheMailingListIdFromTheFormAndSaves() throws Exception {
    addQueryParameter(widgetContext, "name", "News");
    addQueryParameter(widgetContext, "mailingListId", "5");

    try (MockedStatic<SaveBlogCommand> saveBlog = mockStatic(SaveBlogCommand.class)) {
      Blog saved = new Blog();
      saved.setId(1L);
      saved.setMailingListId(5L);
      saveBlog.when(() -> SaveBlogCommand.saveBlog(any())).thenReturn(saved);

      WidgetContext result = new BlogFormWidget().post(widgetContext);

      saveBlog.verify(() -> SaveBlogCommand.saveBlog(
          org.mockito.ArgumentMatchers.argThat(bean -> bean.getMailingListId() == 5L)));
      Assertions.assertEquals("Blog was saved", result.getSuccessMessage());
    }
  }

  @Test
  void postSurfacesAValidationErrorForAnUnknownMailingList() throws Exception {
    addQueryParameter(widgetContext, "name", "News");
    addQueryParameter(widgetContext, "mailingListId", "999");

    try (MockedStatic<SaveBlogCommand> saveBlog = mockStatic(SaveBlogCommand.class)) {
      saveBlog.when(() -> SaveBlogCommand.saveBlog(any()))
          .thenThrow(new DataException("Please check the form and try again:\nThe selected mailing list could not be found"));

      WidgetContext result = new BlogFormWidget().post(widgetContext);

      Assertions.assertTrue(result.getErrorMessage().contains("mailing list could not be found"));
    }
  }
}
