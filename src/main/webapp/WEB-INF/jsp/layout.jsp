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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="headerRenderInfo" class="com.simisinc.platform.presentation.controller.cms.HeaderRenderInfo" scope="request"/>
<jsp:useBean id="footerRenderInfo" class="com.simisinc.platform.presentation.controller.cms.FooterRenderInfo" scope="request"/>
<jsp:useBean id="pageRenderInfo" class="com.simisinc.platform.presentation.controller.cms.PageRenderInfo" scope="request"/>
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="socialPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="masterMenuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="masterCollectionList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="masterWebPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="controllerShowMainMenu" class="java.lang.String" scope="request"/>
<%-- Navigation --%>
<c:if test="${controllerShowMainMenu eq 'true'}">
  <c:choose>
    <c:when test="${fn:startsWith(pageRenderInfo.name, '/checkout')}">
      <%@ include file="layout-header-checkout.jspf" %>
    </c:when>
    <c:when test="${'custom' eq themePropertyMap['theme.menu.location']}">
      <%@ include file="layout-header-renderer.jspf" %>
    </c:when>
    <c:otherwise>
      <%@ include file="layout-header-standard.jspf" %>
    </c:otherwise>
  </c:choose>
</c:if>
<%-- Widget Renderer --%>
<c:set var="rendererClass" scope="request">platform-body</c:set>
<%@ include file="layout-body-renderer.jspf" %>
<%-- Footer --%>
<c:if test="${(controllerShowMainMenu eq 'true' && !fn:startsWith(pageRenderInfo.name, '/admin') && pageRenderInfo.name ne '/content-editor') || pageRenderInfo.name eq '/admin/theme-properties'}">
  <c:choose>
    <c:when test="${'none' eq themePropertyMap['theme.footer.style']}">

    </c:when>
    <c:when test="${'custom' eq themePropertyMap['theme.footer.style']}">
      <%@ include file="layout-footer-renderer.jspf" %>
    </c:when>
    <c:otherwise>
      <%@ include file="layout-footer-standard.jspf" %>
    </c:otherwise>
  </c:choose>
</c:if>
