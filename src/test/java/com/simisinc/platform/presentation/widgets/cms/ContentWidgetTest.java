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

package com.simisinc.platform.presentation.widgets.cms;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;

/**
 * @author matt rajkowski
 * @created 5/3/2022 7:00 PM
 */
class ContentWidgetTest extends WidgetBase {

  @Test
  void execute() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");
    // <p>${uniqueId:sample-content}</p>

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ContentWidget.JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
  }

  @Test
  void executeInLineContent() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>${uniqueId:another-content}");

    Content content2 = new Content();
    content2.setUniqueId("another-content");
    content2.setContent("<p>This is additional content</p>");

    // Execute the widget
    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("another-content")))
          .thenReturn(content2);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }
    Assertions.assertNotNull(widgetContext);
    Assertions.assertTrue(widgetContext.hasJsp());
    Assertions.assertEquals(ContentWidget.JSP, widgetContext.getJsp());
    Assertions.assertNotNull(request.getAttribute("contentHtml"));
    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertTrue(contentHtml.contains("Hello"));
    Assertions.assertTrue(contentHtml.contains("This is additional content"));
  }

  @Test
  void action() {
    // Set widget preferences
    preferences.put("uniqueId", "hello-content");

    // Widgets can have parameters
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    // Set the content the widget will use
    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p><hr><p>Card 2</p>");
    content.setDraftContent("<p>This is Card 1</p><hr><p>This is Card 2</p>");

    // Isolate every collaborator performWebAction() reaches -- including the permission and
    // site-property checks -- rather than relying on the default (no-role) fixture to deny
    // permission and short-circuit before them. That incidental short-circuit is what let this test
    // pass while quietly depending on CacheManager never being asked for a cache it was never
    // started to hold (issue #534): any future change to the default fixture's role list would have
    // sent this test straight into that gap instead of actually exercising the publish path.
    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      // Governed publishing off: a draft may be published directly, as it always could.
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.action(widgetContext);

      contentRepository.verify(() -> ContentRepository.publish(content));
    }
  }

  @Test
  void actionApproveIsNotHandledByTheGetActionPath() {
    // Content approval requires step-up re-authentication (see ContentWidget.post(), which routes
    // through ContentHtmlCommand.performContentApproval()). The GET/action() path must never approve
    // content directly -- that would bypass step-up entirely, reachable via a plain GET request.
    preferences.put("uniqueId", "hello-content");
    widgetContext.getParameterMap().put("action", new String[] { "approve" });

    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setDraftContent("<p>Draft</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      // Grant edit permission so the test proves the dispatch itself doesn't approve, not just that
      // permission was denied first.
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      new ContentWidget().action(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(any(), anyLong(), anyString()), never());
    }
  }

  @Test
  void postSaveDraftForwardsToActionAndPersistsContent() {
    // The inline visual editor's Save Draft button (platform-editor.js saveContentDraft()) submits
    // action=saveDraft via a real POST, so WebContainerContext routes the request to post(), not
    // action() above. Before issue #812 was fixed, post() only recognized "approve" and silently
    // fell through to `return context` for everything else -- the draft was never persisted and no
    // error was shown. This test calls post() directly, the same method a real request now reaches,
    // so it fails if that forwarding gap reopens.
    preferences.put("uniqueId", "hello-content");
    addQueryParameter(widgetContext, "action", "saveDraft");
    addQueryParameter(widgetContext, "html", "<p>Edited content</p>");

    Content content = new Content();
    content.setId(42L);
    content.setUniqueId("hello-content");
    content.setContent("<p>Original content</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<SaveContentCommand> saveContent = mockStatic(SaveContentCommand.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      // performWebAction() unconditionally reads this site property before branching on the action
      // name, even though saveDraft never uses it -- must be stubbed regardless, same as action().
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.post(widgetContext);

      saveContent.verify(
          () -> SaveContentCommand.saveSafeContent(eq("hello-content"), eq("<p>Edited content</p>"), anyLong(), eq(false)));
      // The mocked AuditEventCommand above only prevents the real static method from running during
      // the test -- it doesn't prove saveDraft() actually recorded an event. Verify the call
      // ContentHtmlCommand.saveDraft() makes, same shape as the delete-path checks in
      // BlogPostWidgetTest/WebPageFormWidgetTest.
      auditEvent.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.saveDraft"),
          eq(AuditEventCommand.SUCCESS), eq("content"), eq("42"), eq("hello-content"), any()), times(1));
      Assertions.assertTrue(widgetContext.hasJson());
      Assertions.assertTrue(widgetContext.getJson().contains("\"success\":true"));
    }
  }
}