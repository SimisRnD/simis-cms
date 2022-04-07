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
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="number" uri="/WEB-INF/number-functions.tld" %>
<%@ taglib prefix="folderCategory" uri="/WEB-INF/folder-category-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="useViewer" class="java.lang.String" scope="request"/>
<jsp:useBean id="useDateForTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="folderYearMap" class="java.util.LinkedHashMap" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<c:if test="${empty folderYearMap}">
  No documents were found
</c:if>
<c:if test="${!empty folderYearMap}">
  <div class="platform-content-container">
    <ul class="tabs" data-deep-link="true" data-update-history="true" data-deep-link-smudge="true" data-deep-link-smudge-delay="500" data-tabs id="deeplinked-tabs">
      <c:forEach items="${folderYearMap}" var="year" varStatus="tabStatus">
        <li class="tabs-title<c:if test="${tabStatus.first}"> is-active</c:if>"><a href="#<c:out value="${year.key}" />" aria-selected="true"><c:out value="${year.key}" /></a></li>
      </c:forEach>
    </ul>
    <div class="tabs-content" data-tabs-content="deeplinked-tabs">
      <c:forEach items="${folderYearMap}" var="year" varStatus="tabStatus">
        <div class="tabs-panel<c:if test="${tabStatus.first}"> is-active</c:if>" id="${year.key}">
          <ul class="platform-folder-list-container">
            <c:forEach items="${year.value}" var="subFolder" varStatus="subFolderStatus">
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
                        <span class="label"><a title="<c:out value="${file.title}" />" href="${ctx}/assets/file/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}">File</a></span>
                      </c:when>
                      <c:when test="${fn:toLowerCase(file.fileType) eq 'url'}">
                        <span class="label"><a target="_blank" title="<c:out value="${file.title}" />" href="${ctx}/assets/view/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}?ref=${url:encodeUri(file.filename)}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
                      </c:when>
                      <c:when test="${fn:toLowerCase(file.fileType) eq 'video' || fn:toLowerCase(file.fileType) eq 'pdf' || fn:toLowerCase(file.fileType) eq 'image'}">
                        <span class="label"><a target="_blank" title="<c:out value="${file.title}" />" href="${ctx}/assets/view/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
                      </c:when>
                      <c:otherwise>
                        <span class="label"><a title="<c:out value="${file.title}" />" href="${ctx}/assets/file/<fmt:formatDate pattern="yyyyMMddHHmmss" value="${file.modified}" />-${file.id}/${url:encodeUri(file.filename)}"><c:out value="${folderCategory:name(file.categoryId)}" /></a></span>
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
        </div>
      </c:forEach>
    </div>
  </div>
</c:if>
