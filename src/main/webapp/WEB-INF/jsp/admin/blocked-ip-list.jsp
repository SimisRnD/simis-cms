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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blockedIPList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="query" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<p class="help-text">A blocked IP is checked on every request, before the page loads; a match returns a 404 (not found) response rather than the actual page. IPs land on this list three ways: added manually below, uploaded via CSV, or automatically when a request path matches a known attack-probe pattern (see the server's <code>config/cms/url-block-list.csv</code>). Accepts a single IPv4/IPv6 address or a CIDR range (e.g. <code>203.0.113.0/24</code>). Two other lists also apply on every request: the <a href="${ctx}/admin/allowed-ip-list">Allowed IP list</a>, checked before this one, and a server-side deny list checked after it (<code>config/cms/ip-deny-list.csv</code>, not editable here). A separate, server-file-based allow list (<code>config/cms/ip-allow-list.csv</code>) is also still checked, ahead of the admin-managed one. You can't block your own current IP, manually or via CSV - the save is rejected instead. Deleting a row below unblocks that IP immediately, and every add, delete, import, and export here is recorded in the platform's audit log. See <code>docs/ip-blocking.md</code> in the repository for the fuller strategy write-up.</p>
<%@include file="../page_messages.jspf" %>
<form id="fileForm" method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="uploadCSVFile" />
  <label for="file" class="button small secondary radius float-left margin-left-0"><i class="fa fa-upload"></i> Upload CSV File</label>
  <input type="file" id="file" name="file" accept="text/csv" class="show-for-sr">
</form>
<script nonce="${cspNonce}">
  document.getElementById("file").onchange = function() {
    document.getElementById("fileForm").submit();
  }
</script>
<form method="post" action="${ctx}/admin/blockedIP">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<p class="help-text">Upload requires an "IP Address" column, plus optional "Reason", "Date", and "Remove" columns; set Remove to "true" on a row to unblock that IP instead of adding it. Matching existing IPs with the same reason are skipped. Download includes IP Address, Date, and Reason.</p>
<%-- Search (GET so the query lives in the URL and paging preserves it) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <div class="input-group">
    <label for="blockedIpQuery" class="show-for-sr">Search by IP address or reason</label>
    <input id="blockedIpQuery" class="input-group-field" type="search" name="query" placeholder="Search by IP address or reason..."<c:if test="${!empty query}"> value="<c:out value="${query}"/>"</c:if>>
    <div class="input-group-button">
      <button type="submit" class="button">Search</button>
    </div>
  </div>
</form>
<table class="unstriped stack">
  <thead>
    <tr>
      <th width="100">IP Address</th>
      <th>Location</th>
      <th>Reason</th>
      <th width="80">Logged</th>
      <th width="70">History</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${blockedIPList}" var="record">
    <tr>
      <td nowrap="true">
        <c:out value="${text:trim(record.ipAddress, 24, true)}" />
        <a href="#" onclick="return confirmPostAction('Are you sure you want to delete <c:out value="${js:escape(record.ipAddress)}" />?', '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&blockedIPListId=${record.id}');"><i class="fa fa-remove"></i></a>
      </td>
      <td><c:choose><c:when test="${fn:contains(record.ipAddress, '/')}"><small>Range</small></c:when><c:otherwise><c:out value='${geoip:location(record.ipAddress, " ")}'/></c:otherwise></c:choose></td>
      <td nowrap="true"><small<c:if test="${fn:length(record.reason) > 40}"> title="<c:out value="${record.reason}" />"</c:if>><c:out value="${text:trim(record.reason, 40, true)}" /></small></td>
      <td nowrap="true"><fmt:formatDate pattern="yyyy-MM-dd" value="${record.created}" /></td>
      <td nowrap="true">
        <c:url var="historyUrl" value="/admin/audit-log">
          <c:param name="targetType" value="blocked_ip"/>
          <c:param name="targetLabel" value="${record.ipAddress}"/>
        </c:url>
        <a href="${historyUrl}" title="View audit history for this IP">History</a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty blockedIPList}">
      <tr>
        <td colspan="5"><c:choose><c:when test="${!empty query}">No records matched your search</c:when><c:otherwise>No records were found</c:otherwise></c:choose></td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>