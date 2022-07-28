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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<c:set var="thisIcon" scope="request" value="database"/>
<c:if test="${!empty collection.icon}">
  <c:set var="thisIcon" scope="request" value="${collection.icon}"/>
</c:if>
<c:choose>
  <c:when test="${collection.id eq -1}">
    <h3><i class="${font:far()} fa-database"></i> New Collection</h3>
  </c:when>
  <c:otherwise>
    <h3><i class="${font:fad()} fa-<c:out value="${thisIcon}" />"></i> <c:out value="${collection.name}" /></h3>
  </c:otherwise>
</c:choose>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${collection.id}"/>
  <c:if test="${!empty returnPage}">
    <input type="hidden" name="returnPage" value="<c:out value="${returnPage}"/>" />
  </c:if>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-4 cell">
      <label>Collection Name <span class="required">*</span>
        <input type="text" placeholder="Give it a name..." name="name" value="<c:out value="${collection.name}"/>" required>
      </label>
    </div>
    <div class="small-12 medium-3 cell">
      <label>Icon (<a href="https://fontawesome.com/search?m=free&s=solid" target="_blank">view</a>)
        <input type="text" placeholder="" name="icon" value="<c:out value="${collection.icon}"/>">
      </label>
    </div>
    <div class="small-12 medium-5 cell">
      <label>Listings Link
        <input type="text" placeholder="/page" name="listingsLink" value="<c:out value="${collection.listingsLink}"/>">
      </label>
    </div>
  </div>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${collection.description}"/>">
  </label>
  <div class="grid-x grid-margin-x">
    <div class="small-12 medium-4 cell">
      <label>Show listings link
        <div class="switch large">
          <input class="switch-input" id="showListingsLink-yes-no" type="checkbox" name="showListingsLink" value="true"<c:if test="${collection.showListingsLink eq 'true'}"> checked</c:if>>
          <label class="switch-paddle" for="showListingsLink-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>
    </div>
    <div class="small-12 medium-4 cell">
      <label>Show search
        <div class="switch large">
          <input class="switch-input" id="showSearch-yes-no" type="checkbox" name="showSearch" value="true"<c:if test="${collection.showSearch eq 'true'}"> checked</c:if>>
          <label class="switch-paddle" for="showSearch-yes-no">
            <span class="switch-active" aria-hidden="true">Yes</span>
            <span class="switch-inactive" aria-hidden="true">No</span>
          </label>
        </div>
      </label>
    </div>
  </div>
  <c:if test="${!empty groupList}">
    <fieldset>
      <legend>Access Groups</legend>
      <table>
        <tr>
          <th class="text-left">Name</th>
          <th class="text-left">Allowed Access Type</th>
          <th>Add</th>
          <th>Edit</th>
          <th>Delete</th>
        </tr>
        <tr>
          <td>
            All Guests (Non-Users and Users)
          </td>
          <td>
            <select name="guestPrivacyType" class="no-gap">
              <option value="-1"></option>
              <option value="2000"<c:if test='${collection.guestPrivacyType == 2000}'> selected</c:if>>All Records (Login to Collaborate)</option>
              <option value="3000"<c:if test='${collection.guestPrivacyType == 3000}'> selected</c:if>>All Records (Read-Only)</option>
              <option value="4000"<c:if test='${collection.guestPrivacyType eq 4000}'> selected</c:if>>All Records (Summary Only) (Ask to Join)</option>
            </select>
          </td>
          <td colspan="3"></td>
        </tr>
        <c:forEach items="${groupList}" var="group">
          <c:set var="collectionGroup" scope="request" value="${collection.getCollectionGroup(group.id)}"/>
          <tr>
            <td>
              <c:out value="${group.name}" />
            </td>
            <td>
              <select name="groupId${group.id}privacyType" class="no-gap">
                <option value=""></option>
                <option value="public"<c:if test='${!empty collectionGroup && collectionGroup.privacyType == 2000}'> selected</c:if>>All Records</option>
                <option value="public-read-only"<c:if test='${!empty collectionGroup && collectionGroup.privacyType == 3000}'> selected</c:if>>All Records (Read-Only)</option>
                <option value="protected"<c:if test='${!empty collectionGroup && collectionGroup.privacyType eq 4000}'> selected</c:if>>All Records (Summary Only) (Ask to Join)</option>
                <option value="private"<c:if test='${!empty collectionGroup && collectionGroup.privacyType == 1000}'> selected</c:if>>Assigned Records (Private) (Keep Records Hidden)</option>
              </select>
            </td>
            <td class="text-center" nowrap>
              <input type="checkbox" class="no-gap" id="groupId${group.id}add" name="groupId${group.id}add" value="${group.id}" <c:if test="${!empty collectionGroup && collectionGroup.addPermission}">checked</c:if>/><label for="groupId${group.id}add"></label>
            </td>
            <td class="text-center" nowrap>
              <input type="checkbox" class="no-gap" id="groupId${group.id}edit" name="groupId${group.id}edit" value="${group.id}" <c:if test="${!empty collectionGroup && collectionGroup.editPermission}">checked</c:if>/><label for="groupId${group.id}edit"></label>
            </td>
            <td class="text-center" nowrap>
              <input type="checkbox" class="no-gap" id="groupId${group.id}delete" name="groupId${group.id}delete" value="${group.id}" <c:if test="${!empty collectionGroup && collectionGroup.deletePermission}">checked</c:if>/><label for="groupId${group.id}delete"></label>
            </td>
          </tr>
        </c:forEach>
      </table>
    </fieldset>
  </c:if>
  <div class="button-container">
    <c:choose>
      <c:when test="${!empty returnPage}">
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${returnPage}" class="button radius secondary">Cancel</a>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" value="Save"/>
      </c:otherwise>
    </c:choose>
  </div>
</form>
