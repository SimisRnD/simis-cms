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
<%@ taglib prefix="font" uri="/WEB-INF/font-functions.tld" %>
<%@ taglib prefix="group" uri="/WEB-INF/group-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="collectionList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<table class="unstriped">
  <thead>
    <tr>
      <th>Name</th>
      <th>Access Controls</th>
      <th width="175">Link</th>
      <th width="100" class="text-center"># of categories</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${collectionList}" var="collection">
      <tr>
        <td>
          <c:set var="thisIcon" scope="request" value="database"/>
          <c:if test="${!empty collection.icon}">
            <c:set var="thisIcon" scope="request" value="${collection.icon}"/>
          </c:if>
          <a href="${ctx}/admin/collection-details?collectionId=${collection.id}"><i class="${font:fad()} fa-<c:out value="${thisIcon}" />"></i> <c:out value="${collection.name}" /></a><br />
          <small class="subheader"><fmt:formatNumber value="${collection.itemCount}" /> record<c:if test="${collection.itemCount ne 1}">s</c:if></small>
        </td>
        <td>
          <%--
          <c:if test="${!empty collection.description}">
            <small class="subheader"><c:out value="${collection.description}" /></small><br />
          </c:if>
          --%>
          <c:if test="${collection.allowsGuests}">
            <span class="label success">All Guests</span>
            <%--
            <c:choose>
              <c:when test="${collection.guestPrivacyType eq 2000}"><span class="label round secondary">all</span></c:when>
              <c:when test="${collection.guestPrivacyType eq 3000}"><span class="label round secondary">all-read-only</span></c:when>
              <c:when test="${collection.guestPrivacyType eq 4000}"><span class="label round secondary">all-protected</span></c:when>
              <c:when test="${collection.guestPrivacyType eq 1000}"><span class="label round secondary">assigned</span></c:when>
            </c:choose>
            --%>
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
                  <c:when test="${collectionGroup.privacyType eq 1000}"><span class="label secondary radius">view assigned</span></c:when>
                  <c:when test="${collectionGroup.privacyType eq 2000}"><span class="label secondary radius">view all</span> <small class="">/ auto join</small></c:when>
                  <c:when test="${collectionGroup.privacyType eq 3000}"><span class="label secondary radius">view all</span> <small class="">/ ask to join</small></c:when>
                  <c:when test="${collectionGroup.privacyType eq 4000}"><span class="label secondary radius">view all / summary-only</span> <small class="">/ ask to join</small></c:when>
                </c:choose>
                <c:if test="${collectionGroup.addPermission}"><small class="">/ add</small></c:if>
                <c:if test="${collectionGroup.editPermission}"><small class="">/ edit</small></c:if>
                <c:if test="${collectionGroup.deletePermission}"><small class="">/ delete</small></c:if>
                <c:if test="${!status.last}"><br /></c:if>
              </c:forEach>
            </c:otherwise>
          </c:choose>
          <%--<c:forEach items="${collection.privacyTypes}" var="privacyType" varStatus="status"><span class="label round secondary"><c:out value="${privacyType}" /></span><c:if test="${!status.last}" > </c:if></c:forEach>--%>
        </td>
        <td>
          <small class="subheader"><a href="${ctx}${collection.listingsLink}"><c:out value="${collection.listingsLink}" /></a></small>
        </td>
        <td class="text-center">
          <fmt:formatNumber value="${collection.categoryCount}" />
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty collectionList}">
      <tr>
        <td colspan="4">No collections were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
