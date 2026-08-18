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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="logoClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="logoStyle" class="java.lang.String" scope="request"/>
<jsp:useBean id="view" class="java.lang.String" scope="request"/>
<jsp:useBean id="logoColorProperty" class="java.lang.String" scope="request"/>
<jsp:useBean id="logoColorPropertyDark" class="java.lang.String" scope="request"/>
<jsp:useBean id="text" class="java.lang.String" scope="request"/>
<%-- Which theme property governs the fallback (no "view" pin) below. Defaults to the header's
     property; a caller like the footer names its own via the "colorProperty" widget preference,
     so header and footer can be configured independently. --%>
<c:set var="logoColorPropertyName" value="${empty logoColorProperty ? 'theme.logo.color' : logoColorProperty}"/>
<c:set var="logoColorPropertyNameDark" value="${empty logoColorPropertyDark ? 'theme.logo.color.dark' : logoColorPropertyDark}"/>
<c:set var="logoSrcLight" scope="request" value=""/>
<c:choose>
  <c:when test="${view eq 'white'}">
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
  </c:when>
  <c:when test="${view eq 'color'}">
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo.mixed']}"/></c:set>
  </c:when>
  <c:when test="${view eq 'standard'}">
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap[logoColorPropertyName] eq 'all-white'}">
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap[logoColorPropertyName] eq 'color-and-white'}">
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo.mixed']}"/></c:set>
  </c:when>
  <c:when test="${themePropertyMap[logoColorPropertyName] eq 'text-only'}">
  </c:when>
  <c:when test="${themePropertyMap[logoColorPropertyName] eq 'none'}">
  </c:when>
  <c:otherwise>
    <c:set var="logoSrcLight" scope="request"><c:out value="${sitePropertyMap['site.logo']}"/></c:set>
  </c:otherwise>
</c:choose>
<%-- Dark-mode source. Only auto-swaps when this widget instance hasn't pinned to one fixed
     variant via the "view" preference -- a widget that deliberately requests white/color/standard
     keeps exactly that image in both modes. Otherwise, resolved through theme.logo.color.dark (or
     the footer's own theme.footer.logo.color.dark, via the "colorPropertyDark" widget preference)
     the same way the light-mode source is resolved above, independently per location. Any blank
     result here -- an unset property (falls to the all-white logo below, the value this always
     hardcoded to before this property existed), no white logo uploaded, or an explicit text-only/
     none choice -- falls back to the light-mode source via the existing empty-check below, same as
     the original hardcoding always did. This means text-only/none isn't independently selectable
     for dark mode today: the render logic below only ever swaps between two IMAGE sources, it
     doesn't support showing an image in light mode and text/nothing in dark mode. Not a regression
     -- the prior hardcoding couldn't do this either -- just not solved by this change. --%>
<c:set var="logoSrcDark" scope="request" value=""/>
<c:if test="${empty view}">
  <c:choose>
    <c:when test="${themePropertyMap[logoColorPropertyNameDark] eq 'all-white'}">
      <c:set var="logoSrcDark" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
    </c:when>
    <c:when test="${themePropertyMap[logoColorPropertyNameDark] eq 'color-and-white'}">
      <c:set var="logoSrcDark" scope="request"><c:out value="${sitePropertyMap['site.logo.mixed']}"/></c:set>
    </c:when>
    <c:when test="${themePropertyMap[logoColorPropertyNameDark] eq 'text-only'}">
    </c:when>
    <c:when test="${themePropertyMap[logoColorPropertyNameDark] eq 'none'}">
    </c:when>
    <c:when test="${themePropertyMap[logoColorPropertyNameDark] eq 'full-color'}">
      <c:set var="logoSrcDark" scope="request"><c:out value="${sitePropertyMap['site.logo']}"/></c:set>
    </c:when>
    <c:otherwise>
      <c:set var="logoSrcDark" scope="request"><c:out value="${sitePropertyMap['site.logo.white']}"/></c:set>
    </c:otherwise>
  </c:choose>
</c:if>
<c:if test="${empty logoSrcDark}">
  <c:set var="logoSrcDark" scope="request" value="${logoSrcLight}"/>
</c:if>
<c:choose>
  <c:when test="${!empty logoSrcLight && !empty logoSrcDark && logoSrcLight ne logoSrcDark}">
    <c:set var="logoSrcsetLight" value="${image:srcset(logoSrcLight)}"/>
    <c:set var="logoSrcsetDark" value="${image:srcset(logoSrcDark)}"/>
    <a href="${ctx}/"><img alt="Logo" class="platform-logo-light<c:if test="${!empty logoClass}"> <c:out value="${logoClass}"/></c:if>" <c:if test="${!empty logoStyle}">style="<c:out value="${logoStyle}"/>" </c:if>src="<c:out value="${logoSrcLight}"/>"
      <c:if test="${not empty logoSrcsetLight}"> srcset="<c:out value="${logoSrcsetLight}"/>" sizes="200px"</c:if>
      decoding="async" /><img alt="Logo" class="platform-logo-dark<c:if test="${!empty logoClass}"> <c:out value="${logoClass}"/></c:if>" <c:if test="${!empty logoStyle}">style="<c:out value="${logoStyle}"/>" </c:if>src="<c:out value="${logoSrcDark}"/>"
      <c:if test="${not empty logoSrcsetDark}"> srcset="<c:out value="${logoSrcsetDark}"/>" sizes="200px"</c:if>
      decoding="async" /></a>
    <c:if test="${!empty text}">
      <span class="menu-text" translate="no"><a href="${ctx}/"><c:out value="${text}"/></a></span>
    </c:if>
  </c:when>
  <c:when test="${!empty logoSrcLight}">
    <c:set var="logoSrcset" value="${image:srcset(logoSrcLight)}"/>
    <a href="${ctx}/"><img alt="Logo" <c:if test="${!empty logoClass}">class="<c:out value="${logoClass}"/>" </c:if><c:if test="${!empty logoStyle}">style="<c:out value="${logoStyle}"/>" </c:if>src="<c:out value="${logoSrcLight}"/>"
      <c:if test="${not empty logoSrcset}"> srcset="<c:out value="${logoSrcset}"/>" sizes="200px"</c:if>
      decoding="async" /></a>
    <c:if test="${!empty text}">
      <span class="menu-text" translate="no"><a href="${ctx}/"><c:out value="${text}"/></a></span>
    </c:if>
  </c:when>
  <c:otherwise>
    <span class="menu-text" translate="no"><a href="${ctx}/"><c:out value="${sitePropertyMap['site.name']}"/></a></span>
  </c:otherwise>
</c:choose>
