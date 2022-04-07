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
<%@ page contentType="text/html; charset=UTF-8" %>
<%@ page pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="pageRenderInfo" class="com.simisinc.platform.presentation.controller.cms.PageRenderInfo" scope="request"/>
<!doctype html>
<html class="no-js" lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta http-equiv="x-ua-compatible" content="ie=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title><c:out value="${sitePropertyMap['site.name']}"/></title>
</head>
<body>
<c:forEach items="${pageRenderInfo.sectionRenderInfoList}" var="section">
  <c:if test="${section.hr}">
    <hr/>
  </c:if>
  <c:choose>
    <c:when test="${!empty section.cssClass}">
  <div class="${section.cssClass}"<c:if test="${!empty section.cssStyle}"> style="<c:out value="${section.cssStyle}" />"</c:if>>
    </c:when>
    <c:otherwise>
  <div class="grid-container"<c:if test="${!empty section.cssStyle}"> style="<c:out value="${section.cssStyle}" />"</c:if>>
    <div class="grid-x grid-margin-x">
    </c:otherwise>
  </c:choose>
  <c:forEach items="${section.columnRenderInfoList}" var="column">
    <c:choose>
      <c:when test="${!empty column.cssClass}">
      <div class="${column.cssClass}"<c:if test="${!empty column.cssStyle}"> style="<c:out value="${column.cssStyle}" />"</c:if>>
      </c:when>
      <c:otherwise>
        <div class="small-12 cell"<c:if test="${!empty column.cssStyle}"> style="<c:out value="${column.cssStyle}" />"</c:if>>
      </c:otherwise>
    </c:choose>
    <c:forEach items="${column.widgetRenderInfoList}" var="widget">
      <c:choose>
        <c:when test="${!empty widget.cssClass}">
          <div class="${widget.cssClass}"<c:if test="${!empty widget.cssStyle}"> style="<c:out value="${widget.cssStyle}" />"</c:if>>
        </c:when>
        <c:otherwise>
          <div<c:if test="${!empty widget.cssStyle}"> style="<c:out value="${widget.cssStyle}" />"</c:if>>
        </c:otherwise>
      </c:choose>
      ${widget.content}
          </div>
    </c:forEach>
        </div>
  </c:forEach>
  <c:choose>
    <c:when test="${!empty section.cssClass}">
        </div>
    </c:when>
    <c:otherwise>
      </div>
    </div>
    </c:otherwise>
  </c:choose>
</c:forEach>
</body>
</html>
