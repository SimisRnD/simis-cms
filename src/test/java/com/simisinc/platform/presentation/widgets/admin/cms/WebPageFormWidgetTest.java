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

package com.simisinc.platform.presentation.widgets.admin.cms;

import com.simisinc.platform.WidgetBase;
import com.simisinc.platform.application.admin.LoadSitePropertyCommand;
import com.simisinc.platform.application.cms.ContentReviewCommand;
import com.simisinc.platform.application.cms.InternalPageAccessCommand;
import com.simisinc.platform.application.cms.SaveWebPageCommand;
import com.simisinc.platform.domain.model.Group;
import com.simisinc.platform.domain.model.cms.SolutionTypeOptions;
import com.simisinc.platform.domain.model.cms.WebPage;
import com.simisinc.platform.infrastructure.cache.PublishEventCachePurgeHandler;
import com.simisinc.platform.infrastructure.persistence.GroupRepository;
import com.simisinc.platform.infrastructure.persistence.cms.WebPageRepository;
import com.simisinc.platform.presentation.controller.AuditEventCommand;
import com.simisinc.platform.presentation.controller.WidgetContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

/**
 * deletePageViaPostCallsRepositoryAndAudits guards a real regression: the page editor's delete button submits
 * via a real HTTP POST (issue #358 moved state-changing admin actions off GET query strings), so
 * WebContainerContext routes the request to post(), not action() below -- action()'s "deletePage" dispatch
 * (and its admin-role check) was correct but unreachable, and post() never checked the action parameter, so
 * it fell through to the page-save logic instead. That logic reads an "id" parameter a deletePage request
 * never sends (it sends "webPageId"), so it built a blank WebPage, failed link validation, and replaced the
 * click with a "Please check the form and try again" error plus a bogus content.unpublish failure audit
 * record -- the page was never deleted. This test calls post() directly, the same method a real request now
 * reaches, so it fails if that dispatch gap reopens.
 */
class WebPageFormWidgetTest extends WidgetBase {

  @Test
  void deletePageViaPostCallsRepositoryAndAudits() throws Exception {
    setRoles(widgetContext, ADMIN);

    WebPage webPage = new WebPage();
    webPage.setId(7L);
    webPage.setLink("/about");
    webPage.setTitle("About Us");

    addQueryParameter(widgetContext, "webPageId", "7");
    addQueryParameter(widgetContext, "action", "deletePage");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class);
        MockedStatic<PublishEventCachePurgeHandler> purge = mockStatic(PublishEventCachePurgeHandler.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(anyLong())).thenReturn(webPage);

      WidgetContext result = new WebPageFormWidget().post(widgetContext);

      webPageRepository.verify(() -> WebPageRepository.remove(webPage), times(1));
      audit.verify(() -> AuditEventCommand.record(any(), eq(AuditEventCommand.CONTENT), eq("content.delete"),
          eq(AuditEventCommand.SUCCESS), eq("web_page"), eq("7"), eq("About Us"), any()), times(1));
      Assertions.assertEquals("Page was deleted", result.getSuccessMessage());
      // #420: a deleted page must also drop out of the AFD edge cache, not just the DB
      purge.verify(() -> PublishEventCachePurgeHandler.onPageDeleted("/about"), times(1));
    }
  }

  /**
   * Guards issue #570's admin form dropdown: execute() (the GET path) must publish
   * SolutionTypeOptions.map as the "solutionTypeMap" request attribute, which
   * web-page-form.jsp reads to populate the Solution Type select. This was previously only
   * exercised by manual live-verification captures, not by an automated test -- if a future
   * refactor of execute() drops or renames the attribute, the dropdown would silently render
   * empty and CI would not catch it.
   */
  @Test
  void executeSetsSolutionTypeMapForAdminFormDropdown() {
    WebPage webPage = new WebPage();
    webPage.setId(42L);
    webPage.setLink("/solutions/widget-management");
    webPage.setTitle("Widget Management");

    addQueryParameter(widgetContext, "webPageId", "42");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(42L)).thenReturn(webPage);
      property.when(() -> LoadSitePropertyCommand.loadByName(InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP))
          .thenReturn("");

      WidgetContext result = new WebPageFormWidget().execute(widgetContext);

      Assertions.assertSame(SolutionTypeOptions.map, result.getRequest().getAttribute("solutionTypeMap"));
      Assertions.assertEquals(webPage, result.getRequest().getAttribute("webPage"));
    }
  }

  /**
   * Issue #1688: the form states what ticking "Internal" will actually do, because the setting that
   * gives the flag its teeth lives on a page (/admin/security-properties, role="admin") that a
   * content-manager editing this form cannot open -- and they get no symptom of their own either,
   * since the content-editor tier bypasses the gate.
   */
  @Test
  void executeStatesWhatTheInternalFlagCurrentlyDoes() {
    WebPage webPage = new WebPage();
    webPage.setId(42L);
    webPage.setLink("/employee-handbook");
    addQueryParameter(widgetContext, "webPageId", "42");

    Group staff = new Group();
    staff.setUniqueId("all-employees");
    staff.setName("All Employees");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<LoadSitePropertyCommand> property = mockStatic(LoadSitePropertyCommand.class);
        MockedStatic<GroupRepository> groupRepository = mockStatic(GroupRepository.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(42L)).thenReturn(webPage);
      groupRepository.when(() -> GroupRepository.findByUniqueId("all-employees")).thenReturn(staff);

      // Not configured: the flag is a label, and the form has to say so rather than implying access control
      property.when(() -> LoadSitePropertyCommand.loadByName(InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP))
          .thenReturn("");
      Assertions.assertEquals("restricts nobody until a group is chosen in Security Settings",
          new WebPageFormWidget().execute(widgetContext).getRequest().getAttribute("internalEffect"));

      // Configured and resolvable: name the audience
      property.when(() -> LoadSitePropertyCommand.loadByName(InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP))
          .thenReturn("all-employees");
      Assertions.assertEquals("viewable only by All Employees, plus content editors",
          new WebPageFormWidget().execute(widgetContext).getRequest().getAttribute("internalEffect"));

      // Configured but the group is gone: this fails closed, so the form must not imply it is fine
      property.when(() -> LoadSitePropertyCommand.loadByName(InternalPageAccessCommand.PROPERTY_INTERNAL_PAGE_GROUP))
          .thenReturn("deleted-group");
      Assertions.assertEquals(
          "restricts everyone except content editors -- Security Settings names a group that no longer exists",
          new WebPageFormWidget().execute(widgetContext).getRequest().getAttribute("internalEffect"));
    }
  }

  /**
   * The governed publish workflow (#407) fields must never be settable through this generic form
   * save: post() calls BeanUtils.populate(webPageBean, context.getParameterMap()) against the
   * entire raw request map, so without an explicit guard a crafted POST could set e.g.
   * approvedBy=&lt;attacker's own id&gt; directly and bypass separation-of-duties and step-up
   * re-authentication entirely -- the same class of mass-assignment gap fixed for #492/#730. This
   * proves the guard: a page mid-review (submitted, awaiting a named approver) keeps its exact
   * review state through a save that also tries to inject different values for every one of those
   * fields.
   */
  @Test
  void postCannotInjectGovernedWorkflowFieldsViaFormSave() throws Exception {
    setRoles(widgetContext, ADMIN);

    WebPage existing = new WebPage();
    existing.setId(7L);
    existing.setLink("/about");
    existing.setTitle("About Us");
    existing.setDraftStatus(ContentReviewCommand.STATUS_SUBMITTED);
    existing.setSubmittedBy(5L);
    existing.setApprovedBy(-1L);
    existing.setReleaseReference(null);

    addQueryParameter(widgetContext, "id", "7");
    addQueryParameter(widgetContext, "link", "/about");
    addQueryParameter(widgetContext, "title", "About Us");
    // The attack: try to self-approve by injecting every governed-workflow field directly.
    addQueryParameter(widgetContext, "draftStatus", "approved");
    addQueryParameter(widgetContext, "submittedBy", "999");
    addQueryParameter(widgetContext, "approvedBy", "999");
    addQueryParameter(widgetContext, "releaseReference", "forged");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(7L)).thenReturn(existing);
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      new WebPageFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(argThat(bean ->
          ContentReviewCommand.STATUS_SUBMITTED.equals(bean.getDraftStatus())
              && bean.getSubmittedBy() == 5L
              && bean.getApprovedBy() == -1L
              && bean.getReleaseReference() == null)));
    }
  }

  /**
   * Issue #957: this widget has no content field of its own (web-page-form.jsp is metadata only --
   * title, keywords, sitemap settings, publish/expire dates), but BeanUtils.populate() walks the
   * *raw* HTTP parameter map, not the JSP's actual field set. Without capturing/restoring
   * pageXml/draftPageXml the same way the four governed-workflow fields above are guarded, a crafted
   * "pageXml" POST parameter would write straight to the live page through this widget's
   * unconditional SaveWebPageCommand.saveWebPage() call -- the same review bypass #957 closes in
   * WebPageDesignerWidget, reachable here too since this widget never even checks
   * webPage.review.required. This proves the guard: a save that tries to inject new content for both
   * fields leaves an existing page's real content completely untouched.
   */
  @Test
  void postCannotInjectPageContentViaFormSave() throws Exception {
    setRoles(widgetContext, ADMIN);

    WebPage existing = new WebPage();
    existing.setId(7L);
    existing.setLink("/about");
    existing.setTitle("About Us");
    existing.setPageXml("<page><section><column><widget name=\"content\"><uniqueId>reviewed-and-live</uniqueId></widget></column></section></page>");
    existing.setDraftPageXml(null);

    addQueryParameter(widgetContext, "id", "7");
    addQueryParameter(widgetContext, "link", "/about");
    addQueryParameter(widgetContext, "title", "About Us");
    // The attack: forge the content fields directly, bypassing WebPageDesignerWidget entirely.
    addQueryParameter(widgetContext, "pageXml", "<page><section><column><widget name=\"content\"><uniqueId>injected</uniqueId></widget></column></section></page>");
    addQueryParameter(widgetContext, "draftPageXml", "<page><section><column><widget name=\"content\"><uniqueId>injected-draft</uniqueId></widget></column></section></page>");

    try (MockedStatic<WebPageRepository> webPageRepository = mockStatic(WebPageRepository.class);
        MockedStatic<SaveWebPageCommand> saveCommand = mockStatic(SaveWebPageCommand.class);
        MockedStatic<AuditEventCommand> audit = mockStatic(AuditEventCommand.class)) {
      webPageRepository.when(() -> WebPageRepository.findById(7L)).thenReturn(existing);
      saveCommand.when(() -> SaveWebPageCommand.saveWebPage(any(WebPage.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      new WebPageFormWidget().post(widgetContext);

      saveCommand.verify(() -> SaveWebPageCommand.saveWebPage(argThat(bean ->
          bean.getPageXml().contains("reviewed-and-live")
              && bean.getDraftPageXml() == null)));
    }
  }
}
