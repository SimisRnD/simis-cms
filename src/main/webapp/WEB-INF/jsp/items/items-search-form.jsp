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
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="searchName" class="java.lang.String" scope="request"/>
<jsp:useBean id="searchLocation" class="java.lang.String" scope="request"/>
<jsp:useBean id="categoryId" class="java.lang.String" scope="request"/>
<jsp:useBean id="useAutoComplete" class="java.lang.String" scope="request"/>
<jsp:useBean id="useLocation" class="java.lang.String" scope="request"/>
<jsp:useBean id="showCategories" class="java.lang.String" scope="request"/>
<form method="post" autocomplete="off">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <c:if test="${showCategories ne 'true' && !empty categoryId}">
    <input type="hidden" name="categoryId${widgetContext.uniqueId}" value="${categoryId}">
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Keyword
    <input type="text" placeholder="Search by keyword..." id="name" name="name" value="<c:if test="${!empty searchName}"><c:out value="${searchName}" /></c:if>" autocomplete="off">
  </label>
  <c:if test="${useLocation eq 'true'}">
  <label>Location
    <input type="text" placeholder="Search near city, state" id="location" name="location" value="<c:if test="${!empty searchLocation}"><c:out value="${searchLocation}" /></c:if>" autocomplete="off">
  </label>
  </c:if>
  <c:if test="${showCategories eq 'true' && !empty categoryList}">
    <label>Category
      <select name="categoryId${widgetContext.uniqueId}">
        <option value="-1">All</option>
        <c:forEach items="${categoryList}" var="category">
          <option value="${category.id}"<c:if test="${category.id eq categoryId}"> selected</c:if>><c:out value="${category.name}" /></option>
        </c:forEach>
      </select>
    </label>
  </c:if>
  <div class="button-container">
    <input type="submit" class="button radius primary expanded" value="Search"/>
  </div>
</form>
<c:if test="${useLocation eq 'true' or useAutoComplete eq 'true'}">
<script>
  <c:if test="${useLocation eq 'true'}">
  <%-- Location --%>
  var xhr${widgetContext.uniqueId}location;
  new autoComplete({
    selector: 'input[name="location"]',
    source: function(term, response){
      try { xhr${widgetContext.uniqueId}location.abort(); } catch(e){}
      xhr${widgetContext.uniqueId}location = $.getJSON('${ctx}/json/lookupLocation', { q: term }, function(data) {
        response(data);
      });
    }
  });
  </c:if>

  <c:if test="${useAutoComplete eq 'true'}">
  var xhr${widgetContext.uniqueId}name;
  new autoComplete({
    selector: 'input[name="name"]',
    source: function (term, response) {
      try { xhr${widgetContext.uniqueId}name.abort(); } catch (e) {}
      xhr${widgetContext.uniqueId}name = $.getJSON('${ctx}/json/lookupItem?cid=${collection.id}', {q: term}, function (data) {
        response(data);
      });
    },
    renderItem: function (item, search) {
      search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&');
      var re = new RegExp("(" + search.split(' ').join('|') + ")", "gi");
      return '<div class="autocomplete-suggestion" data-itemName="' + item[0] + '" data-uniqueId="' + item[1] + '" data-city="' + item[2] + '" data-val="' + search + '">' + item[0].replace(re, "<b>$1</b>") + (item[2] ? ' (' + item[2] + ')' : '') + '</div>';
    },
    onSelect: function (e, term, item) {
      document.getElementById("name").value = item.getAttribute("data-itemName");
      var locationEl = document.getElementById("location");
      if (locationEl) {
        locationEl.value = item.getAttribute("data-city");
      }
    }
  });
  </c:if>
</script>
</c:if>