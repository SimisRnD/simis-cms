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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="userList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<jsp:useBean id="query" class="java.lang.String" scope="request"/>
<jsp:useBean id="statusFilter" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<button class="button small primary radius float-left" data-open="formReveal"><i class="fa fa-plus"></i> New User</button>
<form id="fileForm" method="post" enctype="multipart/form-data" class="float-left">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="uploadCSVFile" />
  <label for="file" class="button small secondary radius margin-left-10"><i class="fa fa-upload"></i> Upload CSV File</label>
  <input type="file" id="file" name="file" accept="text/csv" class="show-for-sr">
</form>
<script>
    document.getElementById("file").onchange = function() {
        document.getElementById("fileForm").submit();
    }
</script>
<form id="tableOptionsForm" method="get" autocomplete="off" class="float-right">
  <select id="statusFilter" name="statusFilter" class="float-left width-auto margin-right-10">
    <option value="any"<c:if test="${statusFilter eq 'any'}"> selected</c:if>>Any Status</option>
    <option value="active"<c:if test="${statusFilter eq 'active'}"> selected</c:if>>Active List</option>
    <option value="inactive"<c:if test="${statusFilter eq 'inactive'}"> selected</c:if>>Inactive List</option>
  </select>
  <div class="input-group no-gap width-auto">
    <input class="input-group-field" type="search" name="query" placeholder="<c:if test="${empty query}">Search...</c:if>"<c:if test="${!empty query}"> value="<c:out value="${query}"/>"</c:if> autocomplete="off">
    <div class="input-group-button">
      <button type="submit" class="button search"><i class="fa fa-search"></i></button>
    </div>
  </div>
</form>
<script>
  document.getElementById("statusFilter").onchange = function() {
    document.getElementById("tableOptionsForm").submit();
  }
</script>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th>Email</th>
      <th>Role</th>
      <th width="60">Validated?</th>
      <th width="200">Last Login</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${userList}" var="user">
    <tr>
      <td>
        <a href="${ctx}/admin/user-details?userId=${user.id}"><c:out value="${user.fullName}" /></a>
        <c:if test="${!empty user.organization}">
          <br /><small class="subheader"><c:out value="${user.organization}" /></small>
        </c:if>
      </td>
      <td><c:out value="${user.email}" /></td>
      <td>
        <c:forEach items="${user.roleList}" var="userRole">
          <span class="label round"><c:out value="${userRole.code}" /></span>
        </c:forEach>
      </td>
      <td class="text-center">
        <c:choose>
          <c:when test="${!user.enabled}"><span class="label alert">Suspended</span></c:when>
          <c:when test="${!empty user.validated}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label warning">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <c:if test="${!empty user.lastLogin}"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.lastLogin.created}" /></c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty userList}">
      <tr>
        <td colspan="5">No users were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<c:if test="${!empty statusFilter}">
  <c:set var="recordPagingParams" scope="request" value="statusFilter=${statusFilter}"/>
</c:if>
<%@include file="../paging_control.jspf" %>
<%--<div class="reveal small" id="formReveal" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-in="slide-in-down fast">--%>
<div class="reveal small" id="formReveal" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4>New User</h4>
  <form id="userForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- Form --%>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-5 cell">
        <label>First Name <span class="required">*</span>
          <input type="text" placeholder="First Name" name="firstName" value="" autofocus required>
        </label>
      </fieldset>
      <fieldset class="medium-7 cell">
        <label>Last Name <span class="required">*</span>
          <input type="text" placeholder="Last Name" name="lastName" value="" required>
        </label>
      </fieldset>
    </div>
    <label>Email <span class="required">*</span>
      <input type="email" placeholder="Email Address" name="email" value="" required>
    </label>
    <label>Organization
      <input type="text" placeholder="Organization" name="organization" value="">
    </label>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-6 cell">
        <label>Community Nickname
          <input type="text" placeholder="Nickname" name="nickname" value="">
        </label>
        <p class="help-text" id="nicknameHelpText">Optional name to be shown instead of first/last name</p>
      </fieldset>
      <fieldset class="medium-6 cell">
        <label>Username
          <input type="text" placeholder="Username" name="username" value="">
        </label>
        <p class="help-text" id="usernameHelpText">Optional, system uses email address when this is empty</p>
      </fieldset>
    </div>
    <c:if test="${!empty roleList}">
      <legend>Roles</legend>
      <c:forEach items="${roleList}" var="role">
        <c:choose>
          <c:when test="${role.code eq 'admin' && !userSession.hasRole('admin')}"><%-- --%></c:when>
          <c:otherwise>
            <input id="roleId${role.id}" type="checkbox" name="roleId${role.id}" value="${role.id}" /><label for="roleId${role.id}"><c:out value="${role.title}" /></label>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </c:if>
    <c:if test="${!empty groupList}">
      <legend>Groups</legend>
      <c:forEach items="${groupList}" var="group">
        <c:choose>
          <c:when test="${group.name eq 'All Guests'}">
            <%-- not a logged in user group --%>
          </c:when>
          <c:otherwise>
            <input id="groupId${group.id}" type="checkbox" name="groupId${group.id}" value="${group.id}" /><label for="groupId${group.id}"><c:out value="${group.name}" /></label>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </c:if>
    <p>
      <input type="submit" class="button radius expanded" value="Save" />
    </p>
  </form>
</div>
<%--<script>--%>
<%--  $(document).on('open.zf.reveal', '[data-reveal]', function () {--%>
<%--    let modal = $(this);--%>
<%--    modal.find('[autofocus]').focus();--%>
<%--  });--%>
<%--</script>--%>