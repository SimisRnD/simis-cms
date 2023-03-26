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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="mailingList" class="com.simisinc.platform.domain.model.mailinglists.MailingList" scope="request"/>
<jsp:useBean id="emailList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h4><c:out value="${mailingList.name}" /></h4>
<button class="button small primary radius float-left" data-open="formReveal"><i class="fa fa-plus"></i> Add Email</button>
<form id="fileForm" method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="uploadCSVFile" />
  <input type="hidden" name="mailingListId" value="${mailingList.id}" />
  <label for="file" class="button small secondary radius float-left margin-left-10"><i class="fa fa-upload"></i> Upload CSV File</label>
  <input type="file" id="file" name="file" accept="text/csv" class="show-for-sr">
</form>
<form method="post" action="${ctx}/admin/mailing-list-members">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <input type="hidden" name="mailingListId" value="${mailingList.id}" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<script>
  document.getElementById("file").onchange = function() {
    document.getElementById("fileForm").submit();
  }
</script>
<table>
  <thead>
    <tr>
      <th>Name</th>
      <th>Email</th>
      <th>Location</th>
      <th width="200">Added</th>
      <th width="100">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${emailList}" var="email">
    <tr>
      <td>
        <c:out value="${email.firstName}" />
      </td>
      <td>
        <%--<a href="${ctx}/admin/mailing-list-email-details?userId=${email.id}"><c:out value="${email.email}" /></a>--%>
        <c:out value="${email.email}" />
        <c:if test="${!empty email.organization}">
          <br /><small class="subheader"><c:out value="${email.organization}" /></small>
        </c:if>
      </td>
      <td><small><c:out value="${geoip:location(email.ipAddress, '--')}"/></small></td>
      <td><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${email.created}" /></td>
      <td>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&mailingListId=${mailingList.id}&emailId=${email.id}" onclick="return confirm('Are you sure you want to remove <c:out value="${js:escape(email.email)}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty emailList}">
      <tr>
        <td colspan="5">No members were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<c:set var="recordPagingParams" scope="request" value="mailingListId=${mailingList.id}"/>
<%@include file="../paging_control.jspf" %>
<div class="reveal small" id="formReveal" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4>Add Email</h4>
  <form id="userForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- Form --%>
    <label>Email (required)
      <input type="email" placeholder="Email Address" name="email" value="" required>
    </label>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-5 cell">
        <label>First Name
          <input type="text" placeholder="First Name" name="firstName" value="">
        </label>
      </fieldset>
      <fieldset class="medium-7 cell">
        <label>Last Name
          <input type="text" placeholder="Last Name" name="lastName" value="" >
        </label>
      </fieldset>
    </div>
    <label>Organization
      <input type="text" placeholder="Organization" name="organization" value="">
    </label>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Save" />
    </div>
  </form>
</div>