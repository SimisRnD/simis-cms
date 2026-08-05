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

package com.simisinc.platform.rest.services.cms;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.audit.SaveAuditEventCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.DeltaContentCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.UserSession;
import com.simisinc.platform.rest.controller.ServiceContext;
import com.simisinc.platform.rest.controller.ServiceResponse;
import com.simisinc.platform.rest.controller.ServiceResponseCommand;

/**
 * Description
 *
 * @author matt rajkowski
 * @created 4/17/18 9:00 AM
 */
public class ContentService {

  private static Log LOG = LogFactory.getLog(ContentService.class);

  // GET /content/{contentUniqueId}
  public ServiceResponse get(ServiceContext context) {

    String contentUniqueId = context.getPathParam();
    Content content = LoadContentCommand.loadContentByUniqueId(contentUniqueId);
    if (content == null) {
      ServiceResponse response = new ServiceResponse(404);
      response.getError().put("title", "Content was not found");
      return response;
    }

    // Set the fields to return
    ContentResponse contentResponse = new ContentResponse(content);

    // Prepare the response
    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "content");
    response.setData(contentResponse);
    return response;
  }

  // POST /content/{contentUniqueId} (issue #412 PR2)
  //
  // Not listed as a separate <service method="post" .../> in rest-services.xml -- RestServlet
  // dispatches purely by reflecting for a method named after the HTTP verb, so adding post() here
  // is all that's needed; see the comment on the existing GET entry in that file.
  public ServiceResponse post(ServiceContext context) {

    // RestRequestFilter demotes an unauthenticated (no Bearer token) caller to a role-less guest
    // rather than rejecting outright, since guest reads are legitimate -- but a write never is.
    if (context.getUserId() <= UserSession.GUEST_ID) {
      ServiceResponse response = new ServiceResponse(401);
      response.getError().put("title", "Authentication required");
      return response;
    }
    if (!EditorPermissionCommand.hasContentEditorRole(context::hasRole)) {
      ServiceResponse response = new ServiceResponse(403);
      response.getError().put("title", "Not authorized to edit content");
      return response;
    }

    String contentUniqueId = context.getPathParam();
    if (StringUtils.isBlank(contentUniqueId)) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Content id must be specified");
      return response;
    }

    ContentUpdateRequest updateRequest;
    try {
      updateRequest = context.readJsonBody(ContentUpdateRequest.class);
    } catch (IOException | RuntimeException e) {
      LOG.debug("Could not parse the request body: " + e.getMessage());
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Request body must be valid JSON");
      return response;
    }
    if (updateRequest == null || StringUtils.isBlank(updateRequest.getContent())) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "Content is required");
      return response;
    }
    boolean useDelta = ContentUpdateRequest.FORMAT_DELTA.equalsIgnoreCase(updateRequest.getFormat());
    if (!useDelta && !ContentUpdateRequest.FORMAT_HTML.equalsIgnoreCase(updateRequest.getFormat())) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", "format must be \"html\" or \"delta\"");
      return response;
    }

    // Governed publishing: resolved here, once, rather than trusting SaveContentCommand's own
    // internal re-check -- passing the ALREADY-gated value below (not the caller's raw request)
    // means this response can never claim "gated" while the save call underneath actually published,
    // regardless of whether every current and future SaveContentCommand save method enforces this
    // consistently on its own.
    boolean requestedPublish = updateRequest.isPublish();
    boolean willActuallyPublish = requestedPublish
        && ContentReviewCommand.mayPublishDirectly(LoadSitePropertyCommand.loadByNameAsBoolean("content.review.required"));

    // A content block can independently carry a published version and a pending draft in
    // different formats (the visual editor's Delta migration left that mixed state possible) --
    // check only whichever slot this write is actually about to land in, and only if that slot is
    // already occupied, so an empty draft/content-record can still be created in either format.
    Content existing = ContentRepository.findByUniqueId(contentUniqueId);
    if (existing != null) {
      boolean slotOccupied = willActuallyPublish ? existing.getContent() != null : existing.getDraftContent() != null;
      int existingFormat = willActuallyPublish ? existing.getContentFormat() : existing.getDraftContentFormat();
      if (slotOccupied && useDelta != (existingFormat == DeltaContentCommand.DELTA_FORMAT_VERSION)) {
        ServiceResponse response = new ServiceResponse(409);
        response.getError().put("title", "Content already exists in a different format");
        return response;
      }
    }

    Content saved;
    try {
      if (useDelta) {
        saved = SaveContentCommand.saveSafeDeltaContent(contentUniqueId, updateRequest.getContent(), context.getUserId(), willActuallyPublish);
      } else {
        saved = SaveContentCommand.saveSafeContent(contentUniqueId, updateRequest.getContent(), context.getUserId(), willActuallyPublish);
      }
    } catch (DataException e) {
      ServiceResponse response = new ServiceResponse(400);
      response.getError().put("title", e.getMessage());
      return response;
    }
    if (saved == null) {
      ServiceResponse response = new ServiceResponse(500);
      response.getError().put("title", "Content could not be saved");
      return response;
    }

    // Audit, mirroring ContentEditorWidget.post()'s exact three-way split so the audit trail reads
    // identically regardless of whether the edit came from the browser or the API.
    String actorUsername = context.getUser() != null ? context.getUser().getEmail() : null;
    String sourceIp = context.getRequest() != null ? context.getRequest().getRemoteAddr() : null;
    boolean gated = requestedPublish && !willActuallyPublish;
    if (gated) {
      SaveAuditEventCommand.recordAdminEvent("content", "content.publish", "failure",
          context.getUserId(), actorUsername, sourceIp, null,
          "content", String.valueOf(saved.getId()), contentUniqueId, "gated: saved as a draft for review");
    } else if (willActuallyPublish) {
      SaveAuditEventCommand.recordAdminEvent("content", "content.publish", "success",
          context.getUserId(), actorUsername, sourceIp, null,
          "content", String.valueOf(saved.getId()), contentUniqueId, null);
    } else {
      SaveAuditEventCommand.recordAdminEvent("content", "content.saveDraft", "success",
          context.getUserId(), actorUsername, sourceIp, null,
          "content", String.valueOf(saved.getId()), contentUniqueId, null);
    }

    ServiceResponse response = new ServiceResponse(200);
    ServiceResponseCommand.addMeta(response, "content");
    response.setData(new ContentUpdateResponse(contentUniqueId, willActuallyPublish, gated));
    return response;
  }

}
