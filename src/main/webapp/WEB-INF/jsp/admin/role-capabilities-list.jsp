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
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<p class="help-text page-help">Each role is a bundle of capabilities checked throughout the platform. This page grants or revokes capabilities directly - it doesn't manage which users have which role (see <a href="${ctx}/admin/users">Users</a> for that).</p>
<p class="help-text page-help">A capability is additive, layered on top of roles rather than a replacement for them: a user's effective capabilities are the union of what their role(s) grant here plus any active direct grants made to them individually (see a user's Capability Grants page from their user details). A direct grant can only add access beyond a role - it can never subtract from or override what a role already provides. Use role edits here to give a capability to everyone in a job function; use direct grants for one person's scoped, possibly-temporary exception.</p>
<p class="help-text page-help"><strong>Only "admin:manage" is protected from reaching zero holders.</strong> Revoking it - here, or as a direct grant on a user's Capability Grants page - is refused if no one would be left effectively holding it afterward, counting every user covered by any other role that grants it or by their own active direct grant. For example, if two roles both grant admin:manage, revoking it from the first succeeds because the second still covers everyone who needs it, but revoking it from that second role too is refused unless some user still holds it directly. Every other capability - content:manage, community:manage, data:manage, ecommerce:manage, and any others - has no such check on either page and can be revoked down to zero holders with no warning; that's a deliberate, allowed admin decision. Before doing that, manually confirm some other route to that functionality still exists, since neither page will stop you or warn you.</p>
<p class="help-text page-help">There's no single page listing every user who holds a capability via a direct grant system-wide - each Capability Grants page is scoped to one user. To audit who holds a sensitive capability like admin:manage, cross-reference this page (which roles grant it) against <a href="${ctx}/admin/users">Users/Groups</a> (who's in those roles) and the audit log for individual grant/revoke events.</p>
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
