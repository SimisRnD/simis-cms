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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="js" uri="/WEB-INF/tlds/javascript-escape.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="mailingList" class="com.simisinc.platform.domain.model.mailinglists.MailingList" scope="request"/>
<jsp:useBean id="memberList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<h4><c:out value="${mailingList.name}" /></h4>
<button class="button small primary radius float-left" data-open="formReveal"><i class="fa fa-plus"></i> Add Email</button>
<form id="fileForm" method="post" enctype="multipart/form-data">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="uploadCSVFile" />
  <input type="hidden" name="mailingListId" value="${mailingList.id}" />
  <label for="file" class="button small secondary radius float-left margin-left-10"><i class="fa fa-upload"></i> Upload CSV File</label>
  <input type="file" id="file" name="file" accept="text/csv" class="show-for-sr">
</form>
<form method="post" action="${ctx}/admin/mailing-list-members">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="downloadCSVFile" />
  <input type="hidden" name="mailingListId" value="${mailingList.id}" />
  <button class="button small secondary radius float-left margin-left-10"><i class="fa fa-download"></i> Download CSV File</button>
</form>
<script nonce="${cspNonce}">
  document.getElementById("file").onchange = function() {
    document.getElementById("fileForm").submit();
  }
</script>
<%-- Search/filter (GET so the query lives in the URL and paging preserves it) --%>
<form method="get" autocomplete="off" class="margin-bottom-10">
  <input type="hidden" name="mailingListId" value="${mailingList.id}"/>
  <div class="grid-x grid-margin-x">
    <div class="cell medium-4">
      <label for="memberSearchName" class="show-for-sr">Search by name</label>
      <input id="memberSearchName" type="search" name="searchName" placeholder="Search by name..."<c:if test="${!empty searchName}"> value="<c:out value="${searchName}"/>"</c:if>>
    </div>
    <div class="cell medium-4">
      <label for="memberSearchEmail" class="show-for-sr">Search by email</label>
      <input id="memberSearchEmail" type="search" name="searchEmail" placeholder="Search by email..."<c:if test="${!empty searchEmail}"> value="<c:out value="${searchEmail}"/>"</c:if>>
    </div>
    <div class="cell medium-3">
      <label for="memberStatus" class="show-for-sr">Filter by status</label>
      <select id="memberStatus" name="status">
        <option value="">All statuses</option>
        <option value="active"<c:if test="${status eq 'active'}"> selected</c:if>>Active</option>
        <option value="pending"<c:if test="${status eq 'pending'}"> selected</c:if>>Pending Confirmation</option>
        <option value="unsubscribed"<c:if test="${status eq 'unsubscribed'}"> selected</c:if>>Unsubscribed</option>
        <option value="quarantined"<c:if test="${status eq 'quarantined'}"> selected</c:if>>Quarantined</option>
      </select>
    </div>
    <div class="cell medium-1">
      <button type="submit" class="button expanded">Search</button>
    </div>
  </div>
</form>
<table>
  <thead>
    <tr>
      <th>Name</th>
      <th>Email</th>
      <th>Status</th>
      <th>Location</th>
      <th width="120">IP Address</th>
      <th width="200">Added</th>
      <th width="100">Action</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${memberList}" var="member">
    <tr>
      <td>
        <c:out value="${member.firstName}" />
      </td>
      <td>
        <c:out value="${member.emailAddress}" />
        <c:if test="${!empty member.organization}">
          <br /><small class="subheader"><c:out value="${member.organization}" /></small>
        </c:if>
      </td>
      <td>
        <c:choose>
          <c:when test="${!empty member.quarantined}">
            <span class="label alert" title="Quarantined: <c:out value="${member.quarantineReason}"/>">Quarantined</span>
          </c:when>
          <c:when test="${!empty member.confirmToken}">
            <%-- Checked before "unsubscribed" -- a previously-unsubscribed member re-signing up
                 keeps their old unsubscribed timestamp until they actually reconfirm, so a live
                 confirm_token here means a genuine pending reconfirmation, not a stale unsubscribe. --%>
            <span class="label secondary" title="Sent a confirmation email, not yet clicked">Pending Confirmation</span>
          </c:when>
          <c:when test="${!empty member.unsubscribed}">
            <span class="label warning">Unsubscribed</span>
          </c:when>
          <c:when test="${!empty member.validationStatus && member.validationStatus ne 'valid'}">
            <span class="label warning" title="Deliverability: <c:out value="${member.validationStatus}"/>">Flagged</span>
          </c:when>
          <c:otherwise>
            <span class="label success">Active</span>
          </c:otherwise>
        </c:choose>
      </td>
      <td><small><c:out value="${geoip:location(member.ipAddress, '--')}"/></small></td>
      <td><small><c:out value="${empty member.ipAddress ? '--' : member.ipAddress}" /></small></td>
      <td><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${member.created}" /></td>
      <td>
        <a href="${widgetContext.uri}?command=delete&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&mailingListId=${mailingList.id}&emailId=${member.emailId}" data-confirm-href="Are you sure you want to remove <c:out value="${member.emailAddress}" />?" title="Remove member"><i class="fa fa-remove"></i></a>
        <c:if test="${!empty member.ipAddress}">
          <a href="#" data-confirm-post="Block IP <c:out value="${member.ipAddress}" />? This will prevent that IP from accessing the site. The member itself is not removed." data-post-url="${widgetContext.uri}?command=blockIP&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&mailingListId=${mailingList.id}&emailId=${member.emailId}" title="Block this IP"><i class="fa fa-ban"></i></a>
        </c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty memberList}">
      <tr>
        <td colspan="7"><c:choose><c:when test="${!empty searchName || !empty searchEmail || !empty status}">No members matched your search</c:when><c:otherwise>No members were found</c:otherwise></c:choose></td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control -- must carry every active search/filter param, or they silently reset on page 2+ --%>
<c:set var="recordPagingParams" scope="request">mailingListId=${mailingList.id}<c:if test="${!empty searchName}">&searchName=${url:encodeUri(searchName)}</c:if><c:if test="${!empty searchEmail}">&searchEmail=${url:encodeUri(searchEmail)}</c:if><c:if test="${!empty status}">&status=${url:encodeUri(status)}</c:if></c:set>
<%@include file="../paging_control.jspf" %>
<%-- No data-animation-in (issue #1320, same as #1318): Foundation's Motion-UI animateIn path
     leaves this display:none forever -- a CSS transition can't start on an element that's still
     display:none when the animation class is added, so the transitionend it waits for to reveal
     the element never fires. Omitting it uses Foundation's default, non-animated open. --%>
<div class="reveal small" id="formReveal" data-reveal data-close-on-click="false" role="dialog" aria-modal="true" aria-labelledby="mailingFormRevealTitle">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4 id="mailingFormRevealTitle">Add Email</h4>
  <form id="userForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- Form --%>
    <label>Email (required)
      <input type="email" placeholder="Email Address" name="email" value="" required>
    </label>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-5 cell">
        <label>First Name
          <input type="text" placeholder="First Name" name="firstName" value="">
        </label>
      </fieldset>
      <fieldset class="medium-7 cell">
        <label>Last Name
          <input type="text" placeholder="Last Name" name="lastName" value="" >
        </label>
      </fieldset>
    </div>
    <label>Organization
      <input type="text" placeholder="Organization" name="organization" value="">
    </label>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Save" />
    </div>
  </form>
</div>
<%@include file="../confirm_submit.jspf" %>
