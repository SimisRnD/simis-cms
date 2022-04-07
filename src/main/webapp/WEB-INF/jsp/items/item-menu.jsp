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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="itemTabList" class="java.util.ArrayList" scope="request"/>
<style>
  <%-- Small menu is title-bar --%>
  <%-- Medium and up is menu-bar --%>
  .item-menu.menu-bar {
      min-height: 90px;
      padding: .7rem 1.5rem;
      display: flex;
      flex-wrap: nowrap;
      justify-content: space-between;
      align-items: center;
  }
  .item-menu.menu-bar .item-menu-container {
      display: flex;
  }
  .item-menu.menu-bar .item-menu-items {
      display: flex;
      flex-direction: row;
      align-items: center;
  }
  .item-menu.menu-bar .menu-text,
  .item-menu.menu-bar .item-logo {
      font-size: 1.5em;
      line-height: 1;
      font-weight: 700;
      /*white-space: nowrap;*/
  }
  .item-menu.menu-bar .collection-name {
      display: inline;
      font-size: 0.6em;
      padding: 0;
      font-weight: normal;
      text-decoration: underline;
  }
  .item-menu.menu-bar .item-logo,
  .item-menu.menu-bar .menu-center {
      display: flex;
      flex-direction: row;
      align-items: center;
      flex-wrap: wrap;
  }
  .item-menu.menu-bar div > ul > li > a {
      margin-right: 2px;
      margin-bottom: 2px;
      <c:if test="${!empty collection.menuBorderColor && collection.menuBorderColor ne 'transparent'}">
        border: 1px solid <c:out value="${collection.menuBorderColor}" />;
      </c:if>
      border-radius: 8px;
  }
  .item-menu.menu-bar div > ul > li > a:hover,
  .item-menu.menu-bar .dropdown.menu > li.is-active > a {
      text-decoration: underline;
      text-underline-position: under;
      text-decoration-color: #ffffff;
      text-decoration-thickness: 1px;
  }
  .item-menu.menu-bar ul.submenu.menu {
      z-index: 500;
  }
  @media screen and (max-width: 39.99999em) {
      .item-menu.menu-bar div > ul > li > a {
          padding: 14px;
      }
  }
  @media screen and (min-width: 40em) {
      .item-menu.menu-bar {
          border-radius: 6px;
      }
  }
</style>
<%-- Small Menu --%>
<div class="item-menu title-bar" data-responsive-toggle="responsive-item-menu" data-hide-for="medium">
  <button class="menu-icon" type="button" data-toggle="responsive-item-menu"></button>
  <div class="title-bar-title">
    <c:out value="${item.name}"/>
    <c:forEach items="${itemTabList}" var="tab">
      <c:if test="${tab.isActive}">
        (<c:out value="${tab.name}"/>)
      </c:if>
    </c:forEach>
  </div>
</div>
<%-- Medium Menu --%>
<div class="item-menu menu-bar" id="responsive-item-menu">
  <div class="item-menu-container">
    <c:if test="${!empty item.imageUrl || !empty collection.icon}">
      <div class="item-logo hide-for-small-only margin-right-15">
        <c:choose>
          <c:when test="${!empty item.imageUrl}">
            <div class="item-image-large">
              <img alt="item image" src="<c:out value="${item.imageUrl}"/>"/>
            </div>
          </c:when>
          <c:when test="${!empty collection.icon}">
            <i class="${font:fad()} fa-2x fa-<c:out value="${collection.icon}" />"></i>
          </c:when>
        </c:choose>
      </div>
    </c:if>
    <div class="menu-text hide-for-small-only<c:if test="${!collection.showListingsLink}"> menu-center</c:if> margin-right-25">
      <h2 class="no-gap"><c:out value="${item.name}"/></h2>
      <c:if test="${collection.showListingsLink}">
        <c:choose>
          <c:when test="${!empty collection.listingsLink}">
            <a class="collection-name" href="${ctx}${collection.listingsLink}"><c:out value="${collection.name}"/></a>
          </c:when>
          <c:otherwise>
            <a class="collection-name" href="${ctx}/directory/${collection.uniqueId}"><c:out value="${collection.name}"/></a>
          </c:otherwise>
        </c:choose>
      </c:if>
    </div>
    <c:if test="${!empty itemTabList}">
      <div class="item-menu-items">
        <ul class="menu text-no-wrap">
          <c:forEach items="${itemTabList}" var="tab">
            <c:choose>
              <c:when test="${tab.isActive}">
                <li class="menu-item is-selected"><a href="${ctx}${tab.href}"><c:out value="${tab.name}"/></a></li>
              </c:when>
              <c:otherwise>
                <li class="menu-item"><a href="${ctx}${tab.href}"><c:out value="${tab.name}"/></a></li>
              </c:otherwise>
            </c:choose>
          </c:forEach>
        </ul>
      </div>
    </c:if>
  </div>
  <c:if test="${userSession.loggedIn}">
    <div class="float-right text-right">
      <c:if test="${!collection.allowsGuests}">
        <span class="label alert"><small>PRIVATE</small></span>
      </c:if>
      <c:if test="${userSession.hasRole('admin') || collection.showSearch}">
        <ul class="dropdown menu text-left" data-dropdown-menu>
          <c:if test="${collection.showSearch}">
            <li><input type="search" placeholder="Search" style="margin-right:0"></li>
            <li>
              <button type="button" class="button">Search</button>
            </li>
          </c:if>
          <c:if test="${userSession.hasRole('admin')}">
            <li class="has-submenu">
              <a href="#"><i class="fa-fw fa fa-cog"></i></a>
              <ul class="submenu menu vertical">
                <li><a href="${ctx}/edit/${item.uniqueId}?returnPage=/show/${item.uniqueId}">Edit Item Details</a></li>
                <li><a href="${ctx}/show/${item.uniqueId}/settings">Edit Item Settings</a></li>
                <li><a href="${ctx}/admin/collection-details?collectionId=${collection.id}">Edit Collection</a></li>
              </ul>
            </li>
          </c:if>
        </ul>
      </c:if>
    </div>
  </c:if>
</div>
