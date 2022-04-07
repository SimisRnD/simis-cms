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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="item" class="com.simisinc.platform.domain.model.items.Item" scope="request"/>
<jsp:useBean id="itemTabList" class="java.util.ArrayList" scope="request"/>
<style>
    .item-menu.extended-bar {
        background-color: #ff9f2a;
        color: #ffffff;
    }

    .item-menu.extended-bar img {
        max-height: 52px;
        max-width: 200px;
        margin-right: 15px;
    }
</style>
<div class="item-menu extended-bar">
  <div class="text-center padding-20">
    <h1>
      <c:choose>
        <c:when test="${!empty item.imageUrl}">
          <%--                          <img src="/assets/img/1613486955904-2/SimIS%20Logo.png" />--%>
          <img alt="item image" src="<c:out value="${item.imageUrl}"/>"/>
        </c:when>
        <c:when test="${!empty collection.icon}">
          <i class="${font:fad()} fa-<c:out value="${collection.icon}" />"></i>
        </c:when>
      </c:choose>
      <c:out value="${item.name}"/>
    </h1>
  </div>
  <div>
    <ul class="menu align-center">
      <c:forEach items="${itemTabList}" var="tab">
        <c:choose>
          <c:when test="${tab.isActive}">
            <li class="is-active"><a href="${ctx}${tab.href}"><c:out value="${tab.name}"/></a></li>
          </c:when>
          <c:otherwise>
            <li class=""><a href="${ctx}${tab.href}"><c:out value="${tab.name}"/></a></li>
          </c:otherwise>
        </c:choose>
      </c:forEach>
      <c:if test="${userSession.hasRole('admin')}">
        <li><a href="${ctx}/admin/collection-details?collectionId=${collection.id}"><i class="fa fa-arrow-circle-right"></i></a></li>
      </c:if>
    </ul>
  </div>
</div>
