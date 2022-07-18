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
<%@ page import="static com.simisinc.platform.ApplicationInfo.VERSION" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="webPage" class="com.simisinc.platform.domain.model.cms.WebPage" scope="request"/>
<jsp:useBean id="menuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="menuTab" class="com.simisinc.platform.domain.model.cms.MenuTab" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-sitemap-editor.css?v=<%= VERSION %>" />
<link rel="stylesheet" href="${ctx}/javascript/dragula-3.7.3/dragula.min.css"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h3><c:out value="${webPage.link}" /></h3>
<c:if test="${empty webPage.pageXml}">
  <p class="subheader">Page layout does not exist! Choose a template or design the page yourself...</p>
</c:if>
<form method="post" onsubmit="return checkSiteMapOrder()" >
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="webPage" value="<c:out value="${webPage.link}" />"/>
  <input type="hidden" name="returnPage" value="${returnPage}" />

  <p>
    <textarea name="pageXml" rows="15"><c:out value="${webPage.pageXml}"/></textarea>
  </p>

    <%-- example container
  <div id="site-map-container" class="site-map-container">
    <c:forEach items="${menuTabList}" var="menuTab" varStatus="status">
      <div id="site-map-menu-tab-container-${status.first ? 0 : menuTab.id}" class="site-map-menu-tab">
        <div>
          <div style="position: absolute;right: 5px;top: 0;">
            <small>
              <c:if test="${!status.first}"><a href="javascript:deleteMenuTab(${menuTab.id});"><i class="fa fa-circle-xmark"></i></a></c:if>
            </small>
          </div>
          <div class="float-left">
            <small class="subheader">
              <c:if test="${!status.first}"><i class="fa fa-arrows-h site-map-menu-tab-drag-handle"></i></c:if>
              <c:out value="${menuTab.link}"/>
            </small>
          </div>
        </div>
        <div class="clear-float"></div>
        <div>
          <c:choose>
            <c:when test="${status.first}">
              <strong><c:out value="${menuTab.name}"/></strong>
            </c:when>
            <c:otherwise>
              <input type="text" name="menuTab${menuTab.id}name" value="<c:out value="${menuTab.name}" />"/>
            </c:otherwise>
          </c:choose>
        </div>
        <c:if test="${!status.first}">
          <div id="site-map-submenu-tab-container-${menuTab.id}" class="site-map-submenu-container">
            <c:forEach items="${menuTab.menuItemList}" var="menuItem">
              <div id="site-map-menu-item-${menuItem.id}" class="site-map-submenu-tab">
                <div style="position: absolute;right: 5px;top: 0;">
                  <small>
                    <a href="javascript:deleteMenuItem(${menuItem.id});"><i class="fa fa-circle-xmark"></i></a>
                  </small>
                </div>
                <div class="float-left">
                  <small class="subheader">
                    <i class="fa fa-arrows site-map-submenu-tab-drag-handle"></i>
                    <c:out value="${menuItem.link}" />
                  </small>
                </div>
                <div class="clear-float"></div>
                <div>
                  <input type="text" name="menuItem${menuItem.id}name" value="<c:out value="${menuItem.name}" />"/>
                </div>
              </div>
            </c:forEach>
          </div>
        </c:if>
      </div>
    </c:forEach>
  </div>
--%>


  <div>
    <p>
      <input type="submit" class="button radius success" value="Save"/>
      <c:choose>
        <c:when test="${!empty returnPage}">
          <a href="${returnPage}" class="button radius secondary">Cancel</a>
        </c:when>
        <c:when test="${!empty webPage.link}">
          <a href="${ctx}${webPage.link}" class="button radius secondary">Cancel</a>
        </c:when>
        <c:otherwise>

        </c:otherwise>
      </c:choose>
    </p>
  </div>
</form>


<script src="${ctx}/javascript/dragula-3.7.3/dragula.min.js"></script>
<script>
  var menuTabs = dragula([document.querySelector('#site-map-container')], {
    direction: 'horizontal',
    moves: function (el, container, handle) {
      return handle.classList.contains('site-map-menu-tab-drag-handle');
    },
    accepts: function (el, target, source, sibling) {
      if (sibling == null) return true;
      if (sibling.id && sibling.id === 'site-map-menu-tab-container-0') return false;
      return true;
    }
  });

  function deleteMenuTab(index) {
    if (!confirm("Are you sure you want to delete this menu tab and all of its submenu items?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&menuTabId=' + index;
  }

  function deleteMenuItem(index) {
    if (!confirm("Are you sure you want to delete this sub menu item?")) {
      return;
    }
    window.location.href = '${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&menuItemId=' + index;
  }

  var menuItems = dragula([
    <c:forEach items="${menuTabList}" var="menuTab" varStatus="status">
    document.querySelector('#site-map-submenu-tab-container-${menuTab.id}')<c:if test="${!status.last}">, </c:if>
    </c:forEach>
  ], {
    moves: function (el, container, handle) {
      return handle.classList.contains('site-map-submenu-tab-drag-handle');
    }
  });

  function checkSiteMapOrder() {
    // Check the main tabs
    var menuTabContainer = document.getElementById("site-map-container");
    var menuTabList = menuTabContainer.querySelectorAll(".site-map-menu-tab");
    var menuTabOrder = "";
    var menuItemOrder = "";
    for (var i = 0; i < menuTabList.length; i++) {
      var menuTab = menuTabList[i];
      if (i > 0) {
        menuTabOrder += ",";
      }
      menuTabOrder += menuTab.id;
      // look for menuItems...
      var menuItemList = menuTab.querySelectorAll(".site-map-submenu-tab");
      for (var j = 0; j < menuItemList.length; j++) {
        var menuItem = menuItemList[j];
        if (menuItemOrder.length > 0) {
          menuItemOrder += "|";
        }
        menuItemOrder += (menuTab.id + "," + menuItem.id);
      }
    }
    var menuTabOrderField = document.getElementById("menuTabOrder");
    menuTabOrderField.value = menuTabOrder;

    var menuItemOrderField = document.getElementById("menuItemOrder");
    menuItemOrderField.value = menuItemOrder;

    return true;
  }
</script>
