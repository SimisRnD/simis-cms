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
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="folderList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<c:if test="${userSession.hasRole('admin')}">
  <a class="button small radius primary" href="${ctx}/admin/folder?returnPage=/admin/folders">Add a Folder <i class="fa fa-arrow-circle-right"></i></a>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th>Access</th>
      <th width="100" class="text-center"># of files</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${folderList}" var="folder">
      <tr>
        <td>
          <%--<a href="${ctx}/admin/folder?folderId=${folder.id}"><c:out value="${folder.name}" /></a><br />--%>
          <a href="${ctx}/admin/folder-details?folderId=${folder.id}"><i class="fa fa-folder"></i> <c:out value="${folder.name}" /></a><br />
          <%--
          <c:if test="${!empty collection.description}">
            <small class="subheader"><c:out value="${collection.description}" /></small><br />
          </c:if>
          --%>
        </td>
        <td>
          <c:if test="${folder.allowsGuests}">
            <span class="label success">All Guests</span>
            <c:choose>
              <c:when test="${folder.guestPrivacyType eq 1000}"><span class="label round secondary">Own Files</span></c:when>
              <c:when test="${folder.guestPrivacyType eq 2000}"><span class="label round secondary">All Files</span></c:when>
              <c:when test="${folder.guestPrivacyType eq 3000}"><span class="label round secondary">Files By Token Only</span></c:when>
              <c:when test="${folder.guestPrivacyType eq 4000}"><span class="label round secondary">Drop Box</span></c:when>
            </c:choose>
            <c:if test="${!empty folder.folderGroupList}">
              <br />
            </c:if>
          </c:if>
          <c:choose>
            <c:when test="${empty folder.folderGroupList && !folder.allowsGuests}"><span class="label alert">No access</span></c:when>
            <c:otherwise>
              <c:forEach items="${folder.folderGroupList}" var="folderGroup" varStatus="status">
                <c:choose>
                  <c:when test="${group:name(folderGroup.groupId) eq 'All Users'}">
                    <span class="label success"><c:out value="${group:name(folderGroup.groupId)}" /></span>
                  </c:when>
                  <c:otherwise>
                    <span class="label primary"><c:out value="${group:name(folderGroup.groupId)}" /></span>
                  </c:otherwise>
                </c:choose>
                <c:choose>
                  <c:when test="${folderGroup.privacyType eq 1000}"><span class="label secondary round">Own Files</span></c:when>
                  <c:when test="${folderGroup.privacyType eq 2000}"><span class="label secondary round">All Files</span></c:when>
                  <c:when test="${folderGroup.privacyType eq 3000}"><span class="label secondary round">Files By Token Only</span></c:when>
                  <c:when test="${folderGroup.privacyType eq 4000}"><span class="label secondary round">Drop Box</span></c:when>
                </c:choose>
                <c:if test="${folderGroup.addPermission}"><small>add</small></c:if>
                <c:if test="${folderGroup.editPermission}"><small>edit</small></c:if>
                <c:if test="${folderGroup.deletePermission}"><small>delete</small></c:if>
                <c:if test="${!status.last}"><br /></c:if>
              </c:forEach>
            </c:otherwise>
          </c:choose>
          <%--<c:forEach items="${collection.privacyTypes}" var="privacyType" varStatus="status"><span class="label round secondary"><c:out value="${privacyType}" /></span><c:if test="${!status.last}" > </c:if></c:forEach>--%>
        </td>
        <td class="text-center">
          <fmt:formatNumber value="${folder.fileCount}" />
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty folderList}">
      <tr>
        <td colspan="3">No folders were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
