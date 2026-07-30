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
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="capabilitiesByRoleId" class="java.util.LinkedHashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<p class="help-text">Each role is a bundle of capabilities checked throughout the platform. This page grants or revokes capabilities directly - it doesn't manage which users have which role (see <a href="${ctx}/admin/users">Users</a> for that). Removing every role's last holder of a capability is refused, since that would leave nobody able to grant it back.</p>
<%@include file="../page_messages.jspf" %>
<table class="unstriped stack">
  <thead>
    <tr>
      <th width="160">Role</th>
      <th>Capabilities</th>
      <th width="70">Edit</th>
      <th width="70">History</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${roleList}" var="role">
    <tr>
      <td><c:out value="${role.title}" /></td>
      <td>
        <c:forEach items="${capabilitiesByRoleId[role.id]}" var="capability" varStatus="status">
          <code><c:out value="${capability.code}" /></code><c:if test="${!status.last}">, </c:if>
        </c:forEach>
        <c:if test="${empty capabilitiesByRoleId[role.id]}"><small class="subheader">None</small></c:if>
      </td>
      <td><a href="${ctx}/admin/role-capabilities-form?roleId=${role.id}" title="Edit capabilities for this role"><i class="fa fa-edit"></i></a></td>
      <td>
        <c:url var="historyUrl" value="/admin/audit-log">
          <c:param name="targetType" value="role_capability"/>
          <c:param name="targetLabel" value="${role.code}"/>
        </c:url>
        <a href="${historyUrl}" title="View audit history for this role">History</a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty roleList}">
      <tr>
        <td colspan="4">No roles were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
