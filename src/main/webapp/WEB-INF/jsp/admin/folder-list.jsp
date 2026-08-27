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
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="folderList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="query" class="java.lang.String" scope="request"/>
<jsp:useBean id="sort" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<c:if test="${userSession.hasRole('admin')}">
  <a class="button small radius primary" href="${ctx}/admin/folder?returnPage=/admin/folders">Add a Folder <i class="fa fa-arrow-circle-right"></i></a>
</c:if>
<form method="get" autocomplete="off" class="float-right">
  <div class="input-group no-gap width-auto">
    <input class="input-group-field" type="search" name="query" aria-label="Search folders" placeholder="<c:if test="${empty query}">Search folders...</c:if>"<c:if test="${!empty query}"> value="<c:out value="${query}"/>"</c:if> autocomplete="off">
    <select class="input-group-field" name="sort" aria-label="Sort folders" onchange="this.form.submit()" style="max-width:180px;">
      <option value="name" <c:if test="${sort eq 'name'}">selected</c:if>>Name (A-Z)</option>
      <option value="name_desc" <c:if test="${sort eq 'name_desc'}">selected</c:if>>Name (Z-A)</option>
      <option value="files_desc" <c:if test="${sort eq 'files_desc'}">selected</c:if>># of Files (High-Low)</option>
      <option value="files_asc" <c:if test="${sort eq 'files_asc'}">selected</c:if>># of Files (Low-High)</option>
    </select>
    <div class="input-group-button">
      <button type="submit" class="button search" aria-label="Search"><i class="fa fa-search" aria-hidden="true"></i></button>
    </div>
  </div>
</form>
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
          <c:choose>
            <c:when test="${empty folder.folderGroupList && !folder.allowsGuests}">
              <span class="label alert">No access</span>
              <c:if test="${userSession.hasRole('admin')}">
                <a href="${ctx}/admin/folder?folderId=${folder.id}&returnPage=/admin/folders" title="Edit access"><i class="fa fa-edit"></i></a>
              </c:if>
            </c:when>
            <c:otherwise>
              <table class="folder-access-table">
                <thead>
                  <tr>
                    <th>Who</th>
                    <th class="text-center">Access</th>
                    <th class="text-center" title="Can add files">Add</th>
                    <th class="text-center" title="Can edit files">Edit</th>
                    <th class="text-center" title="Can delete files">Delete</th>
                  </tr>
                </thead>
                <tbody>
                  <c:if test="${folder.allowsGuests}">
                    <tr>
                      <td><span class="label success">All Guests</span></td>
                      <td class="text-center">
                        <c:choose>
                          <c:when test="${folder.guestPrivacyType eq 1000}"><span class="label round secondary">Own Files</span></c:when>
                          <c:when test="${folder.guestPrivacyType eq 2000}"><span class="label round success">All Files</span></c:when>
                          <c:when test="${folder.guestPrivacyType eq 3000}"><span class="label round warning">Token Only</span></c:when>
                          <c:when test="${folder.guestPrivacyType eq 4000}"><span class="label round primary">Drop Box</span></c:when>
                        </c:choose>
                      </td>
                      <td class="text-center" aria-label="No">&ndash;</td>
                      <td class="text-center" aria-label="No">&ndash;</td>
                      <td class="text-center" aria-label="No">&ndash;</td>
                    </tr>
                  </c:if>
                  <c:forEach items="${folder.folderGroupList}" var="folderGroup">
                    <tr>
                      <td>
                        <c:choose>
                          <c:when test="${group:name(folderGroup.groupId) eq 'All Users'}">
                            <span class="label success"><c:out value="${group:name(folderGroup.groupId)}" /></span>
                          </c:when>
                          <c:otherwise>
                            <span class="label primary"><c:out value="${group:name(folderGroup.groupId)}" /></span>
                          </c:otherwise>
                        </c:choose>
                      </td>
                      <td class="text-center">
                        <c:choose>
                          <c:when test="${folderGroup.privacyType eq 1000}"><span class="label round secondary">Own Files</span></c:when>
                          <c:when test="${folderGroup.privacyType eq 2000}"><span class="label round success">All Files</span></c:when>
                          <c:when test="${folderGroup.privacyType eq 3000}"><span class="label round warning">Token Only</span></c:when>
                          <c:when test="${folderGroup.privacyType eq 4000}"><span class="label round primary">Drop Box</span></c:when>
                        </c:choose>
                      </td>
                      <td class="text-center">
                        <c:choose>
                          <c:when test="${folderGroup.addPermission}"><i class="fa fa-check" aria-label="Yes"></i></c:when>
                          <c:otherwise><span aria-label="No">&ndash;</span></c:otherwise>
                        </c:choose>
                      </td>
                      <td class="text-center">
                        <c:choose>
                          <c:when test="${folderGroup.editPermission}"><i class="fa fa-check" aria-label="Yes"></i></c:when>
                          <c:otherwise><span aria-label="No">&ndash;</span></c:otherwise>
                        </c:choose>
                      </td>
                      <td class="text-center">
                        <c:choose>
                          <c:when test="${folderGroup.deletePermission}"><i class="fa fa-check" aria-label="Yes"></i></c:when>
                          <c:otherwise><span aria-label="No">&ndash;</span></c:otherwise>
                        </c:choose>
                      </td>
                    </tr>
                  </c:forEach>
                </tbody>
              </table>
              <c:if test="${userSession.hasRole('admin')}">
                <a href="${ctx}/admin/folder?folderId=${folder.id}&returnPage=/admin/folders" title="Edit access"><i class="fa fa-edit"></i> Edit Access</a>
              </c:if>
            </c:otherwise>
          </c:choose>
        </td>
        <td class="text-center">
          <fmt:formatNumber value="${folder.fileCount}" />
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty folderList}">
      <tr>
        <td colspan="3">
          <c:choose>
            <c:when test="${!empty query}">No folders match "<c:out value="${query}" />"</c:when>
            <c:otherwise>No folders were found</c:otherwise>
          </c:choose>
        </td>
      </tr>
    </c:if>
  </tbody>
</table>
<style nonce="${cspNonce}">
  /* Compact per-folder access matrix (issue #502) -- a plain <table> here would inherit the
     platform's default Foundation table padding/borders, which is sized for the page-level list
     and makes this nested table needlessly tall when many folders each show several groups. */
  .folder-access-table {
    width: auto;
    margin: 0 0 4px 0;
    border-collapse: collapse;
  }
  .folder-access-table th,
  .folder-access-table td {
    padding: 2px 8px;
    font-size: 0.75rem;
    border: none;
  }
  .folder-access-table thead th {
    font-weight: bold;
    border-bottom: 1px solid #DBDCDD;
    color: #6f6f6f;
  }
  .folder-access-table tbody tr:not(:last-child) td {
    border-bottom: 1px solid #f0f0f0;
  }
</style>
