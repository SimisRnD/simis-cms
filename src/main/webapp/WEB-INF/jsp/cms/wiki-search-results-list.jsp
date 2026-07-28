<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h4 class="margin-bottom-20"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<c:forEach items="${searchResultList}" var="searchResult" varStatus="status">
  <div class="platform-content-search-result margin-top-10">
    <c:choose>
      <c:when test="${!empty searchResult.pageTitle}">
        <h5><a href="${ctx}${searchResult.link}"><c:out value="${searchResult.pageTitle}"/></a></h5>
      </c:when>
      <c:otherwise>
        <h5><a href="${ctx}${searchResult.link}"><c:out value="${searchResult.link}"/></a></h5>
      </c:otherwise>
    </c:choose>
    <c:if test="${!empty searchResult.pageDescription}">
      <p><c:out value="${searchResult.pageDescription}" /></p>
    </c:if>
    <p>${searchResult.htmlExcerpt}</p>
  </div>
</c:forEach>
