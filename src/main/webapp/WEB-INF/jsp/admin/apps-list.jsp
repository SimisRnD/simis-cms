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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="appList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="deviceCountByAppId" class="java.util.HashMap" scope="request"/>
<c:if test="${userSession.hasRole('admin')}">
<script nonce="${cspNonce}">
  function deleteApp(appId) {
    if (!confirm("Are you sure you want to permanently delete this App? Its Client ID will stop working immediately, and this cannot be undone.")) {
      return;
    }
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&id=' + appId);
  }
</script>
</c:if>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <h6>What this page shows</h6>
  <p>Each row is an "App" -- really an API client credential, not a piece of software you install. The Client ID is not a secret -- it's safe to show alongside a name to tell same-named Apps apart. There is no separate "Client Secret" surfaced anywhere in this UI; it isn't a credential to look for or rely on. <strong>Devices</strong> counts sessions this App's key has been used to establish (see <a href="${ctx}/admin/cache-management">Cache Management</a> for how sessions relate to caching) -- a nonzero count means this credential has actually been used, not just created.</p>
  <p>Use <strong>Enabled: No</strong> to immediately stop an App from authenticating without losing its history or Client ID -- reversible, and the safer first move. Use <strong>Delete</strong> to permanently remove it once you've confirmed nothing still needs its audit history; this cannot be undone.</p>
</div>

<h5>When to worry</h5>
<div class="callout warning radius">
  <p><strong>An integration that used to work now gets 401s.</strong> Check <strong>Enabled</strong> here before assuming the client-side code, network, or key itself is the problem -- a flipped Enabled=No is a common, easy-to-miss cause and produces the exact same error a genuinely invalid key would (see the <a href="${ctx}/admin/apis">APIs</a> page's troubleshooting notes).</p>
  <p><strong>You need to know who created or last changed an App, or when it was disabled.</strong> Every create, update, enable, disable, and delete is recorded in the platform's audit log -- use each row's <strong>History</strong> link, or search <a href="${ctx}/admin/audit-log">Audit Log</a> for target type <code>app</code>.</p>
  <p><strong>A credential is confirmed leaked or compromised.</strong> Disable it immediately (takes effect right away, on the next request) rather than deleting it first -- deleting is permanent and discards the audit trail you may still want for the incident. Delete only once you're done investigating.</p>
</div>

<h5>Best practices</h5>
<div class="callout radius">
  <p>Give each integration or client its own App rather than sharing one Client ID across several -- it makes Devices (usage), the audit trail, and a future disable-or-delete decision specific to the one integration that actually needs it, instead of an all-or-nothing action that also breaks whatever else was quietly sharing the same key.</p>
  <p>A duplicate name across two Apps is allowed (you'll get a non-blocking warning, not a rejection) -- useful for deliberate cases like staging vs. production credentials for the same integration, but worth a specific-enough name either way so the Client ID column is the only thing you'd ever need to tell two same-named entries apart.</p>
</div>

<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="220">Client ID</th>
      <th width="100" class="text-center">Devices</th>
      <th width="100" class="text-center">Enabled?</th>
      <th width="200">Created</th>
      <th width="70">History</th>
      <th width="80">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${appList}" var="app">
    <tr>
      <td>
        <c:out value="${app.name}" />
        <c:if test="${!empty app.summary}">
          <br /><small class="subheader"><c:out value="${app.summary}" /></small>
        </c:if>
      </td>
      <td><small><c:out value="${app.publicKey}" /></small></td>
      <td class="text-center"><fmt:formatNumber value="${deviceCountByAppId[app.id]}" /></td>
      <td class="text-center">
        <c:choose>
          <c:when test="${app.enabled}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label warning">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${app.created}" /></td>
      <td>
        <c:url var="historyUrl" value="/admin/audit-log">
          <c:param name="targetType" value="app"/>
          <c:param name="targetLabel" value="${app.name}"/>
        </c:url>
        <a href="${historyUrl}" title="View audit history for this app">History</a>
      </td>
      <td>
        <a href="${ctx}/admin/app?appId=${app.id}"><i class="${font:fas()} fa-edit"></i></a>
        <c:if test="${userSession.hasRole('admin')}">
          <a href="#" data-js-call="deleteApp" data-js-arg1="${app.id}"><i class="fa fa-remove"></i></a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty appList}">
      <tr>
        <td colspan="7">No apps were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
