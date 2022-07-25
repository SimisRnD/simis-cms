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
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="category" uri="/WEB-INF/category-functions.tld" %>
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
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<%--<c:choose>--%>
<%--  <c:when test="${category.id gt 0}"><c:out value="${category.name}" /></c:when>--%>
  <%--<c:otherwise>All <c:out value="${collection.name}" /></c:otherwise>--%>
<%--</c:choose>--%>
<c:choose>
  <c:when test="${!empty itemList}">
    <ul<c:if test="${showBullets eq 'false'}"> class="no-bullet"</c:if>>
      <c:forEach items="${itemList}" var="item">
        <li>
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
        </li>
      </c:forEach>
    </ul>
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
