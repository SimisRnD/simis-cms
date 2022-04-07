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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="collectionList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${!empty collectionList}">
    <ul>
      <c:forEach items="${collectionList}" var="collection">
        <li>
          <c:if test="${!empty collection.icon}"><i class="${font:fad()} fa-<c:out value="${collection.icon}" />"></i></c:if>
          <c:choose>
            <c:when test="${!empty collection.listingsLink}">
              <a href="${ctx}${collection.listingsLink}"><c:out value="${collection.name}" /></a>
            </c:when>
            <c:otherwise>
              <a href="${ctx}/directory/${collection.uniqueId}"><c:out value="${collection.name}" /></a>
            </c:otherwise>
          </c:choose>
        </li>
      </c:forEach>
    </ul>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No directories were found
    </p>
  </c:otherwise>
</c:choose>
