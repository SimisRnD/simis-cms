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
<%@ taglib prefix="collection" uri="/WEB-INF/tlds/collection-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="collectionRoleList" class="java.util.ArrayList" scope="request"/>
<form method="post" onsubmit="return checkForm${widgetContext.uniqueId}()" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form specific --%>
  <%--<input type="hidden" name="itemId" value="${item.id}"/>--%>
  <input type="hidden" name="itemUniqueId" value="${item.uniqueId}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <select id="roleId" name="roleId">
    <option value="">Choose a role...</option>
    <c:forEach items="${collectionRoleList}" var="role" varStatus="status">
      <option value="${role.id}">
        <c:out value="${role.title}"/>
      </option>
    </c:forEach>
  </select>
  <input type="text" placeholder="Enter a name" id="searchName" name="searchName" value="" autocomplete="off">
  <input type="hidden" id="selectedEntry" name="selectedEntry" value=""/>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="Add"/>
  </div>
</form>
<script>
  function checkForm${widgetContext.uniqueId}() {
    if (document.getElementById("roleId").value.trim() == "") {
      alert("Make sure to choose a role");
      return false;
    }
    if (document.getElementById("selectedEntry").value.trim() == "") {
      var searchValue = document.getElementById("searchName").value.trim();
      if (searchValue == "") {
        alert("Make sure to search for a user");
        return false;
      } else if (searchValue.indexOf("@") > -1) {
        document.getElementById("selectedEntry").value = searchValue;
      } else {
        alert("Make sure to choose a result from the list");
        return false;
      }
    }
    return true;
  }
</script>
<script>
  var xhr${widgetContext.uniqueId};
  new autoComplete({
    selector: 'input[name="searchName"]',
    cache: false,
    source: function (term, response) {
      try {
        xhr${widgetContext.uniqueId}.abort();
      } catch (e) {
      }
      xhr${widgetContext.uniqueId} = $.getJSON('${ctx}/json/lookupUser', {q: term}, function (data) {
        response(data);
      });
    },
    renderItem: function (item, search) {
      search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
      var re = new RegExp("(" + search.split(' ').join('|') + ")", "gi");
      return '<div class="autocomplete-suggestion" data-itemName="' + item[0] + '" data-uniqueId="' + item[1] + '" data-val="' + search + '">' + item[0].replace(re, "<b>$1</b>") + '</div>';
    },
    onSelect: function (e, term, item) {
      document.getElementById("searchName").value = item.getAttribute('data-itemName');
      document.getElementById("selectedEntry").value = item.getAttribute('data-uniqueId');
    }
  });
</script>
