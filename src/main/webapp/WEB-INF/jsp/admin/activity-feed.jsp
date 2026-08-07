<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="activityList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>

<%-- One color per audit-log category (issue #1006), reused identically for the filter chips below and the
     per-row badges in the table -- consistent with each other, and picked so the same palette still makes
     sense if this is later applied to the full Security Audit Log (that page's own UI is untouched here;
     see the issue). Foundation's built-in .label colors (primary/secondary/success/warning/alert) only
     cover five hues, and success (green) / alert (red) already mean something specific elsewhere on this
     page -- the "failed" tag on a row reuses that same alert red -- so neither is reused as a category
     color; a chip could otherwise be misread as a pass/fail signal. The six hues below were validated with
     Anthropic's dataviz-skill palette validator (OKLab CVD-simulated pairwise separation): they clear every
     check as a set in light mode, and clear all but one adjacent pair in dark mode (a residual violet/blue
     closeness -- six always-simultaneous categories exceed what any hue set can guarantee pairwise-distinct
     under every color-vision type at once; the validator's own reference palette documents this as a hard
     limit past three simultaneous colors, not a defect in this particular choice). That residual case is
     why every chip and badge below always renders the category's name as text alongside the color -- the
     color reinforces, text is what actually disambiguates. Dark-mode values follow this codebase's existing
     [data-theme] convention (see css/platform-tokens.css) rather than plain prefers-color-scheme alone, so
     a viewer's explicit theme choice always wins over the OS default. --%>
<style>
  .activity-cat-chip {
    display: inline-flex;
    align-items: center;
    gap: 0.35em;
    padding: 0.25em 0.75em;
    margin: 0 0.35em 0.35em 0;
    border-radius: 1em;
    border: 1px solid rgba(0, 0, 0, 0.15);
    cursor: pointer;
    font-size: 0.85rem;
  }
  .activity-cat-chip input {
    margin: 0;
  }
  .activity-cat-badge {
    display: inline-block;
    padding: 0.2em 0.6em;
    border-radius: 1em;
    font-size: 0.75rem;
    font-weight: bold;
    white-space: nowrap;
  }
  .activity-cat-authentication { background: #2a78d6; color: #000; }
  .activity-cat-authorization  { background: #4a3aa7; color: #fff; }
  .activity-cat-user_management { background: #1baf7a; color: #000; }
  .activity-cat-configuration  { background: #eda100; color: #000; }
  .activity-cat-content        { background: #008300; color: #fff; }
  .activity-cat-data_access    { background: #e87ba4; color: #000; }
  :root[data-theme="dark"] .activity-cat-authentication  { background: #3987e5; color: #000; }
  :root[data-theme="dark"] .activity-cat-authorization   { background: #9085e9; color: #000; }
  :root[data-theme="dark"] .activity-cat-user_management { background: #199e70; color: #000; }
  :root[data-theme="dark"] .activity-cat-configuration   { background: #c98500; color: #000; }
  :root[data-theme="dark"] .activity-cat-content         { background: #008300; color: #fff; }
  :root[data-theme="dark"] .activity-cat-data_access     { background: #d55181; color: #000; }
  @media (prefers-color-scheme: dark) {
    :root[data-theme="auto"] .activity-cat-authentication  { background: #3987e5; color: #000; }
    :root[data-theme="auto"] .activity-cat-authorization   { background: #9085e9; color: #000; }
    :root[data-theme="auto"] .activity-cat-user_management { background: #199e70; color: #000; }
    :root[data-theme="auto"] .activity-cat-configuration   { background: #c98500; color: #000; }
    :root[data-theme="auto"] .activity-cat-content         { background: #008300; color: #fff; }
    :root[data-theme="auto"] .activity-cat-data_access     { background: #d55181; color: #000; }
  }
</style>

<%-- Day-range selector: default trailing 7 days, widened here -- mirrors AuditLogListWidget's own quick
     range preset buttons (audit-log-list.jsp) at day instead of hour granularity, since a week is this
     feed's baseline rather than an hour. Same limitation as that page's presets, kept for consistency
     rather than fixed here: picking a preset is a fresh link, so it resets the category filter below
     exactly like AuditLogListWidget's own range buttons reset its other filters. --%>
<p class="text-right"><small class="subheader">Showing activity from the last <c:out value="${windowDays}"/> days.</small></p>
<div class="button-group tiny">
  <a href="${widgetContext.uri}?range=7d" class="button ${range eq '7d' ? 'primary' : 'secondary'}">Last 7 days</a>
  <a href="${widgetContext.uri}?range=14d" class="button ${range eq '14d' ? 'primary' : 'secondary'}">Last 14 days</a>
  <a href="${widgetContext.uri}?range=30d" class="button ${range eq '30d' ? 'primary' : 'secondary'}">Last 30 days</a>
  <a href="${widgetContext.uri}?range=90d" class="button ${range eq '90d' ? 'primary' : 'secondary'}">Last 90 days</a>
</div>

<%-- Category filter: checkboxes/chips (issue #1006), reusing AuditLogSpecification's multi-category
     filtering. GET so the selection lives in the URL and paging preserves it, mirroring
     AuditLogListWidget's own filter form. No box checked means "show every category". --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <input type="hidden" name="range" value="<c:out value='${range}'/>"/>
  <div>
    <c:forEach items="${categoryList}" var="cat">
      <label class="activity-cat-chip activity-cat-<c:out value='${cat}'/>">
        <input type="checkbox" name="category" value="<c:out value='${cat}'/>"<c:if test="${selectedCategories.contains(cat)}"> checked</c:if>>
        <c:out value="${cat}"/>
      </label>
    </c:forEach>
  </div>
  <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Apply Filters</button>
  <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
</form>

<table class="unstriped hover">
  <thead>
    <tr>
      <th width="150">When</th>
      <th width="140">Category</th>
      <th>Actor</th>
      <th>Activity</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${activityList}" var="entry">
    <tr>
      <td nowrap>
        <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${entry.occurred}' />"><c:out value="${date:relative(entry.occurred)}" /></span>
      </td>
      <td nowrap>
        <span class="activity-cat-badge activity-cat-<c:out value='${entry.eventCategory}'/>"><c:out value="${entry.eventCategory}" /></span>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty entry.actorUsername}"><c:out value="${entry.actorUsername}" /></c:when>
          <c:otherwise><em class="subheader">(unknown)</em></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:out value="${entry.description}" />
        <c:if test="${!empty entry.targetLabel}"> &mdash; <c:out value="${entry.targetLabel}" /></c:if>
        <c:if test="${entry.outcome eq 'failure'}"> <span class="label alert radius">failed</span></c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty activityList}">
      <tr>
        <td colspan="4">No activity was found for the selected filters.</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
