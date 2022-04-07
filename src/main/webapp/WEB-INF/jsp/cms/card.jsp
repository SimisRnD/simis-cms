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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<jsp:useBean id="classData" class="java.lang.String" scope="request"/>
<jsp:useBean id="title" class="java.lang.String" scope="request"/>
<jsp:useBean id="icon" class="java.lang.String" scope="request"/>
<jsp:useBean id="link" class="java.lang.String" scope="request"/>
<jsp:useBean id="linkTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="linkIcon" class="java.lang.String" scope="request"/>
<div class="<c:out value="${classData}" />"<c:if test="${!empty link}"> onclick="window.location.href='${ctx}${link}'"</c:if>>
  <c:if test="${!empty title}">
    <div class="card-divider"><c:out value="${title}" /></div>
  </c:if>
  <%--<img src="${ctx}/images/templates/${url:encodeUri(template.imagePath)}">--%>
  <div class="card-section">
    <c:if test="${!empty icon}">
      <p>
        <c:choose>
          <c:when test="${fn:contains(icon, ',')}">
            <c:forEach items="${fn:split(icon, ',')}" var="thisIcon">
              <i class="fa fa-3x fa-<c:out value="${thisIcon}"/>"></i>
            </c:forEach>
          </c:when>
          <c:otherwise>
            <i class="fa fa-3x fa-<c:out value="${icon}"/>"></i>
          </c:otherwise>
        </c:choose>
      </p>
    </c:if>
    <c:if test="${!empty linkTitle}">
      <p class="button round tiny"><c:out value="${linkTitle}"/><c:if test="${!empty linkIcon}"> <i class="fa fa-<c:out value="${linkIcon}"/>"></i></c:if></p>
    </c:if>
  </div>
  <%--
  <div class="card-section">
    <p>
      <small><c:out value="${template.name}"/></small>
    </p>
  </div>
  --%>
</div>