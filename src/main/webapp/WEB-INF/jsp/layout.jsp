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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="headerRenderInfo" class="com.simisinc.platform.presentation.controller.HeaderRenderInfo" scope="request"/>
<jsp:useBean id="footerRenderInfo" class="com.simisinc.platform.presentation.controller.FooterRenderInfo" scope="request"/>
<jsp:useBean id="pageRenderInfo" class="com.simisinc.platform.presentation.controller.PageRenderInfo" scope="request"/>
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="socialPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="masterMenuTabList" class="java.util.ArrayList" scope="request"/>
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
<%-- Issue #1726: sticky widgets in the body renderer anchor their bottom to #platform-footer, but
     that id is emitted by layout-footer-renderer.jspf alone -- so only when the footer block below
     runs *and* the footer theme is "custom". Anchoring to an element that is not on the page makes
     Foundation's Sticky._parsePoints call .offset() on an empty jQuery set and throw a TypeError,
     which is what /admin saw: no footer is rendered there at all. Both facts are decided here, once,
     so the anchor and the footer cannot drift apart. Set before the body renderer because that is
     what writes the anchor. container-layout.jsp includes the same renderer and sets neither: an
     unset flag is false, which is right, since it renders no footer either. --%>
<c:set var="renderPageFooter" scope="request" value="${(controllerShowMainMenu eq 'true' && !fn:startsWith(pageRenderInfo.name, '/admin') && pageRenderInfo.name ne '/content-editor') || pageRenderInfo.name eq '/admin/theme-properties'}"/>
<c:set var="platformFooterAnchorExists" scope="request" value="${renderPageFooter && 'custom' eq themePropertyMap['theme.footer.style']}"/>
<%-- Widget Renderer --%>
<c:set var="rendererClass" scope="request">platform-body</c:set>
<%@ include file="layout-body-renderer.jspf" %>
<%-- Footer --%>
<c:if test="${renderPageFooter}">
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
