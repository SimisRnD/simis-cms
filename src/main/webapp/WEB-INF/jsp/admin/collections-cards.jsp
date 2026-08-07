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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collectionList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="${font:far()} ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<p class="help-text">A collection is a structured data container built into the CMS -- custom fields, categories, access controls, and a set of widgets (item lists, search, add/edit forms) for displaying and managing its records on the public site. Think of it as a small database with a web UI, useful for things like staff directories, resource libraries, or catalogs that don't fit the page/content-block model. A collection has no bulk-import feature of its own -- to load many records at once, create a Dataset (a separate admin area, see the Datasets page) and point it at this collection; the Dataset is what reads a CSV/TSV/JSON/GeoJSON/RSS file (no Excel/.xlsx support) and populates the collection's items.</p>
<div class="clear-float">
  <a class="button small radius primary" href="${ctx}/admin/collection-form?returnPage=/admin/collections">Add a Collection <i class="fa fa-arrow-circle-right"></i></a>
</div>
<c:forEach items="${collectionList}" var="collection">
  <a href="${ctx}/admin/collection-details?collectionId=${collection.id}">
    <div class="admin card float-left" style="min-height: 160px">
      <div class="card-divider" style="background-color: <c:out value="${collection.headerBgColor}" />">
        <p style="color: <c:out value="${collection.headerTextColor}" />">
          <c:set var="thisIcon" scope="request" value="database"/>
          <c:if test="${!empty collection.icon}">
            <c:set var="thisIcon" scope="request" value="${collection.icon}"/>
          </c:if>
          <i class="${font:fad()} fa-<c:out value="${thisIcon}" />"></i> <c:out value="${collection.name}" />
        </p>
      </div>
      <div class="card-section">
        <small>
        <c:choose>
          <c:when test="${collection.itemCount == 0}">no records</c:when>
          <c:when test="${collection.itemCount == 1}"><fmt:formatNumber value="${collection.itemCount}" /> record</c:when>
          <c:otherwise><fmt:formatNumber value="${collection.itemCount}" /> records</c:otherwise>
        </c:choose>
        </small>
        <c:if test="${!collection.allowsGuests}">
          <br />
          <span class="label alert"><small>PRIVATE</small></span>
        </c:if>
      </div>
    </div>
  </a>
</c:forEach>
<c:if test="${empty collectionList}">
  <p>No collections were found</p>
</c:if>
