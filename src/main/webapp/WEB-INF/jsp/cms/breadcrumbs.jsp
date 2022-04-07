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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="linkList" class="java.util.LinkedHashMap" scope="request"/>
<nav aria-label="You are here:" role="navigation">
  <ul class="breadcrumbs">
    <c:forEach items="${linkList}" var="link" varStatus="status">
      <c:choose>
      <c:when test="${!empty link.value}">
        <li><a href="${ctx}${link.value}"><c:out value="${link.key}"/></a></li>
      </c:when>
        <c:otherwise>
          <li>
            <span class="show-for-sr">Current: </span> <c:out value="${link.key}" />
          </li>
        </c:otherwise>
      </c:choose>
    </c:forEach>
  </ul>
</nav>
