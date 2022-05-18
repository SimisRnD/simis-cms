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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<%@ taglib prefix="collection" uri="/WEB-INF/collection-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="categoryMap" class="java.util.HashMap" scope="request"/>
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
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped admin-item-list">
  <thead>
  <tr>
    <th>Name</th>
    <c:if test="${columns eq 'all'}">
      <th>Street</th>
      <th>City</th>
      <th>State</th>
      <th>Postal</th>
      <th>Country</th>
      <th>Geocode</th>
    </c:if>
  </tr>
  </thead>
  <tbody>
  <c:forEach items="${itemList}" var="item">
    <c:set var="category" scope="request" value="${categoryMap.get(item.categoryId)}"/>
    <tr>
      <td>
        <c:choose>
          <c:when test="${!empty item.imageUrl}">
            <div class="item-image">
              <img alt="item image" src="<c:out value="${item.imageUrl}"/>" />
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
      </c:if>
    </tr>
  </c:forEach>
  </tbody>
</table>
<c:if test="${empty itemList}">
  No records were found
</c:if>
<%-- Paging Control --%>
<c:set var="recordPagingParams" scope="request" value="collectionId=${collection.id}"/>
<%@include file="../paging_control.jspf" %>
