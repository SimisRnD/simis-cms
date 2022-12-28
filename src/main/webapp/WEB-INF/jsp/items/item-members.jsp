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
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="collection" uri="/WEB-INF/tlds/collection-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="memberList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="canDelete" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${empty memberList}">
  <p class="subheader">
    No members were found
  </p>
</c:if>
<c:if test="${!empty memberList}">
  <ul class="no-bullet">
    <c:forEach items="${memberList}" var="member">
      <c:set var="user" scope="request" value="${user:userById(member.userId)}"/>
      <li>
        <c:out value="${user:name(member.userId)}" />
        <c:if test="${!empty member.roleList}">
          <c:forEach items="${member.roleList}" var="memberRole" varStatus="status">
            <span class="label primary"><c:out value="${memberRole.title}" /></span>
          </c:forEach>
        </c:if>
        <c:if test="${!empty user.city}"><small class="subheader"><c:out value="${user.city}" /></small></c:if>
        <c:if test="${canDelete eq 'true'}">
          <a title="Remove member" href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&memberId=${member.id}" onclick="return confirm('Are you sure you want to remove <c:out value="${js:escape(user:name(member.userId))}" />?');"><i class="fa fa-remove"></i></a>
        </c:if>
      </li>
    </c:forEach>
  </ul>
</c:if>