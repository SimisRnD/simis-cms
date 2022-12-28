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
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collection" class="com.simisinc.platform.domain.model.items.Collection" scope="request"/>
<%@include file="../page_messages.jspf" %>
<a class="button small radius primary float-left" href="${ctx}/admin/collection-form?collectionId=${collection.id}&returnPage=${widgetContext.uri}?collectionId=${collection.id}"><i class="${font:fas()} fa-edit"></i> Modify</a>
<%--
<form method="post" action="/admin/collection-details?collectionId=${collection.id}">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="command" value="downloadCategoriesCSVFile" />
  <button class="button small radius secondary float-left margin-left-10"><i class="fa fa-download"></i> Download Categories</button>
</form>
<form method="post" action="/admin/collection-details?collectionId=${collection.id}">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="command" value="downloadItemsCSVFile" />
  <button class="button small radius secondary float-left margin-left-10"><i class="fa fa-download"></i> Download Items</button>
</form>
--%>
<a class="button small radius alert margin-left-10" href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&collectionId=${collection.id}" onclick="return confirm('Are you sure you want to delete <c:out value="${js:escape(collection.name)}" />?');"><i class="fa fa-remove"></i> Delete</a>
<p class="callout box radius">
  <c:if test="${!empty collection.description}">
    <small class="subheader"><c:out value="${collection.description}" /></small><br />
  </c:if>
  <c:if test="${collection.allowsGuests}">
    <span class="label success">All Guests</span>
    <c:choose>
      <c:when test="${collection.guestPrivacyType eq 2000}"><span class="label round secondary">all</span></c:when>
      <c:when test="${collection.guestPrivacyType eq 3000}"><span class="label round secondary">all-read-only</span></c:when>
      <c:when test="${collection.guestPrivacyType eq 4000}"><span class="label round secondary">all-summary-only</span></c:when>
      <c:when test="${collection.guestPrivacyType eq 1000}"><span class="label round secondary">assigned</span></c:when>
    </c:choose>
    <c:if test="${!empty collection.collectionGroupList}">
      <br />
    </c:if>
  </c:if>
  <c:choose>
    <c:when test="${empty collection.collectionGroupList}"><span class="label alert">No access</span></c:when>
    <c:otherwise>
      <c:forEach items="${collection.collectionGroupList}" var="collectionGroup" varStatus="status">
        <c:choose>
          <c:when test="${group:name(collectionGroup.groupId) eq 'All Users'}">
            <span class="label success"><c:out value="${group:name(collectionGroup.groupId)}" /></span>
          </c:when>
          <c:otherwise>
            <span class="label primary"><c:out value="${group:name(collectionGroup.groupId)}" /></span>
          </c:otherwise>
        </c:choose>
        <c:choose>
          <c:when test="${collectionGroup.privacyType eq 2000}"><span class="label round secondary">view all</span></c:when>
          <c:when test="${collectionGroup.privacyType eq 3000}"><span class="label round secondary">view all-read-only</span></c:when>
          <c:when test="${collectionGroup.privacyType eq 4000}"><span class="label round secondary">view all-summary-only</span></c:when>
          <c:when test="${collectionGroup.privacyType eq 1000}"><span class="label round secondary">view assigned</span></c:when>
        </c:choose>
        <c:if test="${collectionGroup.addPermission}"><span class="label round secondary">add</span></c:if>
        <c:if test="${collectionGroup.editPermission}"><span class="label round secondary">edit</span></c:if>
        <c:if test="${collectionGroup.deletePermission}"><span class="label round secondary">delete</span></c:if>
        <c:if test="${!status.last}"><br /></c:if>
      </c:forEach>
    </c:otherwise>
  </c:choose>
</p>
