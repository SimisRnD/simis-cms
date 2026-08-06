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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="botUserAgentList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<p class="help-text">A session is counted as a bot when its User-Agent header contains any of the partial strings below (a simple substring match, not a regular expression) -- see <code>SessionCommand.checkForBot()</code>. Bot sessions are excluded from "Real Sessions"/"Return Visitor Rate" and most other visitor-facing analytics on the Site Analytics pages, but are still counted separately in the Bot Sessions/Bot Traffic tiles.</p>
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
<form method="post" action="${ctx}/admin/botUserAgent">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<p class="help-text">Upload requires a "Partial User Agent" column, plus optional "Label" and "Remove" columns; set Remove to "true" on a row to remove that entry instead of adding it. Download includes Partial User Agent, Label, and Date.</p>
<table class="unstriped stack">
  <thead>
    <tr>
      <th width="220">Partial User Agent</th>
      <th>Label</th>
      <th width="80">Logged</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${botUserAgentList}" var="record">
    <tr>
      <td nowrap="true">
        <c:out value="${text:trim(record.userAgent, 40, true)}" />
        <a href="#" onclick="return confirmPostAction('Are you sure you want to remove <c:out value="${js:escape(record.userAgent)}" /> from the bot list?', '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&botListId=${record.id}');"><i class="fa fa-remove"></i></a>
      </td>
      <td nowrap="true"><small<c:if test="${fn:length(record.label) > 40}"> title="<c:out value="${record.label}" />"</c:if>><c:out value="${text:trim(record.label, 40, true)}" /></small></td>
      <td nowrap="true"><fmt:formatDate pattern="yyyy-MM-dd" value="${record.created}" /></td>
    </tr>
    </c:forEach>
    <c:if test="${empty botUserAgentList}">
      <tr>
        <td colspan="3">No records were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>
