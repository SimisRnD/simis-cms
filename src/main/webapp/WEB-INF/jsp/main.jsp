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
<%@ page import="static com.simisinc.platform.ApplicationInfo.PRODUCT_NAME" %>
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="g" uri="http://granule.com/tags" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="masterWebPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="pageRenderInfo" class="com.simisinc.platform.presentation.controller.cms.PageRenderInfo" scope="request"/>
<jsp:useBean id="PageBody" class="java.lang.String" scope="request"/>
<jsp:useBean id="systemPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="sitePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="analyticsPropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="ecommercePropertyMap" class="java.util.HashMap" scope="request"/>
<!doctype html>
<html class="no-js" lang="en" xml:lang="en" xmlns="http://www.w3.org/1999/xhtml">
<head>
  <meta charset="UTF-8" />
  <meta http-equiv="x-ua-compatible" content="ie=edge">
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta http-equiv="Content-Language" content="en">
  <!--
  ========================================================================
  SimIS CMS
  https://www.simiscms.com
  ========================================================================
  -->
<c:choose>
  <c:when test="${!empty sitePropertyMap['site.header.line1'] || userSession.hasRole('admin')}">
    <c:if test="${!empty themePropertyMap['theme.utilitybar.backgroundColor']}">
      <meta name="theme-color" content="<c:out value="${themePropertyMap['theme.utilitybar.backgroundColor']}" />" media="(prefers-color-scheme: light)">
      <meta name="theme-color" content="<c:out value="${themePropertyMap['theme.utilitybar.backgroundColor']}" />" media="(prefers-color-scheme: dark)">
    </c:if>
  </c:when>
  <c:otherwise>
    <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">
      <meta name="theme-color" content="<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />" media="(prefers-color-scheme: light)">
      <meta name="theme-color" content="<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />" media="(prefers-color-scheme: dark)">
    </c:if>
  </c:otherwise>
</c:choose>
  <link rel="apple-touch-icon" type="image/png" href="${systemPropertyMap['system.www.context']}/images/apple-touch-icon.png">
  <link rel="icon" type="image/png" href="${systemPropertyMap['system.www.context']}/images/favicon.png">
  <c:choose>
    <c:when test="${!empty pageRenderInfo.title}"><title><c:out value="${pageRenderInfo.title}"/> | <c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:when>
    <c:when test="${!empty masterWebPage.title}"><title><c:out value="${masterWebPage.title}"/> | <c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:when>
    <c:otherwise><title><c:out value="${sitePropertyMap['site.name']}"/><c:if test="${!empty sitePropertyMap['site.name.keyword']}"> - <c:out value="${sitePropertyMap['site.name.keyword']}"/></c:if></title></c:otherwise>
  </c:choose>
  <c:choose>
    <c:when test="${!empty pageRenderInfo.keywords}"><meta name="keywords" content="<c:out value="${pageRenderInfo.keywords}"/>"></c:when>
    <c:when test="${!empty masterWebPage.keywords}"><meta name="keywords" content="<c:out value="${masterWebPage.keywords}"/>"></c:when>
    <c:otherwise><meta name="keywords" content="<c:out value="${sitePropertyMap['site.keywords']}"/>"></c:otherwise>
  </c:choose>
  <c:choose>
    <c:when test="${!empty pageRenderInfo.description}"><meta name="description" content="<c:out value="${pageRenderInfo.description}"/>"></c:when>
    <c:when test="${!empty masterWebPage.description}"><meta name="description" content="<c:out value="${masterWebPage.description}"/>"></c:when>
    <c:otherwise><meta name="description" content="<c:out value="${sitePropertyMap['site.description']}"/>"></c:otherwise>
  </c:choose>
  <c:choose>
    <c:when test="${!empty pageRenderInfo.imageUrl && fn:startsWith(pageRenderInfo.imageUrl, '/')}">
      <meta name="og:image" content="<c:out value="${sitePropertyMap['site.url']}"/><c:out value="${pageRenderInfo.imageUrl}"/>">
    </c:when>
    <c:when test="${!empty sitePropertyMap['site.image'] && fn:startsWith(sitePropertyMap['site.image'], '/')}">
      <meta name="og:image" content="<c:out value="${sitePropertyMap['site.url']}"/><c:out value="${sitePropertyMap['site.image']}"/>">
    </c:when>
  </c:choose>
  <%-- CSS --%>
  <g:compress>
    <c:if test="${!empty themePropertyMap['theme.fonts.body']}">
      <link rel="stylesheet" href="${ctx}/css/google-fonts/${themePropertyMap['theme.fonts.body']}.css">
      <c:if test="${!empty themePropertyMap['theme.fonts.headlines'] && themePropertyMap['theme.fonts.headlines'] ne themePropertyMap['theme.fonts.body']}">
        <link rel="stylesheet" href="${ctx}/css/google-fonts/${themePropertyMap['theme.fonts.headlines']}.css">
      </c:if>
    </c:if>
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/all.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/v4-shims.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/${font:fontawesome()}/css/v5-font-face.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/foundation.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-6.6.3/motion-ui.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/animate-3.7.2/animate.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/css/foundation-datepicker-20180424/foundation-datepicker.min.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/javascript/autocomplete-1.0.7/auto-complete.css" />
    <link rel="stylesheet" type="text/css" href="${ctx}/javascript/swiper-6.5.8/css/swiper-bundle.min.css" />
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
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'lato'}">body { font-family: 'Lato', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'libre-baskerville'}">body { font-family: 'Libre Baskerville', serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'muli'}">body { font-family: 'Muli', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'open-sans'}">body { font-family: 'Open Sans', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'oswald'}">body { font-family: 'Oswald', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'questrial'}">body { font-family: 'Questrial', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'rubik'}">body { font-family: 'Rubik', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.body'] eq 'source-sans-pro'}">body { font-family: 'Source Sans Pro', sans-serif;font-weight: 400; }</c:when>
        </c:choose>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.fonts.headlines']}">
        <c:choose>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'abel'}">h1, h2, h3, h4, h5, h6 { font-family: 'Abel', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'lato'}">h1, h2, h3, h4, h5, h6 { font-family: 'Lato', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'libre-baskerville'}">h1, h2, h3, h4, h5, h6 { font-family: 'Libre Baskerville', serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'muli'}">h1, h2, h3, h4, h5, h6 { font-family: 'Muli', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'open-sans'}">h1, h2, h3, h4, h5, h6 { font-family: 'Open Sans', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'oswald'}">h1, h2, h3, h4, h5, h6 { font-family: 'Oswald', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'questrial'}">h1, h2, h3, h4, h5, h6 { font-family: 'Questrial', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'rubik'}">h1, h2, h3, h4, h5, h6 { font-family: 'Rubik', sans-serif;font-weight: 400; }</c:when>
          <c:when test="${themePropertyMap['theme.fonts.headlines'] eq 'source-sans-pro'}">h1, h2, h3, h4, h5, h6 { font-family: 'Source Sans Pro', sans-serif;font-weight: 400; }</c:when>
        </c:choose>
        </c:if>
        <c:if test="${!empty themePropertyMap['theme.body.text.color']}">body{color:<c:out value="${themePropertyMap['theme.body.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.body.backgroundColor']}">body{background-color:<c:out value="${themePropertyMap['theme.body.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.text.color']}">.button{color:<c:out value="${themePropertyMap['theme.button.text.color']}" /> !important}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.default.backgroundColor']}">.button{background-color:<c:out value="${themePropertyMap['theme.button.default.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.default.hoverBackgroundColor']}">.button:hover, .button:focus{background-color:<c:out value="${themePropertyMap['theme.button.default.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.primary.backgroundColor']}">.button.primary{background-color:<c:out value="${themePropertyMap['theme.button.primary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.primary.hoverBackgroundColor']}">.button.primary:hover, .button.primary:focus, #platform-menu ul.menu li a.button.primary:hover{background-color:<c:out value="${themePropertyMap['theme.button.primary.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.secondary.backgroundColor']}">.button.secondary{background-color:<c:out value="${themePropertyMap['theme.button.secondary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.secondary.hoverBackgroundColor']}">.button.secondary:hover, .button.secondary:focus, #platform-menu ul.menu li a.button.secondary:hover{background-color:<c:out value="${themePropertyMap['theme.button.secondary.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.success.backgroundColor']}">.button.success{background-color:<c:out value="${themePropertyMap['theme.button.success.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.success.hoverBackgroundColor']}">.button.success:hover, .button.success:focus{background-color:<c:out value="${themePropertyMap['theme.button.success.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.warning.backgroundColor']}">.button.warning{background-color:<c:out value="${themePropertyMap['theme.button.warning.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.warning.hoverBackgroundColor']}">.button.warning:hover, .button.warning:focus{background-color:<c:out value="${themePropertyMap['theme.button.warning.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.alert.backgroundColor']}">.button.alert{background-color:<c:out value="${themePropertyMap['theme.button.alert.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.button.alert.hoverBackgroundColor']}">.button.alert:hover, .button.alert:focus{background-color:<c:out value="${themePropertyMap['theme.button.alert.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.backgroundColor']}">.callout{background-color:<c:out value="${themePropertyMap['theme.callout.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.text.color']}">.callout,.callout label{color:<c:out value="${themePropertyMap['theme.callout.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.primary.backgroundColor']}">.callout.primary{background-color:<c:out value="${themePropertyMap['theme.callout.primary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.primary.text.color']}">.callout.primary,.callout.primary label{color:<c:out value="${themePropertyMap['theme.callout.primary.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.secondary.backgroundColor']}">.callout.secondary{background-color:<c:out value="${themePropertyMap['theme.callout.secondary.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.secondary.text.color']}">.callout.secondary,.callout.secondary label{color:<c:out value="${themePropertyMap['theme.callout.secondary.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.success.backgroundColor']}">.callout.success{background-color:<c:out value="${themePropertyMap['theme.callout.success.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.success.text.color']}">.callout.success,.callout.success label{color:<c:out value="${themePropertyMap['theme.callout.success.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.warning.backgroundColor']}">.callout.warning{background-color:<c:out value="${themePropertyMap['theme.callout.warning.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.warning.text.color']}">.callout.warning,.callout.warning label{color:<c:out value="${themePropertyMap['theme.callout.warning.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.alert.backgroundColor']}">.callout.alert{background-color:<c:out value="${themePropertyMap['theme.callout.alert.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.callout.alert.text.color']}">.callout.alert,.callout.alert label{color:<c:out value="${themePropertyMap['theme.callout.alert.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.backgroundColor']}">.platform-footer{background-color:<c:out value="${themePropertyMap['theme.footer.backgroundColor']}" />}.platform-footer .fa-inverse{color:<c:out value="${themePropertyMap['theme.footer.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.text.color']}">.platform-footer,.platform-footer p{color:<c:out value="${themePropertyMap['theme.footer.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.utilitybar.text.color']}">#platform-menu .utility-bar{color:<c:out value="${themePropertyMap['theme.utilitybar.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.utilitybar.link.color']}">#platform-menu .utility-bar a{color:<c:out value="${themePropertyMap['theme.utilitybar.link.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.utilitybar.backgroundColor']}">#platform-menu .utility-bar{background-color:<c:out value="${themePropertyMap['theme.utilitybar.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.text.color']}">#platform-menu, #platform-menu .menu-text, #platform-menu .menu-text a,#platform-menu .menu-text a:hover{color:<c:out value="${themePropertyMap['theme.topbar.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">#platform-menu,#platform-small-menu,#platform-small-menu .title-bar,#platform-small-toggle-menu .drilldown a{background-color:<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.backgroundColor']}">.callout.header{background-color:<c:out value="${themePropertyMap['theme.topbar.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">#platform-menu ul.menu li a,#platform-small-menu ul.menu li a,#platform-small-menu .title-bar-title{color:<c:out value="${themePropertyMap['theme.topbar.menu.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.color']}">.callout.header, #platform-menu button.button i.fa{color:<c:out value="${themePropertyMap['theme.topbar.menu.text.color']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.arrow.color']}">.dropdown.menu>li.is-dropdown-submenu-parent>a::after{border-color:<c:out value="${themePropertyMap['theme.topbar.menu.arrow.color']}" /> transparent transparent}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.text.hoverBackgroundColor']}">#platform-menu ul.menu li a:hover,#platform-menu .is-active{background-color:<c:out value="${themePropertyMap['theme.topbar.menu.text.hoverBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.hoverTextColor']}">#platform-menu ul.menu li > a:hover,#platform-menu ul.menu li.is-active > a,#platform-menu .is-active .is-dropdown-submenu-item a:hover{color:<c:out value="${themePropertyMap['theme.topbar.menu.hoverTextColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.hoverTextColor']}">#platform-menu button.button i.fa:hover{color:<c:out value="${themePropertyMap['theme.topbar.menu.hoverTextColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.dropdown.backgroundColor']}">#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item{background-color:<c:out value="${themePropertyMap['theme.topbar.menu.dropdown.backgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.dropdown.text.color']}">#platform-menu ul.is-dropdown-submenu li.is-dropdown-submenu-item a{color:<c:out value="${themePropertyMap['theme.topbar.menu.dropdown.text.color']}" />;}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.activeBackgroundColor']}">#platform-menu ul.menu .active > a{background-color:<c:out value="${themePropertyMap['theme.topbar.menu.activeBackgroundColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.topbar.menu.activeTextColor']}">#platform-menu ul.menu .active > a{color:<c:out value="${themePropertyMap['theme.topbar.menu.activeTextColor']}" />}</c:if>
        <c:if test="${!empty themePropertyMap['theme.footer.links.color']}">.platform-footer a{color:<c:out value="${themePropertyMap['theme.footer.links.color']}" />}</c:if>
        #site-newsletter-overlay, #site-promo-overlay {
          position: fixed;
          bottom: 0;
          right: 0;
          padding: 32px 30px 70px 30px;
          z-index: 10;
          color: <c:out value="${sitePropertyMap['site.newsletter.color']}" />;
          background-color: <c:out value="${sitePropertyMap['site.newsletter.backgroundColor']}" />;
          border: 1px solid #dbdbdb;
        }
        #site-newsletter-overlay h4, #site-newsletter-overlay p, #site-promo-overlay h4, #site-promo-overlay p {
          color: <c:out value="${sitePropertyMap['site.newsletter.color']}" />;
          font-weight: bolder;
        }
        #site-newsletter-overlay {
          display: none;
        }
      </style>
    </g:compress>
  </c:if>
  <c:if test="${!empty includeGlobalStylesheet}">
    <link rel="stylesheet" type="text/css" href="${ctx}/css/custom/stylesheet.css?v=${includeGlobalStylesheetLastModified}" />
  </c:if>
  <c:if test="${!empty includeStylesheet}">
    <link rel="stylesheet" type="text/css" href="${ctx}/css/custom/stylesheet${includeStylesheet}.css?v=${includeStylesheetLastModified}" />
  </c:if>
  <c:if test="${!empty pageCollection}">
    <style>
        <c:if test="${!empty pageCollection.headerBgColor}">.item-menu.menu-bar,.item-menu.title-bar{background-color:<c:out value="${pageCollection.headerBgColor}" />}</c:if>
        <c:if test="${!empty pageCollection.headerTextColor}">.item-menu.menu-bar, .item-menu.menu-bar .menu-text, .item-menu.menu-bar .collection-name, .item-menu.menu-bar i {color:<c:out value="${pageCollection.headerTextColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuTextColor}">.item-menu.menu-bar div > ul > li > a {color:<c:out value="${pageCollection.menuTextColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuBgColor}">.item-menu.menu-bar div > ul > li > a {background-color:<c:out value="${pageCollection.menuBgColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuBorderColor}">.item-menu.menu-bar div > ul > li > a {border:1px solid <c:out value="${pageCollection.menuBorderColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuActiveTextColor}">.item-menu.menu-bar div > ul > li.is-selected > a {color:<c:out value="${pageCollection.menuActiveTextColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuActiveBgColor}">.item-menu.menu-bar div > ul > li.is-selected > a {background-color:<c:out value="${pageCollection.menuActiveBgColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuActiveBorderColor}">.item-menu.menu-bar div > ul > li.is-selected > a {border:1px solid <c:out value="${pageCollection.menuActiveBorderColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuHoverTextColor}">.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a {color:<c:out value="${pageCollection.menuHoverTextColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuHoverBgColor}">.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a {background-color:<c:out value="${pageCollection.menuHoverBgColor}" />}</c:if>
        <c:if test="${!empty pageCollection.menuHoverBorderColor}">.item-menu.menu-bar div > ul > li > a:hover, .item-menu.menu-bar .dropdown.menu > li.is-active > a {border:1px solid <c:out value="${pageCollection.menuHoverBorderColor}" />}</c:if>
    </style>
  </c:if>
  <%-- Javascript before content--%>
  <c:if test="${!fn:startsWith(pageRenderInfo.name, '/admin') && !fn:startsWith(pageRenderInfo.name, '/content-editor')}">
    <c:if test="${!empty analyticsPropertyMap['analytics.google.tagmanager'] && fn:startsWith(analyticsPropertyMap['analytics.google.tagmanager'], 'GTM-')}">
      <script>(function(w,d,s,l,i){w[l]=w[l]||[];w[l].push({'gtm.start':
      new Date().getTime(),event:'gtm.js'});var f=d.getElementsByTagName(s)[0],
      j=d.createElement(s),dl=l!='dataLayer'?'&l='+l:'';j.async=true;j.src=
      'https://www.googletagmanager.com/gtm.js?id='+i+dl;f.parentNode.insertBefore(j,f);
      })(window,document,'script','dataLayer','${js:escape(analyticsPropertyMap['analytics.google.tagmanager'])}');</script>
    </c:if>
  </c:if>
  <g:compress>
    <script src="${ctx}/javascript/jquery-3.6.0/jquery-3.6.0.min.js"></script>
    <script src="${ctx}/javascript/foundation-datepicker-20180424/foundation-datepicker.min.js"></script>
    <script src="${ctx}/javascript/autocomplete-1.0.7/auto-complete.js"></script>
    <script src="${ctx}/javascript/js-cookie-2.2.1/js.cookie.min.js"></script>
    <script src="${ctx}/javascript/swiper-6.5.8/js/swiper-bundle.min.js"></script>
  </g:compress>
</head>
<body<c:if test="${pageRenderInfo.name eq '/'}"> id="body-home"</c:if><c:if test="${!empty pageRenderInfo.cssClass}"> class="<c:out value="${pageRenderInfo.cssClass}" />"</c:if>>
  <c:choose>
    <c:when test="${fn:startsWith(pageRenderInfo.name, '/admin') && pageRenderInfo.name ne '/admin/web-page' && pageRenderInfo.name ne '/admin/web-page-designer' && pageRenderInfo.name ne '/admin/web-container-designer' && pageRenderInfo.name ne '/admin/css-editor'}">
      <%-- Draw the admin menu--%>
      <div class="off-canvas-wrapper">
        <div class="off-canvas position-left reveal-for-medium admin-menu hide-for-print" style="z-index: 1005 !important; padding-bottom: 50px" id="offCanvas" data-off-canvas>
          <div class="app-title">
            <c:out value="<%= PRODUCT_NAME %>"/><br />
            <small>v<c:out value="<%= VERSION %>"/></small>
          </div>
          <div class="app-user">
            <i class="${font:far()} fa-user fa-fw"></i>
            <c:out value="${userSession.user.fullName}"/>
          </div>
          <%-- Admin Link --%>
          <ul class="vertical menu">
            <li class="section-title">Admin</li>
            <li<c:if test="${pageRenderInfo.name eq '/admin'}"> class="is-active"</c:if>><a href="${ctx}/admin"><i class="${font:far()} fa-home fa-fw"></i> <span>Welcome</span></a></li>
            <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/documentation')}"> class="is-active"</c:if>><a href="${ctx}/admin/documentation/wiki/Home"><i class="${font:far()} fa-book fa-fw"></i> <span>Documentation</span></a></li>
            <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/activity')}"> class="is-active"</c:if>><a href="${ctx}/admin/activity"><i class="${font:far()} fa-exchange-alt fa-fw"></i> <span>Activity</span></a></li>
          </ul>
          <%-- Community menu --%>
          <c:if test="${userSession.hasRole('admin') || userSession.hasRole('community-manager')}">
            <ul class="vertical menu">
              <li class="section-title">Community</li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/community/analytics')}"> class="is-active"</c:if>><a href="${ctx}/admin/community/analytics"><i class="${font:far()} fa-chart-line fa-fw"></i> <span>Analytics</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/form-')}"> class="is-active"</c:if>><a href="${ctx}/admin/form-data"><i class="${font:far()} fa-list-alt fa-fw"></i> <span>Form Data</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/mailing-list') && !fn:startsWith(pageRenderInfo.name, '/admin/mailing-list-properties')}"> class="is-active"</c:if>><a href="${ctx}/admin/mailing-lists"><i class="${font:far()} fa-envelope fa-fw"></i> <span>Mailing Lists</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/user') || fn:startsWith(pageRenderInfo.name, '/admin/modify-user')}"> class="is-active"</c:if>><a href="${ctx}/admin/users"><i class="${font:far()} fa-user-circle fa-fw"></i> <span>Users</span></a></li>
              <c:if test="${userSession.hasRole('admin')}">
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/group')}"> class="is-active"</c:if>><a href="${ctx}/admin/groups"><i class="${font:far()} fa-users fa-fw"></i> <span>User Groups</span></a></li>
              </c:if>
            </ul>
          </c:if>
          <%-- Content menu --%>
          <c:if test="${userSession.hasRole('admin') || userSession.hasRole('content-manager')}">
            <ul class="vertical menu">
              <li class="section-title">Content</li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/content/analytics')}"> class="is-active"</c:if>><a href="${ctx}/admin/content/analytics"><i class="${font:far()} fa-chart-line fa-fw"></i> <span>Analytics</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/sitemap')}"> class="is-active"</c:if>><a href="${ctx}/admin/sitemap"><i class="${font:far()} fa-sitemap fa-fw"></i> <span>Site Map</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/web-page')}"> class="is-active"</c:if>><a href="${ctx}/admin/web-pages"><i class="${font:far()} fa-sticky-note fa-fw"></i> <span>Web Pages</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/image')}"> class="is-active"</c:if>><a href="${ctx}/admin/images"><i class="${font:far()} fa-image fa-fw"></i> <span>Images</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/content-list')}"> class="is-active"</c:if>><a href="${ctx}/admin/content-list"><i class="${font:far()} fa-th fa-fw"></i> <span>Content</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/blog')}"> class="is-active"</c:if>><a href="${ctx}/admin/blogs"><i class="${font:far()} fa-quote-right fa-fw"></i> <span>Blogs</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/calendar')}"> class="is-active"</c:if>><a href="${ctx}/admin/calendars"><i class="${font:far()} fa-calendar fa-fw"></i> <span>Calendars</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/folder')}"> class="is-active"</c:if>><a href="${ctx}/admin/folders"><i class="${font:far()} fa-copy fa-fw"></i> <span>Files &amp; Folders</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/wiki')}"> class="is-active"</c:if>><a href="${ctx}/admin/wikis"><i class="${font:far()} fa-file fa-fw"></i> <span>Wikis</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/sticky-footer-links')}"> class="is-active"</c:if>><a href="${ctx}/admin/sticky-footer-links"><i class="${font:far()} fa-file fa-fw"></i> <span>Sticky Page Buttons</span></a></li>
            </ul>
          </c:if>
          <%-- Data menu --%>
          <c:if test="${userSession.hasRole('admin') || userSession.hasRole('data-manager')}">
            <ul class="vertical menu">
              <li class="section-title">Data</li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/dataset')}"> class="is-active"</c:if>><a href="${ctx}/admin/datasets"><i class="${font:far()} fa-table fa-fw"></i> <span>Datasets</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/collection')}"> class="is-active"</c:if>><a href="${ctx}/admin/collections"><i class="${font:far()} fa-database fa-fw"></i> <span>Collections</span></a></li>
            </ul>
          </c:if>
          <%-- E-Commerce menu (if enabled if settings) --%>
          <c:if test="${!empty ecommercePropertyMap['ecommerce.enabled'] && ecommercePropertyMap['ecommerce.enabled'] eq 'true'}">
            <c:if test="${userSession.hasRole('admin') || userSession.hasRole('ecommerce-manager')}">
              <ul class="vertical menu">
                <li class="section-title">E-Commerce</li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/e-commerce/analytics')}"> class="is-active"</c:if>><a href="${ctx}/admin/e-commerce/analytics"><i class="${font:far()} fa-chart-line fa-fw"></i> <span>Analytics</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/order')}"> class="is-active"</c:if>><a href="${ctx}/admin/orders"><i class="${font:far()} fa-receipt fa-fw"></i> <span>Orders</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/customer')}"> class="is-active"</c:if>><a href="${ctx}/admin/customers"><i class="${font:far()} fa-address-book fa-fw"></i> <span>Customers</span></a></li>
                <li<c:if test="${pageRenderInfo.name eq '/admin/products' || pageRenderInfo.name eq '/admin/product'}"> class="is-active"</c:if>><a href="${ctx}/admin/products"><i class="${font:far()} fa-dolly fa-fw"></i> <span>Products</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/product-categor')}"> class="is-active"</c:if>><a href="${ctx}/admin/product-categories"><i class="${font:far()} fa-border-all fa-fw"></i> <span>Categories</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/pricing-rule')}"> class="is-active"</c:if>><a href="${ctx}/admin/pricing-rules"><i class="${font:far()} fa-tags fa-fw"></i> <span>Pricing Rules</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/sales-tax-nexus')}"> class="is-active"</c:if>><a href="${ctx}/admin/sales-tax-nexus"><i class="${font:far()} fa-balance-scale fa-fw"></i> <span>Sales Tax Nexus</span></a></li>
                <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/shipping-rates')}"> class="is-active"</c:if>><a href="${ctx}/admin/shipping-rates"><i class="${font:far()} fa-shipping-fast fa-fw"></i> <span>Shipping Rates</span></a></li>
              </ul>
            </c:if>
          </c:if>
          <%-- API, Apps, etc. --%>
          <c:if test="${userSession.hasRole('admin')}">
            <ul class="vertical menu">
              <li class="section-title">Access</li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/api')}"> class="is-active"</c:if>><a href="${ctx}/admin/apis"><i class="${font:far()} fa-paper-plane fa-fw"></i> <span>APIs</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/app')}"> class="is-active"</c:if>><a href="${ctx}/admin/apps"><i class="${font:far()} fa-mobile fa-fw"></i> <span>Apps</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/blocked-ip-list')}"> class="is-active"</c:if>><a href="${ctx}/admin/blocked-ip-list"><i class="${font:far()} fa-shield-halved fa-fw"></i> <span>Blocked IPs</span></a></li>
            </ul>
          </c:if>
          <%-- Settings menu --%>
          <c:if test="${userSession.hasRole('admin')}">
            <ul class="vertical menu">
              <li class="section-title">Settings</li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/theme')}"> class="is-active"</c:if>><a href="${ctx}/admin/theme-properties"><i class="${font:far()} fa-palette fa-fw"></i> <span>Theme</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/site-properties')}"> class="is-active"</c:if>><a href="${ctx}/admin/site-properties"><i class="${font:far()} fa-rocket fa-fw"></i> <span>Site Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/social')}"> class="is-active"</c:if>><a href="${ctx}/admin/social-media-settings"><i class="${font:far()} fa-thumbs-up fa-fw"></i> <span>Social Media</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/mail-properties')}"> class="is-active"</c:if>><a href="${ctx}/admin/mail-properties"><i class="${font:far()} fa-cogs fa-fw"></i> <span>Email Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/configure-analytics')}"> class="is-active"</c:if>><a href="${ctx}/admin/configure-analytics"><i class="${font:far()} fa-chart-line fa-fw"></i> <span>Analytics Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/captcha')}"> class="is-active"</c:if>><a href="${ctx}/admin/captcha-properties"><i class="${font:far()} fa-key fa-fw"></i> <span>Captcha Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/ecommerce')}"> class="is-active"</c:if>><a href="${ctx}/admin/ecommerce-properties"><i class="${font:far()} fa-shopping-cart fa-fw"></i> <span>E-commerce Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/mailing-list-properties')}"> class="is-active"</c:if>><a href="${ctx}/admin/mailing-list-properties"><i class="${font:far()} fa-envelope fa-fw"></i> <span>Mailing List Settings</span></a></li>
              <li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/maps')}"> class="is-active"</c:if>><a href="${ctx}/admin/maps-properties"><i class="${font:far()} fa-map fa-fw"></i> <span>Maps Settings</span></a></li>
              <%--<li<c:if test="${fn:startsWith(pageRenderInfo.name, '/admin/email-templates')}"> class="is-active"</c:if>><a href="${ctx}/admin/email-templates"><i class="${font:far()} fa-file-text fa-fw"></i> <span>Email Templates</span></a></li>--%>
            </ul>
          </c:if>
        </div>
        <div class="off-canvas-content" data-off-canvas-content>
          <div class="web-content admin-web-content">
            <jsp:include page="${PageBody}" flush="true"/>
          </div>
        </div>
      </div>
    </c:when>
    <c:otherwise>
      <%-- Draw a regular page --%>
      <c:if test="${!fn:startsWith(pageRenderInfo.name, '/content-editor')}">
        <c:if test="${!empty analyticsPropertyMap['analytics.google.tagmanager'] && fn:startsWith(analyticsPropertyMap['analytics.google.tagmanager'], 'GTM-')}">
        <noscript><iframe src="https://www.googletagmanager.com/ns.html?id=${js:escape(analyticsPropertyMap['analytics.google.tagmanager'])}"
        height="0" width="0" style="display:none;visibility:hidden"></iframe></noscript>
        </c:if>
      </c:if>
      <div class="web-content">
        <jsp:include page="${PageBody}" flush="true"/>
      </div>
      <c:if test="${!empty sitePropertyMap['site.confirmation'] && sitePropertyMap['site.confirmation'] eq 'true'}">
        <div id="site-confirmation" class="reveal full" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-out="fade-out fast">
          <div style="position:absolute; top: 50%; left: 50%; transform: translateY(-50%) translateX(-50%)">
            <div class="modal-prompt">
              <p>
                <c:choose>
                  <c:when test="${!empty sitePropertyMap['site.logo']}">
                    <img alt="Logo" style="max-width: 75%" src="<c:out value="${sitePropertyMap['site.logo']}"/>" />
                  </c:when>
                  <c:otherwise>
                    <c:out value="${sitePropertyMap['site.name']}"/>
                  </c:otherwise>
                </c:choose>
              </p>
              <p style="white-space: nowrap">
                <c:if test="${!empty sitePropertyMap['site.confirmation.line1']}">
                  <c:out value="${sitePropertyMap['site.confirmation.line1']}" />
                </c:if>
                <c:if test="${!empty sitePropertyMap['site.confirmation.line2']}">
                  <br /><c:out value="${sitePropertyMap['site.confirmation.line2']}" />
                </c:if>
              </p>
              <p>
                <button id="site-confirmation-yes" class="button secondary">Yes</button>
                <span style="display:inline-block; vertical-align: middle; height:40px;">or</span>
                <button id="site-confirmation-no" class="button secondary">No</button>
              </p>
            </div>
          </div>
        </div>
      </c:if>
      <c:choose>
        <c:when test="${!empty requestPricingRule.promoCode}">
          <div id="site-promo-overlay" class="animated slideInUp faster delay-1s hide-for-print">
            <button id="site-promo-close-button" class="close-button" type="button">
              <span><i class="${font:fal()} fa-circle-xmark"></i></span>
            </button>
            <h4>Thanks for visiting!</h4>
            <p>We've added a promo code for use on your next purchase</p>
          </div>
        </c:when>
        <c:when test="${!empty requestOverlayHeadline}">
          <div id="site-newsletter-overlay" class="animated slideInUp faster delay-3s hide-for-print">
            <button id="site-newsletter-close-button" class="close-button" type="button">
              <span><i class="${font:fal()} fa-circle-xmark"></i></span>
            </button>
            <h4><c:out value="${requestOverlayHeadline}" /></h4>
            <p><c:out value="${requestOverlayMessage}" /></p>
              <%-- Form Content --%>
            <form method="get" onsubmit="return platformNewsletterOverlaySignUp()">
              <div class="input-group">
                <input class="input-group-field" type="text" id="platformOverlayEmail" name="email" placeholder="name@example.com" required>
                <div class="input-group-button">
                  <button type="submit" class="button small">Sign Up</button>
                </div>
              </div>
              <p class="help-text" id="platformOverlayEmailHelpText"></p>
            </form>
          </div>
        </c:when>
      </c:choose>
      <c:if test="${!empty footerStickyLinks && !fn:startsWith(pageRenderInfo.name, '/admin') && !fn:startsWith(pageRenderInfo.name, '/content-editor')}">
        <style>
          #site-sticky-footer {
            position: fixed;
            bottom: 0;
            right: 0;
            text-align: right;
            padding-right: 30px;
            z-index: 4;
          }
          .site-sticky-footer-button {
            margin: 0 10px -12px 0;
            padding: 24px 20px 30px 20px;
          }
        </style>
        <div id="site-sticky-footer" class="animated slideInUp faster delay-1s hide-for-print">
        <c:forEach items="${footerStickyLinks.entries}" var="link">
          <c:choose>
            <c:when test="${fn:startsWith(pageRenderInfo.name, link.link)}">

            </c:when>
            <c:when test="${fn:startsWith(link.link, 'http://') || fn:startsWith(link.link, 'https://')}">
              <a class="button secondary site-sticky-footer-button" href="${link.link}" target="_blank"><c:out value="${link.name}"/></a>
            </c:when>
            <c:otherwise>
              <a class="button secondary site-sticky-footer-button" href="${ctx}${link.link}"><c:out value="${link.name}"/></a>
            </c:otherwise>
          </c:choose>
        </c:forEach>
        </div>
      </c:if>
    </c:otherwise>
  </c:choose>
  <%-- Javascript after content--%>
  <script>
    var mainToken = '${userSession.formToken}';
  </script>
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
      <c:if test="${!empty sitePropertyMap['site.confirmation'] && sitePropertyMap['site.confirmation'] eq 'true'}">
        var siteConfirmationCookie = 'site-confirmation';
        var foundSiteConfirmation = Cookies.get(siteConfirmationCookie);
        if (foundSiteConfirmation !== undefined) {
          Cookies.set(siteConfirmationCookie, 'valid', { expires: 30 });
        } else {
          var siteConfirmation = $('#site-confirmation');
          siteConfirmation.foundation('open');
          siteConfirmation.on('closed.zf.reveal', function () {
            Cookies.set(siteConfirmationCookie, 'valid', { expires: 30 });
          });
          var siteConfirmationYes = $('#site-confirmation-yes');
          siteConfirmationYes.on('click', function () {
            siteConfirmation.foundation('close');
          });
          var siteConfirmationNo = $('#site-confirmation-no');
          siteConfirmationNo.on('click', function () {
            alert('${js:escape(sitePropertyMap['site.confirmation.declined.text'])}');
          });
        }
      </c:if>
      <c:if test="${!empty requestOverlayHeadline}">
        <%-- Site Newsletter cookies and functions --%>
        var siteNewsletterCookie = 'site-newsletter';
        function validateEmail(email) {
          var re = /\S+@\S+\.\S+/;
          return re.test(email);
        }
        function platformNewsletterOverlaySignUp() {
          var email = document.getElementById("platformOverlayEmail").value;
          if (email === undefined || email.length === 0) {
            document.getElementById('platformOverlayEmailHelpText').innerHTML = "Please enter your email address";
            return false;
          }
          if (!validateEmail(email)) {
            document.getElementById('platformOverlayEmailHelpText').innerHTML = "Please re-enter your email address using a proper format.";
            return false;
          }
          $.getJSON("${ctx}/json/emailSubscribe?token=" + mainToken + "&email=" + encodeURIComponent(email), function(data) {
            if (data.status === undefined || data.status !== '0') {
              document.getElementById('platformOverlayEmailHelpText').innerHTML = "Please re-enter your email address using a proper format.";
              return false;
            }
            document.getElementById('platformOverlayEmailHelpText').innerHTML = "Thanks for signing up for <c:out value="${js:escape(sitePropertyMap['site.name'])}"/> emails";
            Cookies.set(siteNewsletterCookie, 'valid', { expires: 90 });
          });
          return false;
        }
      </c:if>
      $(document).ready(function() {
        <%-- // Elements can animate when they are visible on screen --%>
        function isScrolledIntoView(elem) {
          var docViewTop = $(window).scrollTop();
          var docViewBottom = docViewTop + $(window).height();
          var elemTop = $(elem).offset().top;
          var elemBottom = elemTop + $(elem).height();
          return ((elemBottom <= docViewBottom) && (elemTop >= docViewTop));
        }
        $(window).scroll(function() {
          $('.animated').each(function() {
            if (isScrolledIntoView(this) === true) {
              if ($(this).hasClass("doFadeIn")) {
                $(this).addClass('fadeIn');
              } else if ($(this).hasClass("doFadeInLeft")) {
                $(this).addClass('fadeInLeft');
              } else if ($(this).hasClass("doFadeInRight")) {
                $(this).addClass('fadeInRight');
              } else if ($(this).hasClass("doFadeInRightBig")) {
                $(this).addClass('fadeInRightBig');
              } else if ($(this).hasClass("doFadeInUp")) {
                $(this).addClass('myFadeInUp');
              } else if ($(this).hasClass("doFadeInDown")) {
                $(this).addClass('fadeInDown');
              } else if ($(this).hasClass("doSlideInLeft")) {
                $(this).addClass('slideInLeft');
              } else if ($(this).hasClass("doSlideInRight")) {
                $(this).addClass('slideInRight');
              } else if ($(this).hasClass("doSlideInUp")) {
                $(this).addClass('slideInUp');
              } else if ($(this).hasClass("doSlideInDown")) {
                $(this).addClass('slideInDown');
              }
            }
          });
        });
        <%-- // Add a smooth scroll for anchors --%>
        $(document).on('click', 'a[href^="#"]', function (event) {
          event.preventDefault();
          if ($("#platform-small-menu").is(":visible")) {
            $('html, body').animate({
              scrollTop: $($.attr(this, 'href')).offset().top - $("#platform-small-menu").height() - 20
            }, 500);
          } else {
            $('html, body').animate({
              scrollTop: $($.attr(this, 'href')).offset().top - $("#platform-menu").height() - 20
            }, 500);
          }
        });
        <c:if test="${!empty requestPricingRule.promoCode}">
        var sitePromoOverlay = $('#site-promo-overlay');
          var sitePromoCloseButton = $('#site-promo-close-button');
          sitePromoCloseButton.on('click', function () {
            sitePromoOverlay.removeClass("slideInUp delay-1s");
            sitePromoOverlay.addClass("slideOutDown");
          });
        </c:if>
        <c:if test="${!empty requestOverlayHeadline}">
          <%-- Site newsletter close button --%>
          var siteNewsletterOverlay = $('#site-newsletter-overlay');
          var siteNewsletterCloseButton = $('#site-newsletter-close-button');
          siteNewsletterCloseButton.on('click', function () {
            siteNewsletterOverlay.removeClass("slideInUp delay-3s");
            siteNewsletterOverlay.addClass("slideOutDown");
            if (siteNewsletterCookie) {
              Cookies.set(siteNewsletterCookie, 'valid', {expires: 30});
            }
          });
          if (siteNewsletterCookie) {
            var foundSiteNewsletterOverlay = Cookies.get(siteNewsletterCookie);
            if (foundSiteNewsletterOverlay !== undefined) {
              Cookies.set(siteNewsletterCookie, 'valid', {expires: 30});
            } else {
              siteNewsletterOverlay.show();
            }
          }
        </c:if>
        <%-- Detect hash and scroll to link with adjustment for menu --%>
        var hash = $(window.location.hash);
        if (hash && hash.offset()) {
          var header = $("div#platform-menu");
          var headerHeight = 10;
          if (header) {
              headerHeight += header.height();
          }
          $('html,body').animate({
              scrollTop: hash.offset().top-headerHeight
          }, 0);
        }
      });
    </script>
  </g:compress>
  <c:if test="${!fn:startsWith(pageRenderInfo.name, '/admin')}">
    <c:if test="${!empty analyticsPropertyMap['analytics.service'] && 'google' eq analyticsPropertyMap['analytics.service'] && !empty analyticsPropertyMap['analytics.google.key']}">
      <script async src="https://www.googletagmanager.com/gtag/js?id=${js:escape(analyticsPropertyMap['analytics.google.key'])}"></script>
      <script>
        window.dataLayer = window.dataLayer || [];
        function gtag(){dataLayer.push(arguments);}
        gtag('js', new Date());
        gtag('config', '${js:escape(analyticsPropertyMap['analytics.google.key'])}');
      </script>
    </c:if>
    <c:if test="${!empty analyticsPropertyMap['analytics.simplifi.value']}">
      <script async src='https://tag.simpli.fi/sifitag/${js:escape(analyticsPropertyMap['analytics.simplifi.value'])}'></script>
    </c:if>
    <c:if test="${!empty analyticsPropertyMap['analytics.brandcdn.value'] && !empty analyticsPropertyMap['analytics.brandcdn.value2']}">
      <script type="text/javascript" src="//tag.brandcdn.com/autoscript/${js:escape(analyticsPropertyMap['analytics.brandcdn.value'])}/${js:escape(analyticsPropertyMap['analytics.brandcdn.value2'])}"></script>
    </c:if>
  </c:if>
</body>
</html>
