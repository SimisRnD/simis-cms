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
<p class="help-text">This list only classifies incoming sessions as bot vs. human for <strong>analytics purposes</strong> -- it does not block, throttle, or gate any traffic, and it's completely separate from crawler-permission controls (see <a href="${ctx}/admin/robots-properties">Robots &amp; Crawlers</a> for those). An entry here has zero effect on what a bot is allowed to do; it only changes how that bot's sessions are counted.</p>
<p class="help-text">A session is counted as a bot when its User-Agent header contains any of the partial strings below -- a plain substring match against the raw header, not a regular expression (see <code>SessionCommand.checkForBot()</code>) -- and matching is <strong>case-sensitive</strong>. Enter the signature in the exact case a real crawler actually sends it, copied from an actual server log rather than guessed, and prefer something specific over something short: even a fragment that clears the minimum length can still be too generic and end up matching real browsers. Bot sessions are excluded from "Real Sessions"/"Return Visitor Rate" and most other visitor-facing analytics on the Site Analytics pages, but are still counted separately in the Bot Sessions/Bot Traffic tiles.</p>
<p class="help-text"><strong>checkForBot() only runs once, when a session is first created</strong> -- it never re-evaluates an existing session. Testing a newly-added signature in a browser tab that already has a session cookie will show no effect; test from a genuinely fresh session instead (an incognito/private window, or a tool like curl with a fresh cookie jar and the new signature in a User-Agent header). Adding a signature also does not retroactively reclassify sessions created before the change.</p>
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
<p class="help-text">Upload requires a "Partial User Agent" column, plus optional "Label" and "Remove" columns; set Remove to "true" on a row to remove that entry instead of adding it. Download includes Partial User Agent, Label, and Date. After any bulk import, check the actual result summary rather than assuming every row landed.</p>
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
        <a href="${ctx}/admin/bot-list?botListId=${record.id}" title="Edit"><i class="fa fa-edit"></i></a>
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
<h5>What to monitor</h5>
<ul>
  <li>Watch the <strong>Bot Traffic %</strong> tile on Site Analytics for sudden, unexplained jumps (usually means a new entry is too broad and is now matching real browsers) or drops (usually means a new crawler is evading detection).</li>
  <li>An unexplained dip in real/human traffic numbers is often the visible symptom of an over-broad entry silently misclassifying human visitors, since bot-flagged sessions are excluded from most visitor-facing analytics queries site-wide.</li>
  <li>Don't rely on this list for anything security-related -- pair it with <a href="${ctx}/admin/robots-properties">Robots &amp; Crawlers</a> if you actually want to influence what a bot is allowed to do.</li>
</ul>
