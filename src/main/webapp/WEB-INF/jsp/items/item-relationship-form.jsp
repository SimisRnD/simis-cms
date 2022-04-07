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
<%@ taglib prefix="collection" uri="/WEB-INF/collection-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="collectionRelationshipList" class="java.util.ArrayList" scope="request"/>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form specific --%>
  <input type="hidden" name="itemId" value="${item.id}"/>
  <input type="hidden" name="itemUniqueId" value="${item.uniqueId}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <select id="relatedCollectionId" name="relatedCollectionId">
    <option value="">Choose a category...</option>
    <c:forEach items="${collectionRelationshipList}" var="collectionRelationship" varStatus="status">
      <c:choose>
        <c:when test="${collectionRelationship.collectionId == collectionRelationship.relatedCollectionId}">
          <option value="${collectionRelationship.collectionId}"<c:if test="${collectionRelationshipList.size() == 1}"> selected</c:if>>
            <c:out value="${collection:name(collectionRelationship.collectionId)}"/>
          </option>
        </c:when>
        <c:when test="${collectionRelationship.relatedCollectionId == item.collectionId}">
          <option value="${collectionRelationship.collectionId}"<c:if test="${collectionRelationshipList.size() == 1}"> selected</c:if>>
            <c:out value="${collection:name(collectionRelationship.collectionId)}"/>
          </option>
        </c:when>
        <c:otherwise>
          <option value="${collectionRelationship.relatedCollectionId}"<c:if test="${collectionRelationshipList.size() == 1}"> selected</c:if>>
            <c:out value="${collection:name(collectionRelationship.relatedCollectionId)}"/>
          </option>
        </c:otherwise>
      </c:choose>
    </c:forEach>
  </select>
  <input type="text" placeholder="Enter a name" id="relatedItemName" name="relatedItemName" value="" autocomplete="off">
  <%--<input type="checkbox" name="reciprocal" /> Reciprocal--%>
  <input type="hidden" id="relatedItemUniqueId" name="relatedItemUniqueId" value=""/>
  <p><input type="submit" class="button radius success expanded" value="Save"/></p>
</form>
<script>
  function checkForm${widgetContext.uniqueId}() {
    // Validate the stuff before submitting...
    if (document.getElementById("relatedCollectionId").value.trim() == "") {
      alert("Make sure to choose a category");
      return false;
    }
    if (document.getElementById("relatedItemUniqueId").value.trim() == "") {
      if (document.getElementById("relatedItemName").value.trim() != "") {
        alert("Make sure to choose an item from the list");
        return false;
      }
      alert("Input the name of the item");
      return false;
    }
    return true;
  }
</script>
<script>
  var xhr${widgetContext.uniqueId};
  new autoComplete({
    selector: 'input[name="relatedItemName"]',
    source: function (term, response) {
      if (document.getElementById("relatedCollectionId").value.trim() == "") {
        alert("Please choose a collection to search");
        return false;
      }
      try {
        xhr${widgetContext.uniqueId}.abort();
      } catch (e) {
      }
      xhr${widgetContext.uniqueId} = $.getJSON('${ctx}/json/lookupItem?iid=${item.uniqueId}&cid=' + $("#relatedCollectionId").val(), {q: term}, function (data) {
        response(data);
      });
    },
    renderItem: function (item, search) {
      search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
      var re = new RegExp("(" + search.split(' ').join('|') + ")", "gi");
      return '<div class="autocomplete-suggestion" data-itemName="' + item[0] + '" data-uniqueId="' + item[1] + '" data-val="' + search + '">' + item[0].replace(re, "<b>$1</b>") + '</div>';
    },
    onSelect: function (e, term, item) {
      document.getElementById("relatedItemName").value = item.getAttribute("data-itemName");
      document.getElementById("relatedItemUniqueId").value = item.getAttribute('data-uniqueId');
    }
  });
</script>
