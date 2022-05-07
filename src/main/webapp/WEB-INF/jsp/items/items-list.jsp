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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="category" class="com.simisinc.platform.domain.model.items.Category" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<c:choose>--%>
<%--  <c:when test="${category.id gt 0}"><c:out value="${category.name}" /></c:when>--%>
  <%--<c:otherwise>All <c:out value="${collection.name}" /></c:otherwise>--%>
<%--</c:choose>--%>
<c:choose>
  <c:when test="${!empty itemList}">
    <ul class="no-bullet">
      <c:forEach items="${itemList}" var="item">
        <li>
          <c:choose>
            <c:when test="${!empty item.imageUrl}">
              <div class="item-image">
                <img alt="item image" src="<c:out value="${item.imageUrl}"/>" />
              </div>
            </c:when>
            <c:when test="${!empty collection.icon}">
              <i class="${font:fad()} fa-<c:out value="${collection.icon}" />"></i>
            </c:when>
          </c:choose>
          <a href="${ctx}/show/${item.uniqueId}"><c:out value="${item.name}" /></a>
          <c:if test="${!empty item.city}"><small class="subheader"><c:out value="${item.city}" /></small></c:if>
          <c:if test="${empty item.approved}"><span class="label warning">Needs approval</span></c:if>
        </li>
      </c:forEach>
    </ul>
    <%-- Paging Control --%>
    <c:if test="${category.id gt 0}">
      <c:set var="recordPagingParams" scope="request" value="categoryId=${category.id}"/>
    </c:if>
    <%@include file="../paging_control.jspf" %>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No <c:out value="${fn:toLowerCase(collection.name)}"/> were found
    </p>
  </c:otherwise>
</c:choose>
