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
<%@ taglib prefix="collection" uri="/WEB-INF/collection-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="itemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="category" class="com.simisinc.platform.domain.model.items.Category" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="smallGridCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumGridCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeGridCount" class="java.lang.String" scope="request"/>
<style>
  .card-catalog a {
    font-weight: bold;
    display: -webkit-box;
    -webkit-line-clamp: 3;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-decoration: none;
    word-break: break-word;
    position: static!important;
  }
  .card-catalog .card-top {
    padding: 1rem;
  }
  .card-catalog .item-name {
    min-height: 90px;
  }
  .card-catalog .card-bottom {
    position: relative;
    bottom: 0;
    padding: 0.5rem 1rem;
    font-size: smaller;
  }
</style>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:choose>
  <c:when test="${!empty itemList}">
    <div class="grid-x grid-margin-x align-stretch card-catalog">
      <c:forEach items="${itemList}" var="item">
        <c:set var="categoryIcon" scope="request" value="${category:icon(item.categoryId)}"/>
        <c:set var="categoryHeaderCSS" scope="request" value="${category:headerColorCSS(item.categoryId)}"/>
        <div class="small-<c:out value="${smallGridCount}" /> medium-<c:out value="${mediumGridCount}" /> large-<c:out value="${largeGridCount}" /> cell card">
          <div class="card-top no-gap no-border text-center" style="<c:out value="${categoryHeaderCSS}" />">
            <c:choose>
              <c:when test="${!empty categoryIcon}">
                <i class="fa fa-2x fa-<c:out value="${categoryIcon}" />"></i>
              </c:when>
              <c:otherwise>
                <i class="fa fa-2x fa-bookmark-o"></i>
              </c:otherwise>
            </c:choose>
          </div>
          <div class="card-section">
            <div class="item-name">
              <a href="${ctx}/show/${item.uniqueId}"><c:out value="${item.name}" /></a>
            </div>
            <c:if test="${!empty item.address}"><div class="item-city"><small><c:out value="${item.address}" /></small></div></c:if>
            <c:if test="${!empty item.keywords}"><div class="item-keywords"><small><c:out value="${item.keywords}" /></small></div></c:if>
          </div>
          <div class="card-bottom">
            <c:if test="${!empty collection:icon(item.collectionId)}">
              <i class="${font:fad()} fa-<c:out value="${collection:icon(item.collectionId)}" />"></i>
            </c:if>
            <c:out value="${category:name(item.categoryId)}" />
          </div>
        </div>
      </c:forEach>
    </div>
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
