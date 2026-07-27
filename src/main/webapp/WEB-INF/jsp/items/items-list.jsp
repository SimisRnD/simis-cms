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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/tlds/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="category" class="com.simisinc.platform.domain.model.items.Category" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="showCategory" class="java.lang.String" scope="request"/>
<jsp:useBean id="showImage" class="java.lang.String" scope="request"/>
<jsp:useBean id="showIcon" class="java.lang.String" scope="request"/>
<jsp:useBean id="showBullets" class="java.lang.String" scope="request"/>
<jsp:useBean id="showLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="useItemLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="showLaunchLink" class="java.lang.String" scope="request"/>
<jsp:useBean id="launchLabel" class="java.lang.String" scope="request"/>
<jsp:useBean id="isEditMode" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<c:choose>--%>
<%--  <c:when test="${category.id gt 0}"><c:out value="${category.name}" /></c:when>--%>
  <%--<c:otherwise>All <c:out value="${collection.name}" /></c:otherwise>--%>
<%--</c:choose>--%>
<c:choose>
  <c:when test="${!empty itemList}">
    <ul<c:if test="${showBullets eq 'false'}"> class="no-bullet"</c:if> id="itemList">
      <c:forEach items="${itemList}" var="item">
        <li class="item-row" data-item-id="${item.id}" data-item-order="${item.itemOrder}">
          <c:if test="${isEditMode eq 'true'}">
            <span class="drag-handle" title="Drag to reorder" aria-label="Drag handle for ${item.name}">
              <i class="fa fa-grip-vertical"></i>
            </span>
          </c:if>
          <c:choose>
            <c:when test="${showImage eq 'true' && !empty item.imageUrl}">
              <div class="item-image">
                <img alt="item image" src="<c:out value="${item.imageUrl}"/>" />
              </div>
            </c:when>
            <c:when test="${showIcon eq 'true' && !empty collection.icon}">
              <i class="${font:fad()} fa-<c:out value="${collection.icon}" />"></i>
            </c:when>
          </c:choose>
          <c:choose>
            <c:when test="${showLink eq 'false'}">
              <c:out value="${item.name}" />
            </c:when>
            <c:when test="${useItemLink eq 'true' && (fn:startsWith(item.url, 'http://') || fn:startsWith(item.url, 'https://'))}">
              <a target="_blank" href="${item.url}"><c:out value="${item.name}"/></a>
            </c:when>
            <c:otherwise>
              <a href="${ctx}/show/${item.uniqueId}"><c:out value="${item.name}" /></a>
            </c:otherwise>
          </c:choose>
          <c:if test="${!empty item.city}"><small class="subheader"><c:out value="${item.city}" /></small></c:if>
          <c:if test="${empty item.approved}"><span class="label warning">Needs approval</span></c:if>
          <c:if test="${showCategory eq 'true' && item.categoryId gt 0}">
            <span class="label tiny margin-0" style="${category:headerColorCSS(item.categoryId)}; padding:0.15rem .2rem"><c:out value="${category:name(item.categoryId)}" /></span>
          </c:if>
          <c:if test="${showLaunchLink eq 'true' && !empty item.url}">
            <c:if test="${fn:startsWith(item.url, 'http://') || fn:startsWith(item.url, 'https://')}">
              <a href="${url:encode(item.url)}" class="button primary tiny margin-0" style="padding: 0.2rem .2rem;" target="_blank" rel="nofollow" title="Visit <c:out value="${text:trim(item.url, 30, true)}"/>"><c:out value="${launchLabel}"/> <i class="fa fa-external-link"></i></i></a>
            </c:if>
          </c:if>
          <c:if test="${showUrl eq 'true' && !empty item.url}">
            <p><c:out value="${item.url}" /></p>
          </c:if>
          <c:if test="${isEditMode eq 'true'}">
            <button class="button alert tiny deactivate-btn" data-item-id="${item.id}" title="Deactivate this item">
              <i class="fa fa-ban"></i> Deactivate
            </button>
          </c:if>
        </li>
      </c:forEach>
    </ul>
    <c:if test="${isEditMode eq 'true'}">
      <div class="add-item-section">
        <h5>Add New Item</h5>
        <form id="addItemForm" class="add-item-form">
          <fieldset>
            <div class="row">
              <div class="small-12 medium-6 columns">
                <label for="itemName">Item Name *</label>
                <input type="text" id="itemName" name="itemName" placeholder="Item name" required>
              </div>
              <div class="small-12 medium-6 columns">
                <label for="itemSummary">Item Summary</label>
                <input type="text" id="itemSummary" name="itemSummary" placeholder="Brief summary">
              </div>
            </div>
            <button type="submit" class="button success"><i class="fa fa-plus"></i> Add Item</button>
          </fieldset>
        </form>
      </div>
    </c:if>
    <%-- Paging Control --%>
    <c:if test="${category.id gt 0}">
      <c:set var="recordPagingParams" scope="request" value="categoryId=${category.id}"/>
    </c:if>
    <%@include file="../paging_control.jspf" %>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No <c:out value="${fn:toLowerCase(collection.name)}"/> were found
    </p>
  </c:otherwise>
</c:choose>

<c:if test="${isEditMode eq 'true'}">
<style>
  .item-row {
    position: relative;
    padding-left: 2.5rem;
    padding-right: 6rem;
    margin-bottom: 0.5rem;
  }
  .drag-handle {
    position: absolute;
    left: 0;
    top: 0.5rem;
    cursor: grab;
    color: #999;
    font-size: 1rem;
    padding: 0.25rem 0.5rem;
  }
  .drag-handle:active {
    cursor: grabbing;
  }
  .drag-handle:hover {
    color: #666;
  }
  .item-row.drag-over {
    opacity: 0.5;
  }
  .deactivate-btn {
    position: absolute;
    right: 0;
    top: 0.5rem;
    font-size: 0.75rem;
    padding: 0.25rem 0.5rem;
  }
  .add-item-section {
    margin-top: 2rem;
    padding: 1rem;
    background-color: #f5f5f5;
    border-radius: 0.25rem;
  }
  .add-item-section h5 {
    margin-top: 0;
  }
  .add-item-form fieldset {
    margin-bottom: 0;
  }
</style>

<script>
(function() {
  var collectionId = '${collection.id}';
  var itemList = document.getElementById('itemList');
  var formToken = (typeof mainToken !== 'undefined') ? mainToken : '';

  if (!itemList) return;

  // Initialize Sortable for drag-to-reorder
  if (typeof Sortable !== 'undefined') {
    Sortable.create(itemList, {
      handle: '.drag-handle',
      animation: 150,
      ghostClass: 'drag-over',
      onEnd: function(evt) {
        var itemRow = evt.item;
        var itemId = itemRow.getAttribute('data-item-id');
        var newOrder = Array.from(itemList.children).indexOf(itemRow) + 1;

        // Send reorder mutation to server (form-encoded)
        var params = new URLSearchParams();
        params.append('action', 'reorderCollectionItem');
        params.append('itemId', itemId);
        params.append('newOrder', newOrder);
        params.append('token', formToken);

        fetch(window.location.pathname, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          body: params.toString()
        }).then(r => r.json()).then(function(data) {
          if (data.success !== true) {
            console.error('Reorder failed:', data.message);
            location.reload();
          }
        }).catch(function(err) {
          console.error('Reorder error:', err);
          location.reload();
        });
      }
    });
  }

  // Handle deactivate buttons
  document.querySelectorAll('.deactivate-btn').forEach(function(btn) {
    btn.addEventListener('click', function(e) {
      e.preventDefault();
      if (!confirm('Deactivate this item? It will no longer appear in collections.')) {
        return;
      }

      var itemId = btn.getAttribute('data-item-id');
      var params = new URLSearchParams();
      params.append('action', 'deactivateCollectionItem');
      params.append('itemId', itemId);
      params.append('token', formToken);

      fetch(window.location.pathname, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params.toString()
      }).then(r => r.json()).then(function(data) {
        if (data.success === true) {
          btn.closest('.item-row').style.opacity = '0.5';
          btn.disabled = true;
        } else {
          alert('Failed to deactivate: ' + (data.message || 'Unknown error'));
        }
      }).catch(function(err) {
        alert('Error: ' + err.message);
      });
    });
  });

  // Handle add item form
  var addItemForm = document.getElementById('addItemForm');
  if (addItemForm) {
    addItemForm.addEventListener('submit', function(e) {
      e.preventDefault();

      var itemName = document.getElementById('itemName').value.trim();
      var itemSummary = document.getElementById('itemSummary').value.trim();

      if (!itemName) {
        alert('Item name is required');
        return;
      }

      var params = new URLSearchParams();
      params.append('action', 'saveCollectionItem');
      params.append('collectionId', collectionId);
      params.append('itemName', itemName);
      params.append('itemSummary', itemSummary);
      params.append('token', formToken);

      fetch(window.location.pathname, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded'
        },
        body: params.toString()
      }).then(r => r.json()).then(function(data) {
        if (data.success === true) {
          location.reload();
        } else {
          alert('Failed to add item: ' + (data.message || 'Unknown error'));
        }
      }).catch(function(err) {
        alert('Error: ' + err.message);
      });
    });
  }
})();
</script>
</c:if>
