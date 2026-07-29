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
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="auditLogList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="text-right"><small class="subheader">Events older than <c:out value="${retentionDays}"/> days are purged automatically.</small></p>
<%-- Quick range presets: finer-grained (hour precision) than the date-only filter below. Picking one
     clears the explicit date range server-side (see AuditLogListWidget#buildSpecification). --%>
<div class="button-group tiny">
  <a href="${widgetContext.uri}?range=1h" class="button ${range eq '1h' ? 'primary' : 'secondary'}">Last hour</a>
  <a href="${widgetContext.uri}?range=24h" class="button ${range eq '24h' ? 'primary' : 'secondary'}">Last 24 hours</a>
  <a href="${widgetContext.uri}?range=7d" class="button ${range eq '7d' ? 'primary' : 'secondary'}">Last 7 days</a>
  <a href="${widgetContext.uri}?range=30d" class="button ${range eq '30d' ? 'primary' : 'secondary'}">Last 30 days</a>
  <c:if test="${!empty range}"><a href="${widgetContext.uri}" class="button secondary">Clear</a></c:if>
</div>
<%-- Filters (GET so the criteria live in the URL and paging preserves them) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="grid-x grid-margin-x">
    <div class="cell medium-3">
      <label>Category
        <select name="category">
          <option value="">Any category</option>
          <c:forEach items="${categoryList}" var="cat">
            <option value="<c:out value='${cat}'/>"<c:if test="${category eq cat}"> selected</c:if>><c:out value="${cat}"/></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-3">
      <label>Event type
        <input type="text" name="eventType" placeholder="e.g. user.disable" value="<c:out value='${eventType}'/>">
      </label>
    </div>
    <div class="cell medium-2">
      <label>Outcome
        <select name="outcome">
          <option value="">Any outcome</option>
          <option value="success"<c:if test="${outcome eq 'success'}"> selected</c:if>>Success</option>
          <option value="failure"<c:if test="${outcome eq 'failure'}"> selected</c:if>>Failure</option>
        </select>
      </label>
    </div>
    <div class="cell medium-4">
      <label>Actor (email contains)
        <input type="text" name="actor" placeholder="username or email" value="<c:out value='${actor}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Source IP
        <input type="text" name="sourceIp" placeholder="e.g. 203.0.113.4" value="<c:out value='${sourceIp}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Target type
        <input type="text" name="targetType" placeholder="e.g. user" value="<c:out value='${targetType}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>Target label
        <input type="text" name="targetLabel" placeholder="e.g. an IP address" value="<c:out value='${targetLabel}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>From date
        <input type="date" name="fromDate" value="<c:out value='${fromDate}'/>">
      </label>
    </div>
    <div class="cell medium-3">
      <label>To date
        <input type="date" name="toDate" value="<c:out value='${toDate}'/>">
      </label>
    </div>
    <div class="cell medium-6">
      <label>&nbsp;</label>
      <button type="submit" class="button small primary radius"><i class="fa fa-filter"></i> Filter</button>
      <a href="${widgetContext.uri}" class="button small secondary radius">Clear</a>
    </div>
  </div>
</form>
<%-- Export: same filter criteria as the results below (mirrored as hidden fields since export is a POST) --%>
<form method="post" autocomplete="off" class="margin-bottom-10">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="category" value="<c:out value='${category}'/>"/>
  <input type="hidden" name="eventType" value="<c:out value='${eventType}'/>"/>
  <input type="hidden" name="outcome" value="<c:out value='${outcome}'/>"/>
  <input type="hidden" name="actor" value="<c:out value='${actor}'/>"/>
  <input type="hidden" name="sourceIp" value="<c:out value='${sourceIp}'/>"/>
  <input type="hidden" name="targetType" value="<c:out value='${targetType}'/>"/>
  <input type="hidden" name="targetLabel" value="<c:out value='${targetLabel}'/>"/>
  <input type="hidden" name="range" value="<c:out value='${range}'/>"/>
  <input type="hidden" name="fromDate" value="<c:out value='${fromDate}'/>"/>
  <input type="hidden" name="toDate" value="<c:out value='${toDate}'/>"/>
  <button type="submit" name="command" value="downloadCSVFile" class="button small secondary radius"><i class="fa fa-download"></i> Export CSV</button>
  <button type="submit" name="command" value="downloadJSONFile" class="button small secondary radius"><i class="fa fa-download"></i> Export JSON</button>
</form>
<table class="unstriped hover">
  <thead>
    <tr>
      <th width="150">When</th>
      <th>Event</th>
      <th width="70">Outcome</th>
      <th>Actor</th>
      <th width="120">Source IP</th>
      <th>Target</th>
      <th width="90">Session</th>
      <th>Details</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${auditLogList}" var="record">
    <tr>
      <td nowrap>
        <span title="<fmt:formatDate pattern='yyyy-MM-dd HH:mm:ss z' value='${record.occurred}' />"><c:out value="${date:relative(record.occurred)}" /></span>
      </td>
      <td>
        <c:out value="${record.eventType}" /><br />
        <small class="subheader"><c:out value="${record.eventCategory}" /></small>
      </td>
      <td nowrap>
        <c:choose>
          <c:when test="${record.outcome eq 'success'}"><span class="label success radius">success</span></c:when>
          <c:when test="${record.outcome eq 'failure'}"><span class="label alert radius">failure</span></c:when>
          <c:otherwise><span class="label secondary radius"><c:out value="${record.outcome}"/></span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty record.actorUsername}"><c:out value="${record.actorUsername}" /></c:when>
          <c:otherwise><em class="subheader">(unknown)</em></c:otherwise>
        </c:choose>
      </td>
      <td nowrap><c:out value="${record.sourceIp}" /></td>
      <td>
        <c:if test="${!empty record.targetLabel or !empty record.targetType or !empty record.targetId}">
          <c:out value="${record.targetLabel}" />
          <c:if test="${!empty record.targetType}">
            <br /><small class="subheader">
              <c:out value="${record.targetType}" /><c:if test="${!empty record.targetId}"> #<c:out value="${record.targetId}" /></c:if>
            </small>
          </c:if>
        </c:if>
      </td>
      <td class="break-word"><small><c:out value="${record.sessionId}" /></small></td>
      <td class="break-word">
        <c:if test="${!empty record.details}">
          <a href="#" class="audit-details-trigger" data-details="<c:out value='${record.details}' />" title="View full details">
            <c:choose>
              <c:when test="${fn:length(record.details) > 80}">
                <small><c:out value="${fn:substring(record.details, 0, 80)}" />&#8230;</small>
              </c:when>
              <c:otherwise>
                <small><c:out value="${record.details}" /></small>
              </c:otherwise>
            </c:choose>
          </a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty auditLogList}">
      <tr>
        <td colspan="8">No audit records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%@include file="../paging_control.jspf" %>
<div class="reveal" id="auditDetailsReveal" data-reveal role="dialog" aria-modal="true" aria-labelledby="auditDetailsTitle">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4 id="auditDetailsTitle">Event Details</h4>
  <pre id="auditDetailsContent" class="break-word" style="white-space:pre-wrap"></pre>
</div>
<script nonce="${cspNonce}">
  $(function() {
    // The trigger's data-details is read back already HTML-decoded by jQuery's .data(); using .text()
    // (never .html()) to place it back into the DOM re-escapes it, so this is safe regardless of content.
    $('.audit-details-trigger').on('click', function(e) {
      e.preventDefault();
      $('#auditDetailsContent').text($(this).data('details'));
      $('#auditDetailsReveal').foundation('open');
    });
  });
</script>
