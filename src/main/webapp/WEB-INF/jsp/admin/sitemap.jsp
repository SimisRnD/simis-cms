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
<link rel="stylesheet" href="${ctx}/css/platform-sitemap-editor.css?v=<%= VERSION %>" />
<link rel="stylesheet" href="${ctx}/javascript/dragula-3.7.3/dragula.min.css"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<p class="help-text page-help">
  This page designs the drop-down navigation menu shown across the very top of every page on your website --
  not an XML sitemap and not a visual map of your pages. Adding or removing a tab, and reordering tabs or items,
  only takes effect once you click <strong>Save Site Map Changes</strong> below -- nothing is live, and nothing is
  lost if you leave the page, until then. Adding a submenu item under an existing tab is the one exception: its own
  <strong>Add Item</strong> button saves immediately, the same as it always has.
</p>
<div class="callout radius page-help-figure">
  <p style="margin-bottom:8px;"><strong>Example: what a tab and its items look like to a visitor</strong></p>
  <div style="border:1px solid #ccc;border-radius:4px;overflow:hidden;font-size:0.9rem;">
    <div style="background:#2c2c2c;color:#fff;padding:8px 14px;display:flex;gap:20px;">
      <span>Home</span>
      <span style="border-bottom:2px solid #fff;padding-bottom:2px;">Solutions &#9662;</span>
      <span>Contact Us</span>
    </div>
    <div style="background:#fff;padding:8px 14px;">
      <div style="padding:2px 0;">Government Services</div>
      <div style="padding:2px 0;">Commercial Services</div>
    </div>
  </div>
  <p class="help-text" style="margin-top:8px;margin-bottom:0;">
    "Solutions" is a <strong>tab</strong> (Name: Solutions, Link: /solutions) sitting in the bar across the top
    of the site. "Government Services" and "Commercial Services" are <strong>items</strong> -- they only appear
    in the drop-down underneath "Solutions" when a visitor opens it. A tab with no items just links straight to
    its own page instead of opening a drop-down at all (that's what "Home" and "Contact Us" are above).
  </p>
</div>
<p class="help-text page-help">
  Add a tab above, drag the <i class="fa fa-arrows-h"></i>/<i class="fa fa-arrows"></i> handles to reorder tabs
  and items, or click <i class="fa fa-circle-xmark"></i> to remove a tab or item -- click
  <strong>Save Site Map Changes</strong> below when you're happy with the result, or <strong>Cancel</strong> to
  leave without saving any of it. This page can add and delete tabs/items but not change an existing one's link --
  to rename an existing tab/item or change where it links, use
  <a href="${ctx}/admin/sitemap-editor">Navigation Menu Editor - Edit Links</a> instead.
</p>
<p class="help-text page-help">
  The first tab shown below (usually "Home") has no delete icon, no drag handle, and no "Add Item" box --
  that's not a display bug. Whichever tab's link is exactly <code>/</code> is automatically pinned back to the
  very first position every time you save a reorder here or on the Edit Links page, so it can never actually be
  moved, and this page always renders it as locked to match. If your site has no tab linking to exactly
  <code>/</code>, whichever tab happens to sort first gets this same locked treatment instead.
</p>
<%@include file="../page_messages.jspf" %>
<div class="grid-x grid-margin-x">
  <div class="small-12 medium-5 cell">
    <div class="input-group">
      <input class="input-group-field" type="text" id="siteMapNewTabName" placeholder="New tab name" title="The name shown in the menu, e.g. Solutions" value="">
      <input class="input-group-field" type="text" id="siteMapNewTabLink" placeholder="Optional /link" title="Page path starting with /, e.g. /solutions. Leave blank if this tab should only open a submenu." value="">
      <input class="input-group-field" type="text" id="siteMapNewTabIcon" placeholder="Optional icon" title="Icon name from the site's icon set, without the fa- prefix, e.g. briefcase" value="">
      <div class="input-group-button">
        <input type="button" id="siteMapAddTabButton" class="button success" value="Add Tab">
      </div>
    </div>
    <p class="help-text">Name is required. Link must start with / (e.g. /solutions) and is optional. Icon is an
      optional icon name -- do not include the fa- prefix. Added here, this tab isn't saved until you click
      Save Site Map Changes below.</p>
  </div>
</div>
<c:if test="${empty menuTabList}">
  <p class="subheader">No tabs were found, add one!</p>
</c:if>
<form method="post" id="siteMapOrderForm">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="method" value="sitemap-editor"/>
  <input type="hidden" id="menuTabOrder" name="menuTabOrder" value=""/>
  <input type="hidden" id="menuItemOrder" name="menuItemOrder" value=""/>
  <%-- Populated by checkSiteMapOrder() from the client-side staged-add-tab/staged-delete state below --%>
  <input type="hidden" id="newMenuTabIds" name="newMenuTabIds" value=""/>
  <input type="hidden" id="menuTabsToDelete" name="menuTabsToDelete" value=""/>
  <input type="hidden" id="menuItemsToDelete" name="menuItemsToDelete" value=""/>
  <div id="site-map-container" class="site-map-container">
    <c:forEach items="${menuTabList}" var="menuTab" varStatus="status">
      <div id="site-map-menu-tab-container-${status.first ? 0 : menuTab.id}" class="site-map-menu-tab">
        <div>
          <div style="position: absolute;right: 5px;top: 0;">
            <small>
              <c:if test="${!status.first}"><a href="#" class="site-map-delete-tab" data-menu-tab-id="${menuTab.id}" data-menu-tab-name="<c:out value="${menuTab.name}" />" data-menu-item-count="${fn:length(menuTab.menuItemList)}" title="Delete this tab"><i class="fa fa-circle-xmark"></i></a></c:if>
            </small>
          </div>
          <div class="float-left">
            <small class="subheader">
              <c:if test="${!status.first}"><i class="fa fa-arrows-h site-map-menu-tab-drag-handle"></i></c:if>
              <a href="${ctx}${menuTab.link}"><c:out value="${menuTab.link}"/></a><c:if test="${menuTab.link eq '/'}"> (Homepage)</c:if>
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
              <strong><c:out value="${menuTab.name}"/></strong>
              <%--<input type="text" name="menuTab${menuTab.id}name" value="<c:out value="${menuTab.name}" />"/>--%>
            </c:otherwise>
          </c:choose>
        </div>
        <c:if test="${!status.first}">
          <div id="site-map-submenu-tab-container-${menuTab.id}" class="site-map-submenu-container">
            <c:forEach items="${menuTab.menuItemList}" var="menuItem">
              <div id="site-map-menu-item-${menuItem.id}" class="site-map-submenu-tab">
                <div style="position: absolute;right: 5px;top: 0;">
                  <small>
                    <a href="#" class="site-map-delete-item" data-menu-item-id="${menuItem.id}" data-menu-item-name="<c:out value="${menuItem.name}" />" title="Delete this item"><i class="fa fa-circle-xmark"></i></a>
                  </small>
                </div>
                <div class="float-left">
                  <small class="subheader">
                    <i class="fa fa-arrows site-map-submenu-tab-drag-handle"></i>
                    <a href="${ctx}${menuItem.link}"><c:out value="${menuItem.link}" /></a>
                  </small>
                </div>
                <div class="clear-float"></div>
                <div>
                  <c:out value="${menuItem.name}" />
                </div>
              </div>
            </c:forEach>
          </div>
          <input class="input-group-field" type="text" name="menuTab${menuTab.id}menuItemName" placeholder="New item..." title="Adds a new submenu item under ${fn:escapeXml(menuTab.name)}" value="">
          <input class="input-group-field" type="text" name="menuTab${menuTab.id}menuItemLink" placeholder="Optional /link" title="Page path starting with /, e.g. /government-services" value="">
          <div class="button-container">
            <input type="submit" class="button tiny expanded success" value="Add Item">
          </div>
          <p class="help-text">Adds a submenu item under <strong><c:out value="${menuTab.name}"/></strong>. Link must start with /.</p>
        </c:if>
      </div>
    </c:forEach>
  </div>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save Site Map Changes"/>
    <a href="${ctx}/admin" class="button radius secondary">Cancel</a>
  </div>
</form>
<h5 style="margin-top: var(--sc-space-6);">Common problems and how to fix them</h5>
<ul>
  <li><strong>Deleting a tab also deletes every submenu item under it.</strong> The confirmation prompt tells you
    how many items will go with it, but there's no way to keep the items and only remove the tab -- move anything
    you want to keep to a different tab first.</li>
  <li><strong>A visitor sees a tab that opens to nothing, or a submenu item that 404s.</strong> Neither this page
    nor Edit Links checks that a Link actually points to a real page, and nothing here checks for duplicate
    links either -- two tabs (or two items) can point at the same page, or at a page that doesn't exist, with no
    warning.</li>
  <li><strong>There's no way to temporarily hide a tab or item without deleting it.</strong> Once you save, a tab
    or item is visible to every visitor; there's no draft or disabled state exposed on this page.</li>
  <li><strong>Known issue, fixed in progress:</strong> the server-side check that's supposed to block deleting
    the Home tab even if someone bypasses this page's UI (e.g. by directly hitting the delete action with the
    Home tab's id) doesn't currently fire. This page's own UI never exposes a way to do that -- the delete icon
    is never shown for the locked first tab -- so this only matters if something else constructs that request
    directly.</li>
  <li><strong>Known issue, fixed in progress:</strong> clicking "Save Site Map Changes" currently attempts (and
    fails) a wasted update for every existing tab's Name, since this page never actually lets you edit an
    existing tab's name here -- that has no visible effect (the failed attempt is silently rejected before your
    real name is touched), but it does mean an error gets logged on the server for every tab on every save.</li>
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

  // Deleting a tab or item used to POST immediately (an "x" click hit the server on the spot),
  // bypassing Save/Cancel entirely -- the deletion was permanent before Save was ever clicked, and
  // Cancel could not undo it. Both now just remove the row from the page and record its id here;
  // nothing reaches the server until the whole form is submitted (checkSiteMapOrder() below writes
  // these into hidden fields on submit), and SiteMapWidget applies them there.
  var pendingTabDeletes = [];
  var pendingItemDeletes = [];
  var stagedNewTabIds = [];
  var newTabCounter = 0;

  function wireDeleteTabLink(link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      var tabId = link.getAttribute('data-menu-tab-id');
      var isNew = link.getAttribute('data-menu-tab-new') === 'true';
      if (!isNew) {
        var tabName = link.getAttribute('data-menu-tab-name');
        var itemCount = parseInt(link.getAttribute('data-menu-item-count'), 10) || 0;
        var itemsPhrase = itemCount > 0 ? (" and its " + itemCount + " submenu item" + (itemCount === 1 ? "" : "s")) : "";
        if (!confirm("Remove the menu tab \"" + tabName + "\"" + itemsPhrase + "? This will be permanent once you save.")) {
          return;
        }
        pendingTabDeletes.push(tabId);
      }
      var tabContainer = document.getElementById('site-map-menu-tab-container-' + tabId);
      if (tabContainer) {
        tabContainer.parentNode.removeChild(tabContainer);
      }
    });
  }

  function wireDeleteItemLink(link) {
    link.addEventListener('click', function (event) {
      event.preventDefault();
      var itemId = link.getAttribute('data-menu-item-id');
      var itemName = link.getAttribute('data-menu-item-name');
      if (!confirm("Remove the menu item \"" + itemName + "\"? This will be permanent once you save.")) {
        return;
      }
      pendingItemDeletes.push(itemId);
      var itemRow = document.getElementById('site-map-menu-item-' + itemId);
      if (itemRow) {
        itemRow.parentNode.removeChild(itemRow);
      }
    });
  }

  // Renders a tab exactly like the server-rendered ones above, but unsaved: name/link/icon are set
  // via textContent/value (never innerHTML) since they're admin-typed text, not markup.
  function buildNewMenuTabBlock(tempId, name, link, icon) {
    var container = document.createElement('div');
    container.id = 'site-map-menu-tab-container-' + tempId;
    container.className = 'site-map-menu-tab';
    container.innerHTML =
      '<div>' +
        '<div style="position: absolute;right: 5px;top: 0;">' +
          '<small><a href="#" class="site-map-delete-tab" title="Delete this tab"><i class="fa fa-circle-xmark"></i></a></small>' +
        '</div>' +
        '<div class="float-left">' +
          '<small class="subheader">' +
            '<i class="fa fa-arrows-h site-map-menu-tab-drag-handle"></i> ' +
            '<span class="site-map-new-tab-link"></span>' +
          '</small>' +
        '</div>' +
      '</div>' +
      '<div class="clear-float"></div>' +
      '<div><strong class="site-map-new-tab-name"></strong></div>' +
      '<div id="site-map-submenu-tab-container-' + tempId + '" class="site-map-submenu-container"></div>';

    var deleteLink = container.querySelector('.site-map-delete-tab');
    deleteLink.setAttribute('data-menu-tab-id', tempId);
    deleteLink.setAttribute('data-menu-tab-name', name);
    deleteLink.setAttribute('data-menu-item-count', '0');
    deleteLink.setAttribute('data-menu-tab-new', 'true');
    container.querySelector('.site-map-new-tab-link').textContent = link;
    container.querySelector('.site-map-new-tab-name').textContent = name;

    ['name', 'link', 'icon'].forEach(function (field) {
      var value = field === 'name' ? name : (field === 'link' ? link : icon);
      var hidden = document.createElement('input');
      hidden.type = 'hidden';
      hidden.name = 'menuTab' + tempId + field;
      hidden.value = value;
      container.appendChild(hidden);
    });

    // Lets an item be staged for this brand-new tab too, submitted together with it -- the "Add
    // Item" button is unchanged from the server-rendered version (still saves immediately, on the
    // whole form, exactly as it always has for an existing tab).
    var itemsMarkup = document.createElement('div');
    itemsMarkup.innerHTML =
      '<input class="input-group-field" type="text" name="menuTab' + tempId + 'menuItemName" placeholder="New item..." title="Adds a new submenu item under this tab" value="">' +
      '<input class="input-group-field" type="text" name="menuTab' + tempId + 'menuItemLink" placeholder="Optional /link" title="Page path starting with /, e.g. /government-services" value="">' +
      '<div class="button-container"><input type="submit" class="button tiny expanded success" value="Add Item"></div>' +
      '<p class="help-text">Adds a submenu item under <strong class="site-map-new-tab-name-2"></strong>. Link must start with /.</p>';
    itemsMarkup.querySelector('.site-map-new-tab-name-2').textContent = name;
    while (itemsMarkup.firstChild) {
      container.appendChild(itemsMarkup.firstChild);
    }

    return container;
  }

  function stageNewTab() {
    var nameInput = document.getElementById('siteMapNewTabName');
    var linkInput = document.getElementById('siteMapNewTabLink');
    var iconInput = document.getElementById('siteMapNewTabIcon');
    var name = nameInput.value.trim();
    var link = linkInput.value.trim();
    var icon = iconInput.value.trim();
    if (!name) {
      alert('A tab name is required.');
      nameInput.focus();
      return;
    }
    if (name === '/') {
      alert('A valid tab name is required.');
      nameInput.focus();
      return;
    }
    newTabCounter++;
    var tempId = 'new' + newTabCounter;
    var block = buildNewMenuTabBlock(tempId, name, link, icon);
    document.getElementById('site-map-container').appendChild(block);
    // A brand-new tab's (currently empty) item container isn't in dragula's fixed container list
    // below, which is built once from the server-rendered tabs -- push it in so it participates too.
    menuItems.containers.push(document.getElementById('site-map-submenu-tab-container-' + tempId));
    stagedNewTabIds.push(tempId);
    wireDeleteTabLink(block.querySelector('.site-map-delete-tab'));
    nameInput.value = '';
    linkInput.value = '';
    iconInput.value = '';
    nameInput.focus();
  }

  document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.site-map-delete-tab').forEach(wireDeleteTabLink);
    document.querySelectorAll('.site-map-delete-item').forEach(wireDeleteItemLink);
    var addTabButton = document.getElementById('siteMapAddTabButton');
    if (addTabButton) {
      addTabButton.addEventListener('click', stageNewTab);
    }
  });

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

    // Staged tab creates/deletes -- see the addTabButton/wireDeleteTabLink/wireDeleteItemLink
    // handlers above. These are populated fresh from the in-memory arrays on every submit of this
    // form, whether that's the Save button or an individual tab's Add Item button.
    document.getElementById("newMenuTabIds").value = stagedNewTabIds.join(",");
    document.getElementById("menuTabsToDelete").value = pendingTabDeletes.join(",");
    document.getElementById("menuItemsToDelete").value = pendingItemDeletes.join(",");

    return true;
  }

  // issue #1188: this ran from an inline onsubmit attribute, which CSP blocks -- inline handler
  // attributes fall under script-src-attr and the nonce authorises this block, not an attribute
  // calling into it. Because a blocked handler is skipped rather than treated as a cancel, the form
  // still posted, but with menuTabOrder and menuItemOrder left empty. SiteMapWidget.post() guards
  // both updates with StringUtils.isNotBlank(...) and then redirects either way, so a reordered
  // menu saved as a no-op and reported nothing.
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
