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

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.simisinc.platform.application.cms.ContentVersionDiffCommand;
import com.simisinc.platform.domain.model.User;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.ContentVersion;
import com.simisinc.platform.infrastructure.database.DataConstraints;
import com.simisinc.platform.infrastructure.persistence.UserRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.ContentVersionRepository;
import com.simisinc.platform.presentation.controller.RequestConstants;
import com.simisinc.platform.presentation.controller.WidgetContext;
import com.simisinc.platform.presentation.widgets.GenericWidget;

/**
 * The /admin/content-versions admin page (issue #406): lists a content block's prior published
 * revisions (approver, release reference, timestamp), offers a word-level diff between any two
 * selected versions, and a restore action that loads the chosen version into the draft slot for
 * review -- a subsequent submit/approve/publish cycle is required to make it live again.
 *
 * @author elizabeth houser
 */
public class ContentVersionsListWidget extends GenericWidget {

  static final long serialVersionUID = -8484048371911908894L;

  static String JSP = "/admin/content-versions-list.jsp";

  public WidgetContext execute(WidgetContext context) {

    String uniqueId = context.getParameter("uniqueId");
    Content content = StringUtils.isNotBlank(uniqueId) ? ContentRepository.findByUniqueId(uniqueId) : null;
    if (content == null) {
      context.setErrorMessage("Content was not found");
      return context;
    }
    context.getRequest().setAttribute("content", content);

    // Determine the record paging
    int limit = Integer.parseInt(context.getPreferences().getOrDefault("limit", "20"));
    int page = context.getParameterAsInt("page", 1);
    int itemsPerPage = context.getParameterAsInt("items", limit);
    DataConstraints constraints = new DataConstraints(page, itemsPerPage);
    context.getRequest().setAttribute(RequestConstants.RECORD_PAGING, constraints);

    List<ContentVersion> versionList = ContentVersionRepository.findByContentId(content.getId(), constraints);
    context.getRequest().setAttribute("versionList", versionList);

    // Resolve the approver display name for each version shown on this page
    Map<Long, User> userMap = new HashMap<>();
    if (versionList != null) {
      for (ContentVersion version : versionList) {
        long approvedBy = version.getApprovedBy();
        if (approvedBy > -1 && !userMap.containsKey(approvedBy)) {
          userMap.put(approvedBy, UserRepository.findByUserId(approvedBy));
        }
      }
    }
    context.getRequest().setAttribute("userMap", userMap);

    context.getRequest().setAttribute("recordPagingParams", "uniqueId=" + uniqueId);

    // An optional word-level diff between two versions selected on the page just rendered (#406).
    // Both ids are re-checked against this content block's own versions -- a stray/foreign
    // contentVersionId (typed into the URL, or left over from a different block) must never diff
    // content that doesn't belong here.
    long compareFromId = context.getParameterAsLong("compareFrom", -1);
    long compareToId = context.getParameterAsLong("compareTo", -1);
    if (compareFromId > -1 && compareToId > -1) {
      ContentVersion from = ContentVersionRepository.findById(compareFromId);
      ContentVersion to = ContentVersionRepository.findById(compareToId);
      if (from != null && to != null
          && from.getContentId() == content.getId() && to.getContentId() == content.getId()) {
        context.getRequest().setAttribute("diffResult", ContentVersionDiffCommand.diff(from.getContent(), to.getContent()));
        context.getRequest().setAttribute("compareFromId", compareFromId);
        context.getRequest().setAttribute("compareToId", compareToId);
      } else {
        context.setWarningMessage("One or both selected versions could not be compared");
      }
    }

    // Standard request items
    context.getRequest().setAttribute("icon", context.getPreferences().get("icon"));
    context.getRequest().setAttribute("title", context.getPreferences().get("title"));

    context.setJsp(JSP);
    return context;
  }

  public WidgetContext post(WidgetContext context) throws InvocationTargetException, IllegalAccessException {

    if (!"restore".equals(context.getParameter("action"))) {
      return null;
    }

    long contentVersionId = context.getParameterAsLong("contentVersionId", -1);
    ContentVersion version = contentVersionId > -1 ? ContentVersionRepository.findById(contentVersionId) : null;
    if (version == null) {
      context.setErrorMessage("The selected version was not found");
      return execute(context);
    }

    if (!ContentRepository.restoreDraftFromVersion(version.getContentId(), version.getContent())) {
      context.setErrorMessage("The version could not be restored");
      return execute(context);
    }

    context.setSuccessMessage("The version was restored to the draft. It must be reviewed and published again to go live.");
    return execute(context);
  }
}
