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
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="userLogin" class="com.simisinc.platform.domain.model.login.UserLogin" scope="request"/>
<script>
  function resetPassword() {
    if (!confirm("Are you sure you want to reset the password on this account? An email with instructions will be sent to the user.")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=resetPassword&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}';
  }
  function suspendAccount() {
    if (!confirm("Are you sure you want to SUSPEND this user account?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=suspendAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}';
  }
  function restoreAccount() {
    if (!confirm("Are you sure you want to RESTORE this user account?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=restoreAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}';
  }
  function deleteAccount() {
    if (!confirm("Are you sure you want to DELETE this user account?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?action=deleteAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}';
  }
</script>
<div style="margin-top: 6px;background-color:<c:out value="${themePropertyMap['theme.body.backgroundColor']}" />;">
  <div class="button-container float-right">
    <a class="button small radius float-right" href="${ctx}/admin/modify-user?userId=${user.id}">Modify User</a>
    <ul class="dropdown menu" style="padding-right: 15px;" data-dropdown-menu>
      <li>
        <a href="#">Actions</a>
        <ul class="menu">
          <c:if test="${user.enabled}">
            <li><a href="javascript:resetPassword()">Reset Password</a></li>
            <li><a href="javascript:suspendAccount()">Suspend Account</a></li>
          </c:if>
          <c:if test="${!user.enabled}">
            <li><a href="javascript:restoreAccount()">Restore Account</a></li>
          </c:if>
          <li><a href="javascript:deleteAccount()">Delete Account</a></li>
        </ul>
      </li>
    </ul>
  </div>
  <h3>
    <c:out value="${user.fullName}" />
    <c:if test="${!user.enabled}">
      <span class="label alert">Suspended</span>
    </c:if>
  </h3>
  <c:if test="${!empty user.title || !empty user.city || !empty user.state}">
    <p>
      <c:if test="${!empty user.title}">
        <c:out value="${user.title}" /><br />
      </c:if>
      <c:if test="${!empty user.city || !empty state}">
        <small><i class="fa fa-map-marker"></i>
          <c:if test="${!empty user.city}"><c:out value="${user.city}" /></c:if>
          <c:if test="${!empty user.state}"><c:out value="${user.state}" /></c:if>
        </small>
      </c:if>
    </p>
  </c:if>
  <hr>
</div>
<%@include file="../page_messages.jspf" %>
<div class="grid-container">
<div class="grid-x">
  <div class="small-12 medium-6 large-4 cell">
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>First Name</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user.firstName}" />
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Last Name</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user.lastName}" />
      </div>
    </div>
    <c:if test="${!empty user.title}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Title</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.title}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.organization}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Organization</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.organization}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.department}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Department</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.department}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.nickname}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Community Nickname</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.nickname}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.email && fn:contains(user.email, '@')}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Email</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.email}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.city}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>City</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.city}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.state}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>State/Province</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.state}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.country}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Country</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.country}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.postalCode}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Postal Code</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.postalCode}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.timeZone}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Time Zone</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.timeZone}" />
        </div>
      </div>
    </c:if>
  </div>
  <div class="small-12 medium-6 large-4 cell">
    <c:if test="${user.email ne user.username}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Username</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.username}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.roleList}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Roles</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:forEach items="${user.roleList}" var="userRole" varStatus="status">
            <span class="label"><c:out value="${userRole.title}" /></span>
          </c:forEach>
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.groupList}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Groups</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:forEach items="${user.groupList}" var="group" varStatus="status">
            <span class="label secondary"><c:out value="${group:name(group.id)}" /></span><c:if test="${!status.last}"><br /></c:if>
          </c:forEach>
        </div>
      </div>
    </c:if>
  </div>
  <div class="small-12 medium-6 large-4 cell">
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Validated</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:choose>
          <c:when test="${!empty user.validated}"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.validated}" /></c:when>
          <c:otherwise><span class="label warning">Not Validated</span></c:otherwise>
        </c:choose>
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Created</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.created}" />
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Created By</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user:name(user.createdBy)}"/>
      </div>
    </div>
    <c:if test="${user.created ne user.modified}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Modified</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.modified}" />
        </div>
      </div>
      <c:if test="${user.modifiedBy gt 0}">
        <div class="grid-x grid-padding-x">
          <div class="small-4 text-right cell">
            <small>Modified By</small>
          </div>
          <div class="small-8 align-self-middle cell">
            <c:out value="${user:name(user.modifiedBy)}"/>
          </div>
        </div>
      </c:if>
    </c:if>
    <c:if test="${!empty userLogin.created}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Last Login</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${userLogin.created}" />
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Last IP</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${userLogin.ipAddress}" />
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Geo IP</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${geoip:cityState(userLogin.ipAddress, '---')}"/>
        </div>
      </div>
    </c:if>
  </div>
</div>
</div>
<hr>
<p><a href="${ctx}/admin/users"><i class="fa fa-angle-double-left"></i> Back to list</a></p>