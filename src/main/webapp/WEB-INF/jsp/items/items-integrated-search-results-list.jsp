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
<%@ taglib prefix="category" uri="/WEB-INF/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="searchResultList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4 class="margin-bottom-20"><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<c:forEach items="${searchResultList}" var="searchResult" varStatus="status">
  <c:set var="item" scope="request" value="${itemList[status.index]}"/>
  <div class="platform-content-search-result margin-top-10">
    <h5>
      <a href="${ctx}${searchResult.link}"><c:out value="${searchResult.pageTitle}"/></a>
      <c:if test="${!empty item.city}"><small class="subheader"><c:out value="${item.city}" /></small></c:if>
      <c:if test="${item.categoryId gt 0}">
        <span class="label" style="${category:headerColorCSS(item.categoryId)}"><c:out value="${category:name(item.categoryId)}" /></span>
      </c:if>
    </h5>
    <c:choose>
      <c:when test="${!empty searchResult.htmlExcerpt}">
        <p>${searchResult.htmlExcerpt}</p>
      </c:when>
      <c:when test="${!empty searchResult.pageDescription}">
        <p><c:out value="${searchResult.pageDescription}" /></p>
      </c:when>
    </c:choose>
  </div>
</c:forEach>
