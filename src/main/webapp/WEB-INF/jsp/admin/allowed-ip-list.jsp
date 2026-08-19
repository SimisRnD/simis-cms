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
<jsp:useBean id="allowedIPList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="currentClientIp" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<p class="help-text">IPs on this list bypass every other check in <code>WebRequestFilter</code> - the deny list, the <a href="${ctx}/admin/blocked-ip-list">Blocked IP list</a>, and the URL-probe auto-block - so a match here always lets the request through, for every page on the site (aside from a couple of hardcoded exemptions, like the <code>/healthz</code> health check), not just specific ones. Accepts a single IPv4/IPv6 address or a CIDR range (e.g. <code>203.0.113.0/24</code>). A separate, server-file-based allow list (<code>config/cms/ip-allow-list.csv</code>) is also still checked and isn't managed here; see <code>docs/ip-blocking.md</code>.</p>
<div class="callout radius">
  <p style="margin-bottom:5px;"><strong>Your current request is being seen as:</strong> <code><c:out value="${currentClientIp}"/></code></p>
  <p class="help-text" style="margin-bottom:0;">This is the same value the <a href="${ctx}/admin/blocked-ip-list">Blocked IP list</a>'s check compares entries against. If it doesn't look like your real IP -- for example, an internal/private address while you're browsing from the public internet -- this deployment is likely behind a reverse proxy or load balancer without <code>CMS_TRUSTED_PROXIES</code> configured, and every visitor is seen as the proxy's own address. Until that's fixed by whoever manages the deployment, IP blocking will silently not work correctly for anyone.</p>
</div>
<div class="callout warning radius">
  <h6>Before you add an entry here</h6>
  <ul style="margin-bottom:0;">
    <li>Because an Allowed entry always wins, it's easy to accidentally undo a block: an entry here that also matches a <a href="${ctx}/admin/blocked-ip-list">Blocked IP</a> entry lets that address through anyway, since this list is checked first, before the block list is even consulted. Saving a new block on the Blocked IP page now warns inline when an Allowed entry like this would cover the same address.</li>
    <li>A block only matches its own address family: an IPv4 entry never matches an IPv6 request and vice versa.</li>
  </ul>
</div>
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
<form method="post" action="${ctx}/admin/allowedIP">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<p class="help-text">Upload requires an "IP Address" column, plus optional "Reason", "Date", and "Remove" columns; set Remove to "true" on a row to remove that IP from the allow list instead of adding it. Download includes IP Address, Date, and Reason. After an import, read the result message above for the actual succeeded/skipped counts and re-check the list below -- don't assume a generic-looking message means every row landed.</p>
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
    <c:forEach items="${allowedIPList}" var="record">
    <tr>
      <td nowrap="true">
        <c:out value="${text:trim(record.ipAddress, 24, true)}" />
        <a href="#" data-confirm-post="Are you sure you want to remove <c:out value="${record.ipAddress}" /> from the allow list?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&allowedIPListId=${record.id}"><i class="fa fa-remove"></i></a>
      </td>
      <td><c:choose><c:when test="${fn:contains(record.ipAddress, '/')}"><small>Range</small></c:when><c:otherwise><c:out value='${geoip:location(record.ipAddress, " ")}'/></c:otherwise></c:choose></td>
      <td nowrap="true"><small<c:if test="${fn:length(record.reason) > 40}"> title="<c:out value="${record.reason}" />"</c:if>><c:out value="${text:trim(record.reason, 40, true)}" /></small></td>
      <td nowrap="true"><fmt:formatDate pattern="yyyy-MM-dd" value="${record.created}" /></td>
      <td nowrap="true">
        <c:url var="historyUrl" value="/admin/audit-log">
          <c:param name="targetType" value="allowed_ip"/>
          <c:param name="targetLabel" value="${record.ipAddress}"/>
        </c:url>
        <a href="${historyUrl}" title="View audit history for this IP">History</a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty allowedIPList}">
      <tr>
        <td colspan="5">No records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>
