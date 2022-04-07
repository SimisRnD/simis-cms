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
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/geoip-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="emailList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table>
  <thead>
    <tr>
      <th>Email</th>
      <th>Name</th>
      <th>Location</th>
      <th width="200" class="text-center">Last Emailed</th>
      <th width="100" class="text-center">Subscribed?</th>
      <th width="100" class="text-center">Unsubscribed?</th>
      <th width="100" class="text-center">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${emailList}" var="email">
    <tr>
      <td>
        <c:out value="${email.email}" />
      </td>
      <td>
        <c:out value="${email.firstName}" />
        <c:out value="${email.lastName}" />
      </td>
      <td><small><c:out value="${geoip:location(email.ipAddress, '--')}"/></small></td>
      <td class="text-center"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${email.lastEmailed}" /></td>
      <td class="text-center">
        <c:choose>
          <c:when test="${!empty email.subscribed}"><span class="label success">Yes</span></c:when>
          <c:otherwise><span class="label secondary">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <c:choose>
          <c:when test="${!empty email.unsubscribed}"><span class="label warning">Yes</span></c:when>
          <c:otherwise><span class="label secondary">No</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <%--<a href="${ctx}/admin/mailing-list?emailId=${email.id}&returnPage=/admin/mailing-lists"><i class="fas fa-edit"></i></a>--%>
        <%--<a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&emailId=${email.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(email.email)}" />?');"><i class="fa fa-remove"></i></a>--%>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty emailList}">
      <tr>
        <td colspan="7">No email addresses were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
