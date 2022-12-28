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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/tlds/number-functions.tld" %>
<%@ taglib prefix="folderCategory" uri="/WEB-INF/tlds/folder-category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="subFolderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="fileItemList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<jsp:useBean id="typeFilter" class="java.lang.String" scope="request"/>
<jsp:useBean id="yearFilter" class="java.lang.String" scope="request"/>
<jsp:useBean id="showPaging" class="java.lang.String" scope="request"/>
<jsp:useBean id="folderCategoryList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="folderYearList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="useViewer" class="java.lang.String" scope="request"/>
<jsp:useBean id="useDateForTitle" class="java.lang.String" scope="request"/>
<c:if test="${empty subFolderList}">
  No documents were found
</c:if>
<c:if test="${!empty subFolderList}">
  <div class="platform-sort-options">
    <ul class="dropdown menu" data-dropdown-menu>
      <c:if test="${!empty title}">
        <li class="menu-text"><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></li>
      </c:if>
      <c:if test="${!empty folderYearList}">
      <li class="has-submenu">
        <a href="#1">Year</a>
        <ul class="submenu menu vertical" data-submenu>
          <li><a href="${widgetContext.uri}"><i class="${font:fal()} fa-fw<c:if test="${yearFilter eq 'any'}"> fa-check</c:if>"></i> Any</a></li>
          <c:forEach items="${folderYearList}" var="year">
            <c:set var="yearValue">${year}</c:set>
            <li><a href="?yearFilter=<c:out value="${year}" />"><i class="${font:fal()} fa-fw<c:if test="${yearFilter eq yearValue}"> fa-check</c:if>"></i> <c:out value="${year}" /></a></li>
          </c:forEach>
        </ul>
      </li>
      </c:if>
      <%--
      <c:if test="${!empty folderCategoryList}">
        <li class="has-submenu">
          <a href="#0">Type</a>
          <ul class="submenu menu vertical" data-submenu>
            <li><a href="?sortBy=date&sortOrder=newest"><i class="${font:fal()} fa-fw fa-check"></i> Any</a></li>
            <c:forEach items="${folderCategoryList}" var="category">
              <c:choose>
                <c:when test="${category.name eq 'something'}">
                  <li><a href="?sortBy=date&sortOrder=newest"><i class="${font:fal()} fa-fw fa-check"></i> <c:out value="${category.name}" /></a></li>
                </c:when>
                <c:otherwise>
                  <li><a href="?sortBy=date&sortOrder=oldest"><i class="${font:fal()} fa-fw"></i> <c:out value="${category.name}" /></a></li>
                </c:otherwise>
              </c:choose>
            </c:forEach>
          </ul>
        </li>
      </c:if>
      --%>
    </ul>
  </div>
  <ul class="platform-folder-list-container">
    <c:forEach items="${subFolderList}" var="subFolder" varStatus="subFolderStatus">
    <li class="platform-folder-entry">
      <div class="platform-folder-name">
        <c:choose>
          <c:when test="${useDateForTitle eq 'true'}">
            <fmt:formatDate pattern="MMMMM d, yyyy" value="${subFolder.startDate}"/>
          </c:when>
          <c:otherwise>
            <c:out value="${subFolder.name}" />
          </c:otherwise>
        </c:choose>
      </div>
      <div class="platform-folder-items">
        <c:forEach items="${subFolder.fileItemList}" var="file" varStatus="status">
          <c:choose>
            <c:when test="${file.categoryId lt 1}">
              <span class="label"><a title="<c:out value="${file.title}" />" href="${ctx}/assets/file/${file.url}">File</a></span>
            </c:when>
            <c:when test="${fn:toLowerCase(file.fileType) eq 'url'}">
              <span class="label"><a target="_blank" title="<c:out value="${file.title}" />" href="${ctx}/assets/view/${file.baseUrl}?ref=${url:encodeUri(file.filename)}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
            </c:when>
            <c:when test="${fn:toLowerCase(file.fileType) eq 'video' || fn:toLowerCase(file.fileType) eq 'pdf' || fn:toLowerCase(file.fileType) eq 'image'}">
              <span class="label"><a target="_blank" title="<c:out value="${file.title}" />" href="${ctx}/assets/view/${file.url}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
            </c:when>
            <c:otherwise>
              <span class="label"><a title="<c:out value="${file.title}" />" href="${ctx}/assets/file/${file.url}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
            </c:otherwise>
          </c:choose>
  <%--        <c:if test="${file.fileLength gt 0}">--%>
  <%--          <small class="subheader"><c:out value="${number:suffix(file.fileLength)}"/></small>--%>
  <%--        </c:if>--%>
        </c:forEach>
      </div>
    </li>
  </c:forEach>
  </ul>
  <%-- Paging Control --%>
  <%@include file="../paging_control.jspf" %>
</c:if>
