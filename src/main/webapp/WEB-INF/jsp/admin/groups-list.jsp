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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <p style="margin-bottom:0">
    Groups are access-control buckets, separate from <a href="${ctx}/admin/users">Roles</a>. A
    role decides what an account can <em>do</em> in the admin console (manage users, edit content,
    and so on); a group decides what content an account can <em>see</em> -- for example, a
    Collection's "Access Groups" settings can restrict its records to members of a specific group.
    Use the "Add a user group" form on this page to create a new group; click the pencil next to an
    existing one to rename or describe it, or the X to delete it. Admin-only -- unlike Users, this
    page isn't open to community-managers.
  </p>
</div>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th width="100" class="text-center"># of users</th>
      <th width="60">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${groupList}" var="group">
    <tr>
      <td>
        <c:out value="${group.name}" />
        <c:if test="${!empty group.description}">
          <br /><small class="subheader"><c:out value="${group.description}" /></small>
        </c:if>
      </td>
      <td class="text-center"><fmt:formatNumber value="${group.userCount}" /></td>
      <td>
        <a href="${ctx}/admin/group?groupId=${group.id}"><i class="${font:fas()} fa-edit"></i></a>
        <a href="#" data-confirm-post="Are you sure you want to delete <c:out value="${group.name}" />?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&groupId=${group.id}"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty groupList}">
      <tr>
        <td colspan="3">No groups were found</td>
      </tr>
    </c:if>
  </tbody>
</table>

<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>"Error. Group could not be deleted."</strong> This generic message almost always means
    the group still has members -- deleting it is blocked at the database level rather than silently
    cascading, so no one's group membership disappears without you noticing. Move or remove its
    members first (edit each account, or use bulk actions on the
    <a href="${ctx}/admin/users">Users list</a>), then delete the now-empty group.</li>
  <li><strong>Don't rename or delete "All Users."</strong> It's the one built-in group every new
    account -- created here, self-registered, signed up via CSV import, or provisioned via
    OAuth/SSO -- is automatically added to, by looking up that exact name. Renaming it means those
    lookups quietly stop finding it, and new accounts stop getting the group they're supposed to.
    If you need to reorganize access, add a new group instead of repurposing this one.</li>
  <li><strong>A Collection's "Access Groups" rule for a group you deleted disappeared, or a
    page/section restricted to a group you deleted no longer seems restricted at all.</strong>
    Deleting a group doesn't check whether anything still references it for access control -- a
    Collection's per-group access rule is simply gone along with it, and any other content whose
    restriction depended solely on that group effectively stops being restricted (no one matches a
    group that no longer exists), rather than failing loudly. Creating a new group with the same name
    afterward gets a new internal identity -- it will not automatically reattach to a Collection's old
    access rule, which has to be reconfigured from scratch on the
    <a href="${ctx}/admin/collections">Collections</a> page.</li>
</ul>
