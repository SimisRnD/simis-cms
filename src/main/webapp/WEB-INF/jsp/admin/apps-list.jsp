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
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">The Client ID is not a secret -- it's safe to show alongside a name to tell same-named Apps apart. There is no separate "Client Secret"; use Enabled=No to immediately stop an App from authenticating, or Delete to permanently remove it once a compromised credential no longer needs its audit history.</p>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="220">Client ID</th>
      <th width="100" class="text-center">Devices</th>
      <th width="100" class="text-center">Enabled?</th>
      <th width="200">Created</th>
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
        <a href="${ctx}/admin/app?appId=${app.id}"><i class="${font:fas()} fa-edit"></i></a>
        <c:if test="${userSession.hasRole('admin')}">
          <a href="javascript:deleteApp(${app.id});"><i class="fa fa-remove"></i></a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty appList}">
      <tr>
        <td colspan="6">No apps were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
