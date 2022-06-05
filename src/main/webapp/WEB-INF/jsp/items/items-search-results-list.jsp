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
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="searchName" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchLocation" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${!empty itemList}">
    <p class="search-results subheader">
      <c:choose>
        <c:when test="${recordPaging.totalRecordCount == 0}">
          No search results found
        </c:when>
        <c:when test="${recordPaging.totalRecordCount == 1}">
          Found 1 result
        </c:when>
        <c:when test="${recordPaging.totalRecordCount < 0}">
          Found <fmt:formatNumber value="${itemList.size()}" /> results
        </c:when>
        <c:otherwise>
          Found <fmt:formatNumber value="${recordPaging.totalRecordCount}" /> results
        </c:otherwise>
      </c:choose>
      for <strong>&quot;<c:out value="${searchName}" />&quot;</strong><c:if test="${!empty searchLocation}">
      near <strong>&quot;<c:out value="${searchLocation}" />&quot;</strong>
      </c:if><c:if test="${itemList.size() < recordPaging.totalRecordCount}">, showing the first ${recordPaging.pageSize}...</c:if>
    </p>
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
          <a href="${ctx}/show/${item.uniqueId}" translate="no"><c:out value="${item.name}" /></a>
          <c:if test="${!empty item.city}"><small class="subheader"><c:out value="${item.city}" /></small></c:if>
          <c:if test="${!empty item.summary}">
            <br /><span class="subheader"><c:out value="${text:trim(html:text(item.summary), 200, true)}" /></span>
          </c:if>
        </li>
      </c:forEach>
    </ul>
  </c:when>
  <c:otherwise>
    <p class="search-results subheader">
      No search results found for <strong>&quot;<c:out value="${searchName}" />&quot;</strong>
      <c:if test="${!empty searchLocation}">
        near <strong>&quot;<c:out value="${searchLocation}" />&quot;</strong>
      </c:if>
    </p>
  </c:otherwise>
</c:choose>
