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
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="menuTabList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="menuTab" class="com.simisinc.platform.domain.model.cms.MenuTab" scope="request"/>
<jsp:useBean id="webPageList" class="java.util.ArrayList" scope="request"/>
<link rel="stylesheet" href="${ctx}/css/platform-sitemap-editor.css?v=<%= VERSION %>" />
<link rel="stylesheet" href="${ctx}/javascript/dragula-3.7.3/dragula.min.css"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<p class="help-text page-help">
  This page renames existing entries in the drop-down navigation menu across the top of your site, and changes
  the page they link to -- a <strong>tab</strong> sits in the top bar itself (e.g. "Solutions"), an
  <strong>item</strong> only appears in the drop-down underneath a tab once a visitor opens it (see the example
  on the <a href="${ctx}/admin/sitemap">Navigation Menu Editor</a> page if that distinction isn't clear). Link
  must start with / (e.g. /solutions); if you leave off the leading slash it's added for you rather than
  rejected. Existing page paths are suggested as you type, but the field still accepts any value. Reorder
  tabs and items with the <i class="fa fa-arrows-h"></i>/<i class="fa fa-arrows"></i> drag
  handles or the arrow buttons. To add a new tab/item or to delete one, use
  <a href="${ctx}/admin/sitemap">Navigation Menu Editor</a> instead.
</p>
<p class="help-text page-help">
  <strong>Save Site Map Changes saves every visible tab and item at once</strong> -- there's no per-row save, so
  a typo in one field doesn't stop the rest of the page's edits from being saved. As on the Navigation Menu
  Editor page, the first tab (usually "Home") has no editable Name/Link/Icon fields here, for the same reason:
  whichever tab links to exactly <code>/</code> is always forced back to the first position on save, so it's
  never actually reachable through this UI's rename/relink controls.
</p>
<%@include file="../page_messages.jspf" %>
<c:if test="${empty menuTabList}">
  <p class="subheader">No tabs were found, add one!</p>
</c:if>
<datalist id="webPageLinks">
  <c:forEach items="${webPageList}" var="wp">
    <option value="<c:out value="${wp.link}"/>"><c:out value="${wp.title}"/></option>
  </c:forEach>
</datalist>
<form method="post" id="siteMapOrderForm">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="method" value="sitemap-editor"/>
  <input type="hidden" id="menuTabOrder" name="menuTabOrder" value=""/>
  <input type="hidden" id="menuItemOrder" name="menuItemOrder" value=""/>
  <input type="hidden" id="menuSubItemOrder" name="menuSubItemOrder" value=""/>
  <div id="site-map-container" class="site-map-container">
    <c:forEach items="${menuTabList}" var="menuTab" varStatus="status">
      <div id="site-map-menu-tab-container-${status.first ? 0 : menuTab.id}" class="site-map-menu-tab">
        <div>
          <div class="float-left">
            <small class="subheader">
              <c:choose>
                <c:when test="${status.first}">
                  <a href="${ctx}${menuTab.link}"><c:out value="${menuTab.link}"/></a><c:if test="${menuTab.link eq '/'}"> (Homepage)</c:if>
                </c:when>
                <c:otherwise>
                  <i class="fa fa-arrows-h site-map-menu-tab-drag-handle" aria-hidden="true"></i>
                  <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move tab left"
                          data-move="tabLeft" data-move-target="site-map-menu-tab-container-${menuTab.id}">&#9664;</button>
                  <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move tab right"
                          data-move="tabRight" data-move-target="site-map-menu-tab-container-${menuTab.id}">&#9654;</button>
                </c:otherwise>
              </c:choose>
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
              <input type="text" name="menuTab${menuTab.id}name" value="<c:out value="${menuTab.name}" />" title="Tab name shown in the menu" style="margin-bottom:0"/>
              <input type="text" name="menuTab${menuTab.id}link" value="<c:out value="${menuTab.link}" />" placeholder="/link" title="Page path starting with /, e.g. /solutions" style="margin-bottom:0" list="webPageLinks"/>
              <input type="text" name="menuTab${menuTab.id}icon" value="<c:out value="${menuTab.icon}" />" placeholder="Optional icon" title="Icon name without the fa- prefix, e.g. briefcase"/>
            </c:otherwise>
          </c:choose>
        </div>
        <c:if test="${!status.first}">
          <div id="site-map-submenu-tab-container-${menuTab.id}" class="site-map-submenu-container">
            <c:forEach items="${menuTab.menuItemList}" var="menuItem">
              <div id="site-map-menu-item-${menuItem.id}" class="site-map-submenu-tab">
                <%--
                <div style="position: absolute;right: 5px;top: 0;">
                  <small>
                    <a href="#" data-js-call="deleteMenuItem" data-js-arg1="${menuItem.id}"><i class="fa fa-circle-xmark"></i></a>
                  </small>
                </div>
                --%>
                <div class="float-left">
                  <small class="subheader">
                    <i class="fa fa-arrows site-map-submenu-tab-drag-handle" aria-hidden="true"></i>
                    <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move item up"
                            data-move="itemUp" data-move-target="site-map-menu-item-${menuItem.id}">&#9650;</button>
                    <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move item down"
                            data-move="itemDown" data-move-target="site-map-menu-item-${menuItem.id}">&#9660;</button>
                    <%--<a href="${ctx}${menuItem.link}"><c:out value="${menuItem.link}" /></a>--%>
                  </small>
                </div>
                <div class="clear-float"></div>
                <div>
                  <input type="text" name="menuItem${menuItem.id}name" value="<c:out value="${menuItem.name}" />" title="Item name shown in the submenu" style="margin-bottom:0"/>
                  <input type="text" name="menuItem${menuItem.id}link" value="<c:out value="${menuItem.link}" />" placeholder="/link" title="Page path starting with /, e.g. /government-services" style="margin-bottom:0" list="webPageLinks"/>
                </div>
                <%-- Third level (issue #1728). The container is rendered whether or not it has
                     children, because dragula needs a drop target to exist before anything can be
                     dragged into it -- an item with no sub-items yet would otherwise be the one
                     place you could never create one. --%>
                <div id="site-map-subitem-container-${menuItem.id}" class="site-map-subitem-container">
                  <c:forEach items="${menuItem.menuItemList}" var="subMenuItem">
                    <div id="site-map-menu-subitem-${subMenuItem.id}" class="site-map-subitem">
                      <div class="float-left">
                        <small class="subheader">
                          <i class="fa fa-arrows site-map-subitem-drag-handle" aria-hidden="true"></i>
                          <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move sub-item up"
                                  data-move="subItemUp" data-move-target="site-map-menu-subitem-${subMenuItem.id}">&#9650;</button>
                          <button type="button" class="button tiny secondary" style="margin:0 2px" aria-label="Move sub-item down"
                                  data-move="subItemDown" data-move-target="site-map-menu-subitem-${subMenuItem.id}">&#9660;</button>
                        </small>
                      </div>
                      <div class="clear-float"></div>
                      <div>
                        <input type="text" name="menuItem${subMenuItem.id}name" value="<c:out value="${subMenuItem.name}" />" title="Sub-item name shown in the menu" style="margin-bottom:0"/>
                        <input type="text" name="menuItem${subMenuItem.id}link" value="<c:out value="${subMenuItem.link}" />" placeholder="/link" title="Page path starting with /, e.g. /rhtt-robotic-human-type-targets" style="margin-bottom:0" list="webPageLinks"/>
                      </div>
                    </div>
                  </c:forEach>
                </div>
                <%-- Creating the third level (issue #1728). Everything else about a nested item --
                     storing, reordering, reparenting, rendering, searching -- already worked, but
                     nothing could create the first one: the only code that set a parent was the
                     drag-and-drop reorder, which can only move an item that is nested already.
                     Sits outside the drop container above so an empty container stays a clean drop
                     target, and renders only for an item that can legally take children -- the
                     server re-checks that, because the field name is guessable. --%>
                <%-- Mirrors MenuItem.hasParentMenuItem(): "> 0", because the repository stores -1
                     for "no parent" rather than null. Written as a property test, not
                     ${!menuItem.hasParentMenuItem} -- that is a method, not a getter, so EL cannot
                     resolve it and the JSP compiles clean while failing at request time. --%>
                <c:if test="${empty menuItem.parentMenuItemId or menuItem.parentMenuItemId le 0}">
                  <div class="site-map-subitem-add">
                    <input class="input-group-field" type="text" name="menuItem${menuItem.id}subItemName"
                           placeholder="New sub-item..." title="Adds a sub-item under <c:out value="${menuItem.name}"/>" value=""/>
                    <input class="input-group-field" type="text" name="menuItem${menuItem.id}subItemLink"
                           placeholder="Optional /link" title="Page path starting with /, e.g. /usv-fos" value="" list="webPageLinks"/>
                  </div>
                </c:if>
              </div>
            </c:forEach>
          </div>
        </c:if>
      </div>
    </c:forEach>
  </div>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save Site Map Changes"/>
    <a href="${ctx}/admin" class="button radius secondary">Cancel</a>
  </div>
</form>
<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>I changed a tab's Link and now the old page it used to point to has no way to be reached from the
    menu.</strong> Changing a Link here only repoints the menu entry -- it doesn't touch the underlying page,
    move it, or add a redirect. If anything still linked to the old path (bookmarks, search results, other
    pages), set up a redirect separately rather than relying on the menu to keep working.</li>
  <li><strong>I renamed a tab and the change didn't stick.</strong> Every field on this page is saved together
    by the single "Save Site Map Changes" button at the bottom -- editing a field and navigating away without
    clicking it discards that edit along with everything else unsaved on the page.</li>
</ul>
<script src="${ctx}/javascript/dragula-3.7.3/dragula.min.js"></script>
<script nonce="${cspNonce}">
  var menuTabs = dragula([document.getElementById('site-map-container')], {
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
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&menuTabId=' + index);
  }

  function deleteMenuItem(index) {
    if (!confirm("Are you sure you want to delete this sub menu item?")) {
      return;
    }
    postAction('${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&menuItemId=' + index);
  }

  <%--
  function addTabAfter(index) {
    alert(menuTabs.containers.length);
    var original = document.getElementById("site-map-menu-tab-container-0");
    var clone = original.cloneNode(true);
    clone.id = "site-map-menu-tab-container-" + menuTabs.containers.length;

    if ((index + 1) == menuTabs.containers.length) {
      menuTabs.containers.push(clone);
    } else {
      menuTabs.containers.splice(index + 1, 0, clone);
    }

    original.parentNode.appendChild(clone);

  }
  --%>

  var menuItems = dragula([
    <c:forEach items="${menuTabList}" var="menuTab" varStatus="status">
    document.querySelector('#site-map-submenu-tab-container-${menuTab.id}')<c:if test="${!status.last}">, </c:if>
    </c:forEach>
  ], {
    moves: function (el, container, handle) {
      return handle.classList.contains('site-map-submenu-tab-drag-handle');
    }
  });

  // Third level (issue #1728). Collected at runtime rather than emitted per item by the JSP: there
  // is one container per menu item, and querySelectorAll keeps that list correct without a nested
  // loop in the markup. Passing every container to a single dragula instance is what makes an item
  // draggable from one parent to another, exactly as the level-2 instance above does across tabs.
  var menuSubItems = dragula(
    Array.prototype.slice.call(document.querySelectorAll('.site-map-subitem-container')), {
      moves: function (el, container, handle) {
        return handle.classList.contains('site-map-subitem-drag-handle');
      }
    });

  function checkSiteMapOrder() {
    // Check the main tabs
    var menuTabContainer = document.getElementById("site-map-container");
    var menuTabList = menuTabContainer.querySelectorAll(".site-map-menu-tab");
    var menuTabOrder = "";
    var menuItemOrder = "";
    var menuSubItemOrder = "";
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
        // ...and any sub-items nested under this item. Same parentId,childId shape, read from live
        // DOM position, so an item dragged to a different parent reports its new one.
        var subItemContainer = menuItem.querySelector(".site-map-subitem-container");
        if (subItemContainer) {
          var subItemList = subItemContainer.querySelectorAll(".site-map-subitem");
          for (var k = 0; k < subItemList.length; k++) {
            if (menuSubItemOrder.length > 0) {
              menuSubItemOrder += "|";
            }
            menuSubItemOrder += (menuItem.id + "," + subItemList[k].id);
          }
        }
      }
    }
    var menuTabOrderField = document.getElementById("menuTabOrder");
    menuTabOrderField.value = menuTabOrder;

    var menuItemOrderField = document.getElementById("menuItemOrder");
    menuItemOrderField.value = menuItemOrder;

    var menuSubItemOrderField = document.getElementById("menuSubItemOrder");
    menuSubItemOrderField.value = menuSubItemOrder;

    return true;
  }

  function moveTabLeft(id) {
    var el = document.getElementById(id);
    var prev = el.previousElementSibling;
    if (prev && prev.id !== 'site-map-menu-tab-container-0') {
      el.parentNode.insertBefore(el, prev);
    }
  }
  function moveTabRight(id) {
    var el = document.getElementById(id);
    var next = el.nextElementSibling;
    if (next) { el.parentNode.insertBefore(next, el); }
  }
  function moveItemUp(id) {
    var el = document.getElementById(id);
    var prev = el.previousElementSibling;
    if (prev) el.parentNode.insertBefore(el, prev);
  }
  function moveItemDown(id) {
    var el = document.getElementById(id);
    var next = el.nextElementSibling;
    if (next) el.parentNode.insertBefore(next, el);
  }

  // issue #1188: the tab/item arrow buttons carried inline onclick attributes, blocked the same
  // way. They are the keyboard-accessible alternative to the drag handles, so while they were dead
  // the only way to reorder anything on this page was a mouse drag.
  document.addEventListener('DOMContentLoaded', function () {
    var moves = {
      tabLeft: moveTabLeft,
      tabRight: moveTabRight,
      itemUp: moveItemUp,
      itemDown: moveItemDown,
      subItemUp: moveItemUp,
      subItemDown: moveItemDown
    };
    document.querySelectorAll('[data-move]').forEach(function (button) {
      button.addEventListener('click', function () {
        var move = moves[button.getAttribute('data-move')];
        if (move) {
          move(button.getAttribute('data-move-target'));
        }
      });
    });
  });

  // issue #1188: checkSiteMapOrder ran from an inline onsubmit attribute, which CSP blocks --
  // inline handler attributes fall under script-src-attr and the nonce authorises this block, not
  // an attribute calling into it. Because a blocked handler is skipped rather than treated as a
  // cancel, the form still posted, but with menuTabOrder and menuItemOrder left empty.
  // SiteMapEditorWidget.post() guards both updates with StringUtils.isNotBlank(...) and then
  // redirects either way, so a reordered menu saved as a no-op and reported nothing.
  document.addEventListener('DOMContentLoaded', function () {
    var form = document.getElementById('siteMapOrderForm');
    if (form) {
      form.addEventListener('submit', function (event) {
        if (!checkSiteMapOrder()) {
          event.preventDefault();
        }
      });
    }
  });
</script>
