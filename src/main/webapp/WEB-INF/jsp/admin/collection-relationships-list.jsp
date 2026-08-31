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
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="collection" uri="/WEB-INF/tlds/collection-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="relationshipList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text page-help">A relationship here only declares that this collection and another one are allowed to link records to each other -- it doesn't link any specific record itself. Linking an individual record in this collection to one in the related collection happens later, on that item's own page, not on this admin screen. Relationships are one-directional: declaring that this collection relates to another does not automatically make the reverse true, so if records need to link both ways, add a relationship on the other collection too.</p>
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
        <a href="#" data-confirm-post="Are you sure you want to remove this relationship?" data-post-url="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&relationshipId=${relationship.id}"><i class="fa fa-remove"></i></a>
      </td>
    </tr>
    </c:forEach>
  </tbody>
</table>
