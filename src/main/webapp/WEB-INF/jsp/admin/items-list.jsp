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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="collection" uri="/WEB-INF/tlds/collection-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/tlds/category-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="columns" class="java.lang.String" scope="request"/>
<style>
  .admin-item-list .item-image, .admin-item-list .item-icon {
      float: left;
  }
  .admin-item-list a {
      float: left;
      margin-top: 8px;
      display: -webkit-box;
      -webkit-line-clamp: 1;
      -webkit-box-orient: vertical;
      overflow: hidden;
      text-decoration: none;
      word-break: break-word;
      position: static!important;
      max-width: 85%;
  }
</style>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<%-- The bulk-actions toolbar, filter, edit link, and status column only apply to the full admin
     list (columns=all, used by /admin/collection-records); the compact "columns=name" preview
     rendered on /admin/collection-details is a read-only sidebar summary and shouldn't gain
     checkboxes for records it isn't the primary place to manage (see web-page-list.jsp's
     "don't duplicate selectable rows across two renderings of the same records" precedent). --%>
<c:if test="${columns eq 'all'}">
<form method="get" autocomplete="off" class="margin-bottom-10">
  <input type="hidden" name="collectionId" value="${collection.id}"/>
  <label class="inline">
    <input type="checkbox" name="includeArchived" value="true" onchange="this.form.submit()" <c:if test="${includeArchived}">checked</c:if>>
    Include archived items
  </label>
</form>
<div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
  <span id="bulkSelectedCount"></span>
  <button type="button" class="button tiny radius" id="bulkPublishBtn">Publish</button>
  <button type="button" class="button tiny radius" id="bulkUnpublishBtn">Unpublish</button>
  <button type="button" class="button tiny radius" id="bulkArchiveBtn">Archive</button>
  <button type="button" class="button tiny radius" id="bulkMoveBtn">Move</button>
  <button type="button" class="button tiny alert radius" id="bulkDeleteBtn">Delete</button>
</div>
</c:if>
<table class="unstriped admin-item-list">
  <thead>
  <tr>
    <c:if test="${columns eq 'all'}">
      <th width="24"><input type="checkbox" id="selectAllItems" aria-label="Select all items on this page"></th>
    </c:if>
    <th>Name</th>
    <c:if test="${columns eq 'all'}">
      <th>Street</th>
      <th>City</th>
      <th>State</th>
      <th>Postal</th>
      <th>Country</th>
      <th>Geocode</th>
      <th width="100" class="text-center">Status</th>
      <th width="60" class="text-center">Action</th>
    </c:if>
  </tr>
  </thead>
  <tbody>
  <c:forEach items="${itemList}" var="item" varStatus="status">
    <c:set var="category" scope="request" value="${categoryMap.get(item.categoryId)}"/>
    <tr>
      <c:if test="${columns eq 'all'}">
        <td><input type="checkbox" class="itemRowCheckbox" value="${item.id}" data-name="${fn:escapeXml(item.name)}" aria-label="Select ${fn:escapeXml(item.name)}"></td>
      </c:if>
      <td>
        <c:choose>
          <c:when test="${!empty item.imageUrl}">
            <c:set var="itemImageSrcset" value="${image:srcsetBatch(item.imageUrl, imageVariantsByImageId, imageWidthsByImageId)}"/>
            <div class="item-image">
              <img alt="item image" src="<c:out value="${item.imageUrl}"/>"
                <c:if test="${not empty itemImageSrcset}"> srcset="<c:out value="${itemImageSrcset}"/>" sizes="150px"</c:if>
                decoding="async"<c:if test="${!status.first}"> loading="lazy"</c:if> />
            </div>
          </c:when>
          <c:when test="${!empty category.headerBgColor && !empty category.headerTextColor}">
            <c:choose>
              <c:when test="${!empty category.icon}">
                <span class="item-icon padding-10 padding-width-10 margin-right-10" style="background-color:<c:out value="${category.headerBgColor}" />;color:<c:out value="${category.headerTextColor}" />">
                  <i class="${font:far()} fa-fw fa-<c:out value="${category.icon}" />"></i>
                </span>
              </c:when>
              <c:otherwise>
              <span class="item-icon padding-10 padding-width-10 margin-right-10" style="background-color:<c:out value="${category.headerBgColor}" />;color:<c:out value="${category.headerTextColor}" />">
                <i class="${font:far()} fa-fw"></i>
              </span>
              </c:otherwise>
            </c:choose>
          </c:when>
          <c:when test="${!empty collection.icon}">
            <i class="item-icon ${font:fad()} fa-<c:out value="${collection.icon}" />"></i>
          </c:when>
        </c:choose>
        <a href="${ctx}/show/${item.uniqueId}" translate="no"><c:out value="${item.name}" /></a>
      </td>
      <c:if test="${columns eq 'all'}">
        <td><c:out value="${item.street}" /></td>
        <td><c:out value="${item.city}" /></td>
        <td><c:out value="${item.state}" /></td>
        <td><c:out value="${item.postalCode}" /></td>
        <td><c:out value="${item.country}" /></td>
        <td>
          <c:if test="${item.geocoded}">
            <c:out value="${item.latitude}" />, <c:out value="${item.longitude}" />
          </c:if>
        </td>
        <td class="text-center">
          <c:choose>
            <c:when test="${!empty item.archived}">
              <span class="label secondary radius">Archived</span>
            </c:when>
            <c:when test="${!empty item.approved}">
              <span class="label success radius">Published</span>
            </c:when>
            <c:otherwise>
              <span class="label radius">Draft</span>
            </c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <a href="${ctx}/edit/${item.uniqueId}?returnPage=/admin/collection-records%3FcollectionId%3D${collection.id}"><i class="fa fa-edit"></i></a>
        </td>
      </c:if>
    </tr>
  </c:forEach>
  </tbody>
</table>
<c:if test="${empty itemList}">
  No records were found
</c:if>
<%-- Paging Control --%>
<%@include file="../paging_control.jspf" %>
<c:if test="${columns eq 'all'}">
<%-- Bulk action reveal modals -- selection is scoped to the items currently checked on this page
     (see the JS below); each is populated at open time with the live selection, not just a count.
     Mirrors calendar-event-list.jsp/blog-post-list.jsp's bulk reveal modals (issue #427 pattern). --%>
<div class="reveal" id="bulkPublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkPublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkPublishRevealTitle">Publish <span id="bulkPublishCount">0</span> Item(s)</h4>
  <p class="help-text">Publishing approves the selected items so they're marked Published.</p>
  <ul id="bulkPublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkPublish"/>
    <input type="hidden" name="collectionId" value="${collection.id}"/>
    <input type="submit" class="button radius" value="Publish Items"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkUnpublishReveal" role="dialog" aria-modal="true" aria-labelledby="bulkUnpublishRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkUnpublishRevealTitle">Unpublish <span id="bulkUnpublishCount">0</span> Item(s)</h4>
  <p class="help-text">Unpublishing removes approval; the selected items revert to Draft.</p>
  <ul id="bulkUnpublishList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkUnpublish"/>
    <input type="hidden" name="collectionId" value="${collection.id}"/>
    <input type="submit" class="button radius" value="Unpublish Items"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkArchiveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkArchiveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkArchiveRevealTitle">Archive <span id="bulkArchiveCount">0</span> Item(s)</h4>
  <p class="help-text">Archived items are hidden from this list and the public site by default. Check "Include archived items" above to find them again.</p>
  <ul id="bulkArchiveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkArchive"/>
    <input type="hidden" name="collectionId" value="${collection.id}"/>
    <input type="submit" class="button radius" value="Archive Items"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkMoveReveal" role="dialog" aria-modal="true" aria-labelledby="bulkMoveRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkMoveRevealTitle">Move <span id="bulkMoveCount">0</span> Item(s)</h4>
  <p class="help-text">Moves the selected items to a different category within this collection, replacing their current category.</p>
  <ul id="bulkMoveList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkMove"/>
    <input type="hidden" name="collectionId" value="${collection.id}"/>
    <label for="bulkMoveCategoryId">Destination category <span class="required">*</span>
      <select id="bulkMoveCategoryId" name="categoryId" required>
        <c:forEach items="${categoryList}" var="categoryOption">
          <option value="${categoryOption.id}"><c:out value="${categoryOption.name}" /></option>
        </c:forEach>
      </select>
    </label>
    <input type="submit" class="button radius" value="Move Items"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkDeleteReveal" role="dialog" aria-modal="true" aria-labelledby="bulkDeleteRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkDeleteRevealTitle">Delete <span id="bulkDeleteCount">0</span> Item(s)</h4>
  <p class="help-text">This permanently removes the selected items. This cannot be undone.</p>
  <ul id="bulkDeleteList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkDelete"/>
    <input type="hidden" name="collectionId" value="${collection.id}"/>
    <input type="submit" class="button alert radius" value="Delete Items"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    var $selectAll = $('#selectAllItems');
    var $rows = $('.itemRowCheckbox');
    var $bar = $('#bulkActionsBar');
    var $count = $('#bulkSelectedCount');

    function selected() {
      return $rows.filter(':checked');
    }

    function refresh() {
      var n = selected().length;
      $count.text(n + (n === 1 ? ' item selected  ' : ' items selected  '));
      $bar.toggle(n > 0);
      $selectAll.prop('indeterminate', n > 0 && n < $rows.length);
      $selectAll.prop('checked', n > 0 && n === $rows.length);
    }

    // Populates one bulk modal's hidden itemId fields and visible name list from the currently
    // checked rows, so the admin sees exactly what is about to be affected before confirming.
    function populateBulkModal(revealId, listId, countId) {
      var $reveal = $('#' + revealId);
      var $form = $reveal.find('form');
      var $list = $('#' + listId);
      $form.find('input[name="itemId"]').remove();
      $list.empty();
      selected().each(function () {
        var $checkbox = $(this);
        $form.append($('<input type="hidden" name="itemId">').val($checkbox.val()));
        $list.append($('<li>').text($checkbox.data('name')));
      });
      $('#' + countId).text(selected().length);
      $reveal.foundation('open');
    }

    $selectAll.on('change', function () {
      $rows.prop('checked', this.checked);
      refresh();
    });
    $rows.on('change', refresh);

    $('#bulkPublishBtn').on('click', function () { populateBulkModal('bulkPublishReveal', 'bulkPublishList', 'bulkPublishCount'); });
    $('#bulkUnpublishBtn').on('click', function () { populateBulkModal('bulkUnpublishReveal', 'bulkUnpublishList', 'bulkUnpublishCount'); });
    $('#bulkArchiveBtn').on('click', function () { populateBulkModal('bulkArchiveReveal', 'bulkArchiveList', 'bulkArchiveCount'); });
    $('#bulkMoveBtn').on('click', function () { populateBulkModal('bulkMoveReveal', 'bulkMoveList', 'bulkMoveCount'); });
    $('#bulkDeleteBtn').on('click', function () { populateBulkModal('bulkDeleteReveal', 'bulkDeleteList', 'bulkDeleteCount'); });

    refresh();
  })();
</script>
</c:if>
