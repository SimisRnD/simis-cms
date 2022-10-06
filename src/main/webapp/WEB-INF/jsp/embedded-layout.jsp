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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="g" uri="http://granule.com/tags" %>
<jsp:useBean id="masterWebPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="pageRenderInfo" class="com.simisinc.platform.presentation.controller.PageRenderInfo" scope="request"/>
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<!doctype html>
<html class="no-js" lang="en">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="x-ua-compatible" content="ie=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <link rel="apple-touch-icon" type="image/png" href="${systemPropertyMap['system.www.context']}/images/apple-touch-icon.png">
  <link rel="icon" type="image/png" href="${systemPropertyMap['system.www.context']}/images/favicon.png">
  <c:choose>
    <c:when test="${!empty masterWebPage.title}"><title><c:out value="${masterWebPage.title}"/> | <c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:when>
    <c:when test="${!empty pageRenderInfo.title}"><title><c:out value="${pageRenderInfo.title}"/> | <c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:when>
    <c:otherwise><title><c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:otherwise>
  </c:choose>
  <c:choose>
    <c:when test="${!empty masterWebPage.keywords}"><meta name="keywords" content="<c:out value="${masterWebPage.keywords}"/>"></c:when>
    <c:when test="${!empty pageRenderInfo.keywords}"><meta name="keywords" content="<c:out value="${pageRenderInfo.keywords}"/>"></c:when>
    <c:otherwise><meta name="keywords" content="<c:out value="${sitePropertyMap['site.keywords']}"/>"></c:otherwise>
  </c:choose>
  <c:choose>
    <c:when test="${!empty masterWebPage.description}"><meta name="description" content="<c:out value="${masterWebPage.description}"/>"></c:when>
    <c:when test="${!empty pageRenderInfo.description}"><meta name="description" content="<c:out value="${pageRenderInfo.description}"/>"></c:when>
    <c:otherwise><meta name="description" content="<c:out value="${sitePropertyMap['site.description']}"/>"></c:otherwise>
  </c:choose>
  <g:compress>
    <c:if test="${!empty themePropertyMap['theme.fonts.body']}">
      <link rel="stylesheet" href="${ctx}/css/google-fonts/${themePropertyMap['theme.fonts.body']}.css">
    </c:if>
    <c:if test="${!empty themePropertyMap['theme.fonts.headlines'] && themePropertyMap['theme.fonts.headlines'] ne themePropertyMap['theme.fonts.body']}">
      <link rel="stylesheet" href="${ctx}/css/google-fonts/${themePropertyMap['theme.fonts.headlines']}.css">
    </c:if>
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/all.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/v4-shims.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/v5-font-face.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/foundation.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/motion-ui.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/animate-3.7.2/animate.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-datepicker-20180424/foundation-datepicker.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/javascript/autocomplete-1.0.7/auto-complete.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/javascript/swiper-8.4.2/swiper-bundle.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/platform.css" />
  </g:compress>
  <c:if test="${!empty themePropertyMap}">
    <g:compress>
      <style><%-- Prevent top-bar flicker --%>
        .no-js .top-bar { display: none; }
        @media screen and (min-width: 40em) {
          .no-js .top-bar { display: block; }
          .no-js .title-bar { display: none; }
        }
        <c:if test="${!empty themePropertyMap['theme.fonts.body']}">
          <c:choose>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'abel'}">body { font-family: 'Abel', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'bakbak-one'}">body { font-family: 'Bakbak One', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'inter'}">body { font-family: 'Inter', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'lato'}">body { font-family: 'Lato', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'libre-baskerville'}">body { font-family: 'Libre Baskerville', serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'muli'}">body { font-family: 'Muli', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'open-sans'}">body { font-family: 'Open Sans', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'oswald'}">body { font-family: 'Oswald', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'oxygen'}">body { font-family: 'Oxygen', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'poppins'}">body { font-family: 'Poppins', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'questrial'}">body { font-family: 'Questrial', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'rubik'}">body { font-family: 'Rubik', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.body'] eq 'source-sans-pro'}">body { font-family: 'Source Sans Pro', sans-serif;font-weight: 400; }</c:when>
          </c:choose>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.fonts.headlines']}">
          <c:choose>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'abel'}">h1, h2, h3, h4, h5, h6 { font-family: 'Abel', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'bakbak-one'}">h1, h2, h3, h4, h5, h6 { font-family: 'Bakbak One', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'inter'}">h1, h2, h3, h4, h5, h6 { font-family: 'Inter', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'lato'}">h1, h2, h3, h4, h5, h6 { font-family: 'Lato', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'libre-baskerville'}">h1, h2, h3, h4, h5, h6 { font-family: 'Libre Baskerville', serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'muli'}">h1, h2, h3, h4, h5, h6 { font-family: 'Muli', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'open-sans'}">h1, h2, h3, h4, h5, h6 { font-family: 'Open Sans', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'oswald'}">h1, h2, h3, h4, h5, h6 { font-family: 'Oswald', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'oxygen'}">h1, h2, h3, h4, h5, h6 { font-family: 'Oxygen', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'poppins'}">h1, h2, h3, h4, h5, h6 { font-family: 'Poppins', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'questrial'}">h1, h2, h3, h4, h5, h6 { font-family: 'Questrial', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'rubik'}">h1, h2, h3, h4, h5, h6 { font-family: 'Rubik', sans-serif;font-weight: 400; }</c:when>
            <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'source-sans-pro'}">h1, h2, h3, h4, h5, h6 { font-family: 'Source Sans Pro', sans-serif;font-weight: 400; }</c:when>
          </c:choose>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:<c:out value="${themePropertyMap['theme.body.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.body.backgroundColor']}">body{background-color:<c:out value="${themePropertyMap['theme.body.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.utilitybar.backgroundColor']}">#platform-menu .utility-bar{background-color:<c:out value="${themePropertyMap['theme.utilitybar.backgroundColor']}" />}</c:if>
      <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">#platform-menu,#platform-small-menu,#platform-small-menu .title-bar,#platform-small-toggle-menu .drilldown a{background-color:<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">.callout.header{background-color:<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">#platform-menu ul.menu li a,#platform-small-menu ul.menu li a,#platform-small-menu .title-bar-title{color:<c:out value="${themePropertyMap['theme.topbar.menu.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">.callout.header{color:<c:out value="${themePropertyMap['theme.topbar.menu.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.arrow.color']}">.dropdown.menu>li.is-dropdown-submenu-parent>a::after{border-color:<c:out value="${themePropertyMap['theme.topbar.menu.arrow.color']}" /> transparent transparent}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.dropdown.backgroundColor']}">#platform-menu .is-dropdown-submenu-item{background-color:<c:out value="${themePropertyMap['theme.topbar.menu.dropdown.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.dropdown.text.color']}">#platform-menu .is-dropdown-submenu-item a{color:<c:out value="${themePropertyMap['theme.topbar.menu.dropdown.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.hoverBackgroundColor']}">#platform-menu ul.menu li a:hover,#platform-menu .is-active{background-color:<c:out value="${themePropertyMap['theme.topbar.menu.text.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.hoverTextColor']}">#platform-menu ul.menu li > a:hover,#platform-menu ul.menu li.is-active > a,#platform-menu .is-active .is-dropdown-submenu-item a:hover{color:<c:out value="${themePropertyMap['theme.topbar.menu.hoverTextColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.text.color']}">.button{color:<c:out value="${themePropertyMap['theme.button.text.color']}" /> !important}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.default.backgroundColor']}">.button{background-color:<c:out value="${themePropertyMap['theme.button.default.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.default.hoverBackgroundColor']}">.button:hover, .button:focus{background-color:<c:out value="${themePropertyMap['theme.button.default.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.primary.backgroundColor']}">.button.primary{background-color:<c:out value="${themePropertyMap['theme.button.primary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.primary.hoverBackgroundColor']}">.button.primary:hover, .button.primary:focus{background-color:<c:out value="${themePropertyMap['theme.button.primary.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.secondary.backgroundColor']}">.button.secondary{background-color:<c:out value="${themePropertyMap['theme.button.secondary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.secondary.hoverBackgroundColor']}">.button.secondary:hover, .button.secondary:focus{background-color:<c:out value="${themePropertyMap['theme.button.secondary.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.success.backgroundColor']}">.button.success{background-color:<c:out value="${themePropertyMap['theme.button.success.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.success.hoverBackgroundColor']}">.button.success:hover, .button.success:focus{background-color:<c:out value="${themePropertyMap['theme.button.success.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.warning.backgroundColor']}">.button.warning{background-color:<c:out value="${themePropertyMap['theme.button.warning.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.warning.hoverBackgroundColor']}">.button.warning:hover, .button.warning:focus{background-color:<c:out value="${themePropertyMap['theme.button.warning.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.alert.backgroundColor']}">.button.alert{background-color:<c:out value="${themePropertyMap['theme.button.alert.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.alert.hoverBackgroundColor']}">.button.alert:hover, .button.alert:focus{background-color:<c:out value="${themePropertyMap['theme.button.alert.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.backgroundColor']}">.platform-footer{background-color:<c:out value="${themePropertyMap['theme.footer.backgroundColor']}" />}.platform-footer .fa-inverse{color:<c:out value="${themePropertyMap['theme.footer.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.text.color']}">.platform-footer{color:<c:out value="${themePropertyMap['theme.footer.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.links.color']}">.platform-footer a{color:<c:out value="${themePropertyMap['theme.footer.links.color']}" />}</c:if>
      </style>
    </g:compress>
  </c:if>
  <%-- Javascript before content--%>
  <g:compress>
    <script src="${ctx}/javascript/jquery-3.6.1/jquery-3.6.1.min.js"></script>
    <script src="${ctx}/javascript/foundation-datepicker-20180424/foundation-datepicker.min.js"></script>
    <script src="${ctx}/javascript/autocomplete-1.0.7/auto-complete.js"></script>
    <script src="${ctx}/javascript/js-cookie-2.2.1/js.cookie.min.js"></script>
  </g:compress>
</head>
<body<c:if test="${pageRenderInfo.name eq '/'}"> id="body-home"</c:if><c:if test="${!empty pageRenderInfo.cssClass}"> class="<c:out value="${pageRenderInfo.cssClass}" />"</c:if>>
  <div class="web-content">
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
</div>
<%-- Javascript after content--%>
  <g:compress>
    <script src="${ctx}/javascript/foundation-6.6.3/what-input-5.2.6.min.js"></script>
    <script src="${ctx}/javascript/foundation-6.6.3/foundation.min.js"></script>
    <script>
      $(document).foundation();
      <%--
      $('.card-profile-stats-more-link').click(function(e){
        e.preventDefault();
        if ( $(".card-profile-stats-more-content").is(':hidden') ) {
          $('.card-profile-stats-more-link').find('i').removeClass('fa-angle-down').addClass('fa-angle-up');
        } else {
          $('.card-profile-stats-more-link').find('i').removeClass('fa-angle-up').addClass('fa-angle-down');
        }
        $(this).next('.card-profile-stats-more-content').slideToggle();
      });
      --%>
    </script>
  </g:compress>
</body>
</html>
