<%--
  ~ Copyright 2026 SimIS Inc.
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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<jsp:useBean id="secretStatusList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<p class="help-text">Every integration credential the platform can store, in one place. Values are
  encrypted at rest and never shown here -- rotate a value from its own settings page (linked
  below); this view is read-only. Rows with no link are provisioned directly in the database and
  not editable from any admin screen, by design.</p>
<table class="unstriped stack">
  <thead>
  <tr>
    <th>Integration</th>
    <th>Status</th>
    <th>Last Rotated</th>
    <th>Expires</th>
    <th>Manage</th>
  </tr>
  </thead>
  <tbody>
  <c:forEach items="${secretStatusList}" var="secretStatus">
    <tr>
      <td><code><c:out value="${secretStatus.name}" /></code><br/><small class="subheader"><c:out value="${secretStatus.label}" /></small></td>
      <td>
        <c:choose>
          <c:when test="${secretStatus.set}"><span class="label success">Set</span></c:when>
          <c:otherwise><span class="label secondary">Not set</span></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty secretStatus.modified}">
            <fmt:formatDate pattern="yyyy-MM-dd" value="${secretStatus.modified}" />
            <c:if test="${!empty secretStatus.modifiedByName}"> by <c:out value="${secretStatus.modifiedByName}" /></c:if>
          </c:when>
          <c:otherwise><small class="subheader">Unknown</small></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${secretStatus.expiryStatus eq 'expired'}">
            <span class="label alert">Expired <fmt:formatDate pattern="yyyy-MM-dd" value="${secretStatus.expiresAt}" /></span>
          </c:when>
          <c:when test="${secretStatus.expiryStatus eq 'expiring-soon'}">
            <span class="label warning">Expires <fmt:formatDate pattern="yyyy-MM-dd" value="${secretStatus.expiresAt}" /></span>
          </c:when>
          <c:when test="${secretStatus.expiryStatus eq 'ok'}">
            <fmt:formatDate pattern="yyyy-MM-dd" value="${secretStatus.expiresAt}" />
          </c:when>
          <c:otherwise><small class="subheader">&mdash;</small></c:otherwise>
        </c:choose>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty secretStatus.pageUrl}">
            <a href="${ctx}<c:out value="${secretStatus.pageUrl}"/>">Manage</a>
          </c:when>
          <c:when test="${secretStatus.disabled}">
            <small class="subheader" title="Provisioned directly in the database; not editable from any admin screen">Database-managed</small>
          </c:when>
          <c:otherwise>
            <small class="subheader" title="No admin editor exists yet for this credential">No admin UI</small>
          </c:otherwise>
        </c:choose>
      </td>
    </tr>
  </c:forEach>
  <c:if test="${empty secretStatusList}">
    <tr>
      <td colspan="5">No integration credentials are registered</td>
    </tr>
  </c:if>
  </tbody>
</table>
