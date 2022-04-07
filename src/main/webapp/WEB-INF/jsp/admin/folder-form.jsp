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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.login.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.cms.WidgetContext" scope="request"/>
<jsp:useBean id="folder" class="com.simisinc.platform.domain.model.cms.Folder" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<c:choose>
  <c:when test="${folder.id eq -1}"><h4>New Folder</h4></c:when>
  <c:otherwise><h4>Update Folder</h4></c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${folder.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <label>Folder Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${folder.name}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="summary" value="<c:out value="${folder.summary}"/>">
  </label>
  <fieldset>
    <legend>File Categories</legend>
    <c:forEach items="${folder.folderCategoryList}" var="category" varStatus="categoryStatus">
      <input type="hidden" name="category${categoryStatus.index}id" value="${category.id}" />
      <input type="text" name="category${categoryStatus.index}name" placeholder="Category Name" value="<c:out value="${category.name}" />" style="width:200px; float:left; margin-right: 20px;" />
    </c:forEach>
    <c:forEach begin="${fn:length(folder.folderCategoryList)}" end="${fn:length(folder.folderCategoryList) + 4}" varStatus="loop">
<%--      <input type="checkbox" name="category${loop.index + fn:length(folder.folderCategoryList)}enabled" value="true" />--%>
      <input type="text" name="category${loop.index}name" placeholder="Category Name" value="" style="width:200px; float:left; margin-right: 20px;" />
    </c:forEach>
  </fieldset>
  <c:if test="${!empty groupList}">
    <fieldset>
      <legend>Access Groups</legend>
      <table>
        <tr>
          <th class="text-left">Name</th>
          <th class="text-left">Allowed Access?</th>
          <th>Add</th>
          <th>Edit</th>
          <th>Delete</th>
        </tr>
        <tr>
          <td>
            All Guests (Non-Users and Users)
          </td>
          <td>
            <select name="guestPrivacyType">
              <option value="-1"></option>
                <%--<option value="1000"<c:if test='${folder.guestPrivacyType == 1000}'> selected</c:if>>Own Files</option>--%>
              <option value="2000"<c:if test='${folder.guestPrivacyType == 2000}'> selected</c:if>>All Files</option>
              <option value="3000"<c:if test='${folder.guestPrivacyType == 3000}'> selected</c:if>>Files By Token Only</option>
              <%--<option value="4000"<c:if test='${folder.guestPrivacyType eq 4000}'> selected</c:if>>Drop Box Only</option>--%>
            </select>
          </td>
          <td colspan="3"></td>
        </tr>
        <c:forEach items="${groupList}" var="group">
          <c:set var="folderGroup" scope="request" value="${folder.getFolderGroup(group.id)}"/>
          <tr>
            <td>
              <c:out value="${group.name}" />
            </td>
            <td>
              <select name="groupId${group.id}privacyType">
                <option value=""></option>
                <option value="public"<c:if test='${!empty folderGroup && folderGroup.privacyType == 2000}'> selected</c:if>>All Files</option>
                <option value="private"<c:if test='${!empty folderGroup && folderGroup.privacyType == 1000}'> selected</c:if>>Own Files</option>
                <option value="public-read-only"<c:if test='${!empty folderGroup && folderGroup.privacyType == 3000}'> selected</c:if>>Files By Token Only</option>
                <option value="protected"<c:if test='${!empty folderGroup && folderGroup.privacyType eq 4000}'> selected</c:if>>Drop Box Only</option>
              </select>
            </td>
            <td class="text-center">
              <input type="checkbox" id="groupId${group.id}add" name="groupId${group.id}add" value="${group.id}" <c:if test="${!empty folderGroup && folderGroup.addPermission}">checked</c:if>/><label for="groupId${group.id}add"></label>
            </td>
            <td class="text-center">
              <input type="checkbox" id="groupId${group.id}edit" name="groupId${group.id}edit" value="${group.id}" <c:if test="${!empty folderGroup && folderGroup.editPermission}">checked</c:if>/><label for="groupId${group.id}edit"></label>
            </td>
            <td class="text-center">
              <input type="checkbox" id="groupId${group.id}delete" name="groupId${group.id}delete" value="${group.id}" <c:if test="${!empty folderGroup && folderGroup.deletePermission}">checked</c:if>/><label for="groupId${group.id}delete"></label>
            </td>
          </tr>
        </c:forEach>
      </table>
    </fieldset>
  </c:if>
<%--
  <fieldset>
    <legend>Access Options</legend>
    <input type="checkbox" id="privacyTypesPrivate" name="privacyTypes" value="private" <c:if test="${fn:contains(folder.privacyTypesList, 'private')}">checked</c:if>/><label for="privacyTypesPrivate">Private (hidden to all users)</label><br />
    <input type="checkbox" id="privacyTypesPublic" name="privacyTypes" value="public" <c:if test="${fn:contains(folder.privacyTypesList, 'public')}">checked</c:if>/><label for="privacyTypesPublic">Public</label><br />
    <input type="checkbox" id="privacyTypesPublicReadOnly" name="privacyTypes" value="public-read-only" <c:if test="${fn:contains(folder.privacyTypesList, 'public-read-only')}">checked</c:if>/><label for="privacyTypesPublicReadOnly">Public (read-only)</label><br />
    <input type="checkbox" id="privacyTypesProtected" name="privacyTypes" value="protected" <c:if test="${fn:contains(folder.privacyTypesList, 'protected')}">checked</c:if>/><label for="privacyTypesProtected">Protected (visible, requires permission)</label>
  </fieldset>
--%>
  <c:choose>
    <c:when test="${!empty returnPage}">
      <p>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </p>
    </c:when>
    <c:otherwise>
      <p><input type="submit" class="button radius success expanded" value="Save"/></p>
    </c:otherwise>
  </c:choose>
</form>