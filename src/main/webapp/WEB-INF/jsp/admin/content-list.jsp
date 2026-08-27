<%--
  ~ Copyright 2022 SimIS Inc.
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  --%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="contentList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="contentUsageMap" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="contentStatusMap" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="sharedUniqueIds" class="java.util.LinkedHashSet" scope="request"/>
<jsp:useBean id="templatedContentLocations" class="java.util.LinkedHashMap" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>Every <strong>Content</strong> block on this site: a reusable, named chunk of HTML or rich text, each
    identified by its own <strong>Unique Id</strong> (the "Reference name" chosen when it was created in the
    "Add content" box). A Content block is not a page and not a blog post -- creating one here does not put it
    anywhere. It only appears on a live page once that page's layout includes a <code>content</code>-family
    widget (Content, Content Tabs, Content Cards, Content Accordion, Content Slider, Content Reveal, Content
    Gallery, or Content Carousel) configured to point at this exact Unique Id -- that wiring is done in the
    page designer or a filesystem page template, not here.</p>
  <p><strong>Unique Id</strong> is purely an internal lookup key, never a URL, so there is no route-collision
    risk with pages, blog posts, or anything else. It also is not scoped to "this form": typing an existing
    Unique Id in "Add content" opens that existing block for editing instead of creating a new one -- this page
    now warns before that happens rather than silently taking over a name another page may depend on.</p>
  <p><strong>Usage</strong> shows every page or site-wide template that currently references a block's Unique
    Id: <span class="label alert radius">Orphaned</span> means nothing currently references it;
    <span class="label secondary radius">Shared</span> means it is referenced from more than one place, or from
    a site-wide template (like the site footer) that is rendered on every page -- editing it changes more than
    just the one page you might be looking at; <span class="label warning radius">Templated</span> means its
    Unique Id matches a per-item template pattern (e.g. a product detail block, one per product) that cannot be
    verified as used by a static scan, but should not be assumed orphaned either.</p>
</div>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Search
        <input type="text" name="q" placeholder="unique id or content text" value="<c:out value='${q}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Modified from
        <input type="date" name="fromDate" value="<c:out value='${fromDate}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Modified to
        <input type="date" name="toDate" value="<c:out value='${toDate}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Characters
        <div class="input-group">
          <input type="number" min="0" name="minLength" placeholder="min" class="input-group-field" value="<c:out value='${minLength}'/>">
          <span class="input-group-label">&ndash;</span>
          <input type="number" min="0" name="maxLength" placeholder="max" class="input-group-field" value="<c:out value='${maxLength}'/>">
        </div>
      </label>
    </div>
    <div class="cell medium-2">
      <%-- Option values must match ContentReviewCommand.LIST_STATUS_* exactly (see ContentReviewCommand.listStatusLabel / ContentRepository#addStatusFilter) --%>
      <label>Status
        <select name="status">
          <option value="">All</option>
          <option value="Draft" <c:if test="${status == 'Draft'}">selected</c:if>>Draft</option>
          <option value="Pending Review" <c:if test="${status == 'Pending Review'}">selected</c:if>>Pending Review</option>
          <option value="Approved" <c:if test="${status == 'Approved'}">selected</c:if>>Approved</option>
          <option value="Live" <c:if test="${status == 'Live'}">selected</c:if>>Live</option>
        </select>
      </label>
    </div>
    <div class="cell medium-1">
      <label>&nbsp;</label>
      <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
      <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
    </div>
  </div>
</form>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Unique Id
      </th>
      <th width="130" class="text-center">
        Status
      </th>
      <th>
        Sample
      </th>
      <th width="100" class="text-center">
        # of characters
      </th>
      <th width="200" class="text-center">
        Last Modified
      </th>
      <th>
        Usage
      </th>
      <th width="60" class="text-center">
        Action
      </th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${contentList}" var="content">
    <c:set var="plainText" value="${html:text(content.content)}" />
    <c:set var="usageList" value="${contentUsageMap[content.uniqueId]}" />
    <c:set var="templatedLocations" value="${templatedContentLocations[content.uniqueId]}" />
    <c:set var="contentStatus" value="${contentStatusMap[content.uniqueId]}" />
    <tr>
      <td>
        <a href="${ctx}/content-editor?uniqueId=${content.uniqueId}&returnPage=/admin/content-list"><c:out value="${content.uniqueId}" /></a>
      </td>
      <td class="text-center">
        <%-- Read-only surfacing of the governed publish workflow's existing state (ContentReviewCommand); no submit/approve/reject actions here, those stay in the content editor --%>
        <c:choose>
          <c:when test="${contentStatus == 'Live'}">
            <span class="label success radius"><c:out value="${contentStatus}" /></span>
          </c:when>
          <c:when test="${contentStatus == 'Approved'}">
            <span class="label primary radius"><c:out value="${contentStatus}" /></span>
          </c:when>
          <c:when test="${contentStatus == 'Pending Review'}">
            <span class="label warning radius"><c:out value="${contentStatus}" /></span>
          </c:when>
          <c:otherwise>
            <span class="label radius"><c:out value="${contentStatus}" /></span>
          </c:otherwise>
        </c:choose>
      </td>
      <td><span class="subheader"><c:out value="${text:trim(plainText, 50, true)}" /></span></td>
      <td class="text-center"><fmt:formatNumber value="${fn:length(plainText)}" /></td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${content.modified}" /></td>
      <td>
        <c:choose>
          <c:when test="${!empty usageList}">
            <c:if test="${sharedUniqueIds.contains(content.uniqueId)}"><span class="label secondary radius">Shared</span> </c:if>
            <small class="subheader">Used on:
              <c:forEach items="${usageList}" var="location" varStatus="locStatus"><c:out value="${location}" /><c:if test="${!locStatus.last}">, </c:if></c:forEach>
            </small>
          </c:when>
          <c:when test="${!empty templatedLocations}">
            <span class="label warning radius" title="Matches a per-item template pattern -- cannot be verified as used by this page's scan, but is not safe to assume orphaned">Templated</span>
            <small class="subheader">Matches a template pattern in:
              <c:forEach items="${templatedLocations}" var="location" varStatus="locStatus"><c:out value="${location}" /><c:if test="${!locStatus.last}">, </c:if></c:forEach>
            </small>
          </c:when>
          <c:otherwise>
            <span class="label alert radius">Orphaned</span>
          </c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <%-- content-editor is intentionally excluded here: this page's own role gate in
             admin-layout.xml is admin/content-manager only, so a content-editor-only user can never
             reach this row to see the icon anyway. EditorPermissionCommand.canEditContent() (the
             actual gate on the delete action itself) already allows content-editor, so if this page
             is ever opened up to that role, the delete action will work with no further changes --
             only this visibility check and the help text below would need the role added back. --%>
        <c:if test="${userSession.hasRole('admin') || userSession.hasRole('content-manager')}">
          <a href="#" title="Delete this content block" data-confirm-post="Delete this content block? This cannot be undone." data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&uniqueId=${content.uniqueId}"><i class="fa fa-remove"></i></a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty contentList}">
      <tr>
        <td colspan="7">No content records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>

<h5>The draft, review, and publish workflow</h5>
<div class="callout radius">
  <p>Editing a block is always a draft/publish process inside the content editor (click a Unique Id above to
    open it). Whether a draft also needs a second person's approval before it can go live depends on the
    <code>content.review.required</code> site property, which is <strong>off by default</strong>. With it off,
    "Publish Immediately" in the content editor takes a draft straight to the live page with no review step.
    Turning it on changes that: a draft must be explicitly submitted for review, then approved (promoting it to
    live) or rejected back to the author by someone other than whoever submitted it -- the system enforces that
    the approver can never be the submitter, even between two people who both hold an approver role, and
    approval requires a step-up re-authentication (password or authenticator code) on top of being logged in.</p>
  <p>The Status column above reflects this, read-only -- no submit/approve/reject actions live on this page,
    those stay in the content editor: <span class="label success radius">Live</span> (no draft in progress, so
    what visitors see is exactly what's stored), <span class="label radius">Draft</span> (being edited, or sent
    back by a rejection), <span class="label warning radius">Pending Review</span> (submitted, awaiting a
    decision), <span class="label primary radius">Approved</span> (cleared for publish, but not yet published --
    that is still one more explicit action).</p>
</div>

<h5>How to fix common errors</h5>
<div class="callout warning radius">
  <p><strong>A page shows an "Add Content Here" button instead of real content</strong> -- the page's widget is
    correctly pointed at a Unique Id, but no Content row exists for it yet. Click that button (if you have edit
    permission), or come here and use "Add content" with that exact Unique Id, then write something and
    publish it.</p>
  <p><strong>Content was edited and published, but the live page still shows the old text (or nothing)</strong>
    -- by far the most common cause is a mismatched Unique Id: the page's widget XML references a slightly
    different spelling than the Content record that was actually edited (a typo, different casing, or an
    extra/missing dash). Check the exact Unique Id in the page designer against the one shown in this list --
    they have to match exactly, character for character.</p>
</div>

<h5>What to monitor</h5>
<div class="callout warning radius">
  <p><strong>A growing number of <span class="label alert radius">Orphaned</span> blocks</strong> over time
    usually means content that was removed from a page but never cleaned up here. Periodic review is worth it --
    each row's delete action (admin or content-manager only) removes a block for good, so
    confirm it is genuinely unused first (not just a <span class="label warning radius">Templated</span> match
    this page's scan cannot verify) before deleting.</p>
  <p><strong>This page getting slower as the site grows</strong> is a real, known cost, not a bug: the Usage
    column is computed fresh on every visit to this page by scanning every page's layout and every filesystem
    template -- it is not cached. A site with many pages and templates will feel that scan on every load of
    this page.</p>
  <p><strong>Content editors reporting they cannot publish</strong> once governed review is turned on -- check
    whether the submitter and the approver are configured as different users. Separation of duties is enforced
    in the code, not just policy: a submitter can never approve their own content, even if they also hold an
    approver role.</p>
</div>

<h5>For Azure</h5>
<div class="callout radius">
  <p>Content rows live in this app's own database (the <code>content</code> table in Postgres), so they are
    covered by normal Azure Database for PostgreSQL backup/restore -- no separate asset backup is needed for
    the text itself. The exception is content that references uploaded images: those are files, not database
    rows, and are a separate Images/Files backup concern.</p>
  <p>The full-text search index behind the Search box above (a <code>tsv</code> column with a GIN index)
    rebuilds automatically on every save via a database trigger -- there is nothing to provision or maintain
    separately for it, on Azure or anywhere else.</p>
  <p>Turning on <code>content.review.required</code> is a plain site-property toggle, not an infrastructure
    change -- no redeploy or Azure configuration is needed to turn governed review on or off.</p>
</div>
