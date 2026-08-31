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

import java.sql.Timestamp;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.DataException;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentHtmlCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.EditorPermissionCommand;
import com.simisinc.platform.application.cms.LoadContentCommand;
import com.simisinc.platform.application.cms.LoadWebPageCommand;
import com.simisinc.platform.application.cms.SaveContentCommand;
import com.simisinc.platform.application.login.StepUpAuthCommand;
import com.simisinc.platform.domain.model.cms.Content;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.cms.ContentRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
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

  /**
   * Issue: the optional "Last updated" line. The record's own timestamp is exposed only when the
   * record's html is what ends up on screen.
   */
  @Test
  void aContentRecordExposesItsOwnModifiedDate() {
    preferences.put("uniqueId", "hello-content");
    preferences.put("showLastUpdated", "true");

    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");
    Timestamp modified = Timestamp.valueOf("2026-08-14 09:30:00");
    content.setModified(modified);

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      widgetContext = new ContentWidget().execute(widgetContext);
    }
    Assertions.assertEquals(modified, request.getAttribute("contentModified"),
        "the record supplied the html, so its own date is the one to report");
    Assertions.assertEquals("true", request.getAttribute("showLastUpdated"));
  }

  /**
   * The empty-record fallback (issue 1689): the record exists but is blank, so the html on screen
   * comes from the page XML instead. Reporting the record's date here would put a date on the page
   * that has nothing to do with when that html was last edited, so it must be cleared -- the JSP
   * then falls through to the page's own modified date.
   */
  @Test
  void anEmptyRecordDoesNotLendItsDateToTheInlineHtmlThatReplacesIt() {
    preferences.put("uniqueId", "hello-content");
    preferences.put("showLastUpdated", "true");
    preferences.put("html", "<p>Inline from the page XML</p>");

    Content emptyRecord = new Content();
    emptyRecord.setUniqueId("hello-content");
    emptyRecord.setContent("");
    emptyRecord.setModified(Timestamp.valueOf("2020-01-01 00:00:00"));

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(emptyRecord);
      widgetContext = new ContentWidget().execute(widgetContext);
    }
    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertTrue(contentHtml.contains("Inline from the page XML"),
        "precondition: the inline html is what is being rendered");
    Assertions.assertNull(request.getAttribute("contentModified"),
        "the record did not supply the html, so its date must not be reported for it");
  }

  @Test
  void theLastUpdatedLineIsOffUnlessThePageAsksForIt() {
    preferences.put("uniqueId", "hello-content");

    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");
    content.setModified(Timestamp.valueOf("2026-08-14 09:30:00"));

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(content);
      widgetContext = new ContentWidget().execute(widgetContext);
    }
    Assertions.assertNull(request.getAttribute("showLastUpdated"),
        "no preference means no line -- existing pages must not sprout a date they did not ask for");
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
  void executeExposesAReusabilityWarningWhenTheDraftPublishConfirmIsShownForASharedBlock() {
    // #499 slice 2: the DRAFT badge's "Publish this content?" confirm (reviewOffer == 'publish',
    // ungoverned direct publish) affects every page/template that references this uniqueId --
    // warn with the real list before it happens.
    preferences.put("uniqueId", "cmmc-header");

    Content content = new Content();
    content.setUniqueId("cmmc-header");
    content.setContent("<p>Live</p>");
    content.setDraftContent("<p>Draft</p>");

    WebPage careers = new WebPage();
    careers.setLink("/careers");
    careers.setPageXml(
        "<page><section><column><widget name=\"content\"><uniqueId>cmmc-header</uniqueId></widget></column></section></page>");
    WebPage aboutUs = new WebPage();
    aboutUs.setLink("/about-us");
    aboutUs.setPageXml(
        "<page><section><column><widget name=\"content\"><uniqueId>cmmc-header</uniqueId></widget></column></section></page>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("cmmc-header"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      // Governed publishing off -> offerFor() returns OFFER_PUBLISH, the state that actually
      // renders the DRAFT badge's confirm in content.jsp.
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(careers, aboutUs));

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);

      Assertions.assertEquals(ContentReviewCommand.OFFER_PUBLISH, request.getAttribute("reviewOffer"));
      String warning = (String) request.getAttribute("reusabilityWarning");
      Assertions.assertNotNull(warning, "a block used on 2 pages must produce a warning");
      Assertions.assertTrue(warning.contains("2 pages"), warning);
      Assertions.assertTrue(warning.contains("/careers"), warning);
      Assertions.assertTrue(warning.contains("/about-us"), warning);
    }
  }

  @Test
  void executeDoesNotExposeAWarningWhenTheSharedBlockIsUsedOnAtMostOnePage() {
    preferences.put("uniqueId", "solo-header");

    Content content = new Content();
    content.setUniqueId("solo-header");
    content.setContent("<p>Live</p>");
    content.setDraftContent("<p>Draft</p>");

    WebPage careers = new WebPage();
    careers.setLink("/careers");
    careers.setPageXml(
        "<page><section><column><widget name=\"content\"><uniqueId>solo-header</uniqueId></widget></column></section></page>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("solo-header"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);
      webPageRepository.when(WebPageRepository::findAll).thenReturn(List.of(careers));

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);

      Assertions.assertEquals(ContentReviewCommand.OFFER_PUBLISH, request.getAttribute("reviewOffer"));
      assertNull(request.getAttribute("reusabilityWarning"));
    }
  }

  @Test
  void executeSkipsTheUsageScanWhenNoPublishConfirmWillBeShown() {
    // Cost containment: ContentUsageCommand#findUsageMap is a bulk scan of every page and
    // filesystem template (#499 slice 1). It must only run for the one reviewOffer state that
    // actually renders the confirm it feeds (OFFER_PUBLISH) -- not on every content-widget render
    // on every page view. Here there is no draft at all, so offerFor() returns OFFER_NONE.
    preferences.put("uniqueId", "hello-content");

    Content content = new Content();
    content.setUniqueId("hello-content");
    content.setContent("<p>Hello</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);

      webPageRepository.verify(WebPageRepository::findAll, never());
      Assertions.assertEquals(ContentReviewCommand.OFFER_NONE, request.getAttribute("reviewOffer"));
      assertNull(request.getAttribute("reusabilityWarning"));
    }
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
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        // #420: publishContent() now also looks up the current page (to purge its AFD cache) after
        // a successful publish -- isolate that lookup here too, same reasoning as every other
        // collaborator in this list: otherwise it falls through to a real, unmocked DB call.
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      // Governed publishing off: a draft may be published directly, as it always could.
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.action(widgetContext);

      contentRepository.verify(() -> ContentRepository.publish(eq(content), anyInt()));
    }
  }

  @Test
  void actionPublishTriggersCachePurgeForTheCurrentPage() {
    // #420: ContentHtmlCommand.publishContent() is the actual "Publish" handler reached from
    // ContentWidget (and its six siblings) through the standard editor UI -- unlike the WebPage-level
    // hooks already wired for #420, this path never triggered an AFD purge. The widget submits its
    // publish action back to widgetContext.uri (see content.jsp), which WidgetBase stubs to
    // "/example/path" by default -- that's the page whose cache must be invalidated.
    preferences.put("uniqueId", "hello-content");
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    Content content = new Content();
    content.setId(11L);
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p><hr><p>Card 2</p>");
    content.setDraftContent("<p>This is Card 1</p><hr><p>This is Card 2</p>");

    WebPage webPage = new WebPage();
    webPage.setId(9L);
    webPage.setLink("/example/path");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink("/example/path")).thenReturn(webPage);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.action(widgetContext);

      contentRepository.verify(() -> ContentRepository.publish(eq(content), anyInt()));
      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(webPage));
    }
  }

  @Test
  void actionPublishSkipsThePurgeWhenTheCurrentUriIsNotAKnownPage() {
    // Defensive: if the current page can't be resolved to a WebPage record (shouldn't normally
    // happen for a widget action, but must not NPE or otherwise blow up the publish), no purge call
    // is attempted.
    preferences.put("uniqueId", "hello-content");
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    Content content = new Content();
    content.setId(11L);
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p>");
    content.setDraftContent("<p>This is Card 1</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink("/example/path")).thenReturn(null);

      ContentWidget contentWidget = new ContentWidget();
      assertDoesNotThrow(() -> contentWidget.action(widgetContext));

      purge.verifyNoInteractions();
    }
  }

  @Test
  void gatedPublishNeverTriggersAPurge() {
    // With governed publishing on, an unapproved draft is blocked before ContentRepository.publish()
    // is ever reached -- nothing changed on the live page, so no purge should fire either.
    preferences.put("uniqueId", "hello-content");
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    Content content = new Content();
    content.setId(11L);
    content.setUniqueId("hello-content");
    content.setDraftContent("<p>This is Card 1</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.mayPublish(eq(content), eq(true))).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.action(widgetContext);

      purge.verifyNoInteractions();
    }
  }

  @Test
  void publishingContentDoesNotBlockWhenAfdIsUnconfigured() {
    // #420: PublishEventCachePurgeHandlerTest already proves the "AFD not configured" skip path
    // never throws and returns fast, in isolation. This proves that same real (unmocked) handler
    // code is safely reachable end-to-end from ContentWidget's publish action -- LoadWebPageCommand
    // is mocked only to keep this a unit test (no live DB), PublishEventCachePurgeHandler itself is
    // deliberately left unmocked so its real skip-gracefully/non-blocking behavior actually runs.
    Assumptions.assumeTrue(System.getenv("AZURE_FRONTDOOR_PROFILE_NAME") == null
        && System.getenv("AZURE_FRONTDOOR_RESOURCE_GROUP") == null
        && System.getenv("AZURE_FRONTDOOR_ENDPOINT_NAME") == null
        && System.getenv("AZURE_SUBSCRIPTION_ID") == null,
        "AFD env vars are set in this environment; the unconfigured-skip path can't be exercised here");

    preferences.put("uniqueId", "hello-content");
    widgetContext.getParameterMap().put("action", new String[] { "publish" });

    Content content = new Content();
    content.setId(11L);
    content.setUniqueId("hello-content");
    content.setContent("<p>Card 1</p>");
    content.setDraftContent("<p>This is Card 1</p>");

    WebPage webPage = new WebPage();
    webPage.setId(9L);
    webPage.setLink("/example/path");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink("/example/path")).thenReturn(webPage);

      ContentWidget contentWidget = new ContentWidget();
      long start = System.currentTimeMillis();
      assertDoesNotThrow(() -> contentWidget.action(widgetContext));
      long elapsed = System.currentTimeMillis() - start;

      contentRepository.verify(() -> ContentRepository.publish(eq(content), anyInt()));
      assertTrue(elapsed < 2000L,
          "Expected the unconfigured AFD purge path to return fast, took " + elapsed + "ms");
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
  void approvingContentTriggersCachePurgeForTheCurrentPage() {
    // #420: ContentHtmlCommand.approveContent() (reached via performContentApproval(), the step-up
    // gated "Approve" handler ContentWidget.post() routes to) is the other content-level publish
    // path that never triggered an AFD purge. Exercised directly through the public entry point
    // rather than ContentWidget.post(), which also needs execute() to have already put a JSP on the
    // context -- performContentApproval()'s own contract (see its Javadoc).
    preferences.put("uniqueId", "hello-content");
    addQueryParameter(widgetContext, "releaseReference", "CR-1234");

    Content content = new Content();
    content.setId(21L);
    content.setUniqueId("hello-content");
    content.setDraftContent("<p>Approved content</p>");

    WebPage webPage = new WebPage();
    webPage.setId(9L);
    webPage.setLink("/example/path");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<LoadSitePropertyCommand> siteProperty = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<LoadWebPageCommand> loadWebPage = mockStatic(LoadWebPageCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      // Step-up already satisfied for this session -- the approval itself is what's under test here.
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      loadWebPage.when(() -> LoadWebPageCommand.loadByLink("/example/path")).thenReturn(webPage);
      // #406: approveContent() now resolves content.versionHistoryLimit before publishing.
      siteProperty.when(() -> LoadSitePropertyCommand.loadByName(anyString())).thenReturn(null);

      ContentHtmlCommand.performContentApproval(widgetContext);

      contentReview.verify(() -> ContentReviewCommand.approve(content, widgetContext.getUserId(), "CR-1234"));
      contentRepository.verify(() -> ContentRepository.publish(eq(content), anyInt()));
      purge.verify(() -> PublishEventCachePurgeHandler.onPageUpdated(webPage));
      Assertions.assertEquals("The content was approved and published", widgetContext.getSuccessMessage());
    }
  }

  @Test
  void approvalRequiringStepUpNeverTriggersAPurge() {
    // No step-up credential supplied yet -- approveContent() must not run at all, so nothing was
    // published and there is nothing to purge.
    preferences.put("uniqueId", "hello-content");

    Content content = new Content();
    content.setId(21L);
    content.setUniqueId("hello-content");
    content.setDraftContent("<p>Approved content</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(false);

      ContentHtmlCommand.performContentApproval(widgetContext);

      contentRepository.verify(() -> ContentRepository.publish(any(), anyInt()), never());
      purge.verifyNoInteractions();
    }
  }

  @Test
  void failedApprovalNeverTriggersAPurge() {
    // ContentReviewCommand.approve() enforces separation of duties and can reject the approval (e.g.
    // the approver was also the submitter) -- when it throws, ContentRepository.publish() is never
    // reached, so nothing changed on the live page and no purge should fire.
    preferences.put("uniqueId", "hello-content");

    Content content = new Content();
    content.setId(21L);
    content.setUniqueId("hello-content");
    content.setDraftContent("<p>Approved content</p>");

    try (MockedStatic<LoadContentCommand> loadContent = mockStatic(LoadContentCommand.class);
        MockedStatic<EditorPermissionCommand> editorPermission = mockStatic(EditorPermissionCommand.class);
        MockedStatic<StepUpAuthCommand> stepUpAuth = mockStatic(StepUpAuthCommand.class);
        MockedStatic<ContentReviewCommand> contentReview = mockStatic(ContentReviewCommand.class);
        MockedStatic<ContentRepository> contentRepository = mockStatic(ContentRepository.class);
        MockedStatic<AuditEventCommand> auditEvent = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      loadContent.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content"))).thenReturn(content);
      editorPermission.when(() -> EditorPermissionCommand.canEditContent(any())).thenReturn(true);
      stepUpAuth.when(() -> StepUpAuthCommand.isValid(any())).thenReturn(true);
      contentReview.when(() -> ContentReviewCommand.approve(eq(content), anyLong(), any()))
          .thenThrow(new DataException("You cannot approve your own submission"));

      ContentHtmlCommand.performContentApproval(widgetContext);

      contentRepository.verify(() -> ContentRepository.publish(any(), anyInt()), never());
      purge.verifyNoInteractions();
      Assertions.assertEquals("You cannot approve your own submission", widgetContext.getErrorMessage());
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

  @Test
  void postSaveDraftSurfacesAccessibilityFindingsWhenPresent() {
    // #258: ContentAccessibilityCommand is wired into this save path as a non-blocking, purely
    // additive author-facing notice. The save itself (verified above by the other saveDraft test)
    // is unaffected either way -- this only proves the JSON response gets enriched when the saved
    // content has a real, checkable violation.
    preferences.put("uniqueId", "hello-content");
    addQueryParameter(widgetContext, "action", "saveDraft");
    addQueryParameter(widgetContext, "html", "<p>Text</p><img src=\"/assets/foo.jpg\">");

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
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.post(widgetContext);

      Assertions.assertTrue(widgetContext.hasJson());
      String json = widgetContext.getJson();
      Assertions.assertTrue(json.contains("\"success\":true"), json);
      Assertions.assertTrue(json.contains("\"a11yFindings\""), json);
      Assertions.assertTrue(json.contains("image-missing-alt"), json);
      Assertions.assertTrue(json.contains("WCAG 1.1.1"), json);
    }
  }

  @Test
  void postSaveDraftOmitsAccessibilityFindingsWhenContentIsClean() {
    // The flip side of the above: a clean save must not add the field at all. Existing clients
    // (platform-editor.js) only read success/error and must keep working unchanged.
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
      siteProperty.when(() -> LoadSitePropertyCommand.loadByNameAsBoolean(anyString())).thenReturn(false);

      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.post(widgetContext);

      Assertions.assertTrue(widgetContext.hasJson());
      String json = widgetContext.getJson();
      Assertions.assertEquals("{\"success\":true}", json);
      Assertions.assertFalse(json.contains("a11yFindings"), json);
    }
  }

  @Test
  void anEmptyContentRecordFallsBackToTheInlineHtmlInsteadOfBlankingTheSection() {
    // The page XML shape that made this destructive: a content widget carrying BOTH a uniqueId and
    // inline html. Four shipped pages use it (/admin/useful-links, /admin/sticky-footer-links,
    // /validate-account, /validation-sent), and /content-editor opens EMPTY for such a uniqueId
    // because no record exists yet, so one Save there stored an empty record. The resolver's
    // fallback was guarded on html == null; an empty record resolves to "" and skipped it, so the
    // widget rendered nothing and the inline html could never be reached again (issue 1689).
    preferences.put("uniqueId", "hello-content");
    preferences.put("html", "<p>Declared in the page layout</p>");

    Content emptyRecord = new Content();
    emptyRecord.setUniqueId("hello-content");
    emptyRecord.setContent("");

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(emptyRecord);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }

    Assertions.assertNotNull(widgetContext, "the widget must still render, not be dropped");
    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertNotNull(contentHtml);
    Assertions.assertTrue(contentHtml.contains("Declared in the page layout"), contentHtml);
  }

  @Test
  void aPopulatedContentRecordStillOverridesTheInlineHtml() {
    // The other half of the contract: the fallback must only apply when the record has nothing to
    // show. A saved record is an override and has to keep winning over the page-layout default.
    preferences.put("uniqueId", "hello-content");
    preferences.put("html", "<p>Declared in the page layout</p>");

    Content saved = new Content();
    saved.setUniqueId("hello-content");
    saved.setContent("<p>Edited in the content editor</p>");

    try (MockedStatic<LoadContentCommand> staticLoadContentCommand = mockStatic(LoadContentCommand.class)) {
      staticLoadContentCommand.when(() -> LoadContentCommand.loadContentByUniqueId(eq("hello-content")))
          .thenReturn(saved);
      ContentWidget contentWidget = new ContentWidget();
      widgetContext = contentWidget.execute(widgetContext);
    }

    String contentHtml = (String) request.getAttribute("contentHtml");
    Assertions.assertNotNull(contentHtml);
    Assertions.assertTrue(contentHtml.contains("Edited in the content editor"), contentHtml);
    Assertions.assertFalse(contentHtml.contains("Declared in the page layout"), contentHtml);
  }
}
