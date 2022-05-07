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
<%@ taglib prefix="js" uri="/WEB-INF/javascript-escape.tld" %>
<%@ taglib prefix="collection" uri="/WEB-INF/collection-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="relationshipList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>
        Relationships
      </th>
    </tr>
  </thead>
  <tbody>
    <c:if test="${empty relationshipList}">
      <tr>
        <td class="subheader">No relationships were found</td>
      </tr>
    </c:if>
    <c:forEach items="${relationshipList}" var="relationship">
    <tr>
      <td>
        <c:choose>
          <c:when test="${relationship.collectionId == relationship.relatedCollectionId}">
            <c:out value="${collection.name}" />
            <i class="fa fa-exchange"></i>
            <c:out value="${collection:name(relationship.relatedCollectionId)}" />
          </c:when>
          <c:when test="${relationship.relatedCollectionId == collection.id}">
            <a href="${ctx}/admin/collection-details?collectionId=${relationship.collectionId}"><c:out value="${collection:name(relationship.collectionId)}" /></a>
            <i class="fa fa-angle-double-right"></i>
            <c:out value="${collection.name}" />
          </c:when>
          <c:otherwise>
            <c:out value="${collection.name}" /> <i class="fa fa-angle-double-right"></i>
            <a href="${ctx}/admin/collection-details?collectionId=${relationship.relatedCollectionId}"><c:out value="${collection:name(relationship.relatedCollectionId)}" /></a>
          </c:otherwise>
        </c:choose>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&relationshipId=${relationship.id}" onclick="return confirm('Are you sure you want to remove <c:out value="${js:escape('this relationship')}" />?');"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
  </tbody>
</table>
