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
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="logoClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="logoStyle" class="java.lang.String" scope="request"/>
<jsp:useBean id="view" class="java.lang.String" scope="request"/>
<c:set var="logoSrc" scope="request" value=""/>
<c:choose>
  <c:when test="${view eq 'white'}">
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
  </c:when>
  <c:when test="${view eq 'color'}">
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo.mixed']}"/></c:set>
  </c:when>
  <c:when test="${view eq 'standard'}">
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap['theme.logo.color'] eq 'all-white'}">
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap['theme.logo.color'] eq 'color-and-white'}">
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo.mixed']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap['theme.logo.color'] eq 'text-only'}">
  </c:when>
  <c:otherwise>
    <c:set var="logoSrc" scope="request"><c:out value="${sitePropertyMap['site.logo']}"/></c:set>
  </c:otherwise>
</c:choose>
<c:choose>
  <c:when test="${!empty logoSrc}">
    <a href="${ctx}/"><img alt="Logo" <c:if test="${!empty logoClass}">class="${logoClass}" </c:if><c:if test="${!empty logoStyle}">style="${logoStyle}" </c:if>src="${logoSrc}" /></a>
  </c:when>
  <c:otherwise>
    <span class="menu-text" translate="no"><a href="${ctx}/"><c:out value="${sitePropertyMap['site.name']}"/></a></span>
  </c:otherwise>
</c:choose>
