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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="courseList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="showBullets" class="java.lang.String" scope="request"/>
<jsp:useBean id="showLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="useItemLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="showLaunchLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="launchLabel" class="java.lang.String" scope="request"/>
<jsp:useBean id="noRecordsFoundMessage" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${!empty courseList}">
    <ul<c:if test="${showBullets eq 'false'}"> class="no-bullet"</c:if>>
      <c:forEach items="${courseList}" var="item">
        <li>
          <c:choose>
            <c:when test="${useItemLink eq 'true' && !empty item.course.url && (fn:startsWith(item.course.url, 'http://') || fn:startsWith(item.course.url, 'https://'))}">
              <a target="_blank" href="${item.course.url}"><c:out value="${item.course.name}"/></a>
            </c:when>
            <c:otherwise>
              <c:out value="${item.course.name}" />
            </c:otherwise>
          </c:choose>
          <c:if test="${showLaunchLink eq 'true' && !empty item.course.url}">
            <c:if test="${fn:startsWith(item.course.url, 'http://') || fn:startsWith(item.course.url, 'https://')}">
              <a href="${url:encode(item.course.url)}" class="button primary tiny margin-0" style="padding: 0.2rem .2rem;" target="_blank" rel="nofollow" title="Visit <c:out value="${text:trim(item.course.url, 30, true)}"/>"><c:out value="${launchLabel}"/> <i class="fa fa-external-link"></i></i></a>
            </c:if>
          </c:if>
          <c:if test="${item.course.enrollments gt 0}">
            <c:choose>
              <c:when test="${item.course.enrollments eq 1}">
                <span class="margin-left-5 subheader">(${item.course.enrollments} participant)</span>
              </c:when>
              <c:otherwise>
                <span class="margin-left-5 subheader">(${item.course.enrollments} participants)</span>
              </c:otherwise>
            </c:choose>
          </c:if>
        </li>
      </c:forEach>
    </ul>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      <c:out value="${noRecordsFoundMessage}" />
    </p>
  </c:otherwise>
</c:choose>
