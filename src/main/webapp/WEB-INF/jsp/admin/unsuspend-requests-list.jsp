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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="requestList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="statusFilter" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="h1"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<p class="help-text">
  Accounts holding an elevated role (community-manager and above) can't be reactivated by one
  administrator acting alone (issue #492) -- a request here must be reviewed and approved by a
  <em>different</em> administrator before the account is restored, and approval immediately
  invalidates its current password: the account holder must set a new one via an emailed link
  before they can sign in again. Every request, approval, and denial is recorded in the
  <a href="${ctx}/admin/audit-log">audit log</a>.
</p>
<%@include file="../page_messages.jspf" %>
<form method="get" autocomplete="off" class="float-right">
  <label for="statusFilter" class="show-for-sr">Status</label>
  <select id="statusFilter" name="statusFilter" class="float-left width-auto">
    <option value="pending"<c:if test="${statusFilter eq 'pending'}"> selected</c:if>>Pending</option>
    <option value="approved"<c:if test="${statusFilter eq 'approved'}"> selected</c:if>>Approved</option>
    <option value="reverified"<c:if test="${statusFilter eq 'reverified'}"> selected</c:if>>Reverified</option>
    <option value="denied"<c:if test="${statusFilter eq 'denied'}"> selected</c:if>>Denied</option>
    <option value="superseded"<c:if test="${statusFilter eq 'superseded'}"> selected</c:if>>Superseded</option>
    <option value=""<c:if test="${empty statusFilter}"> selected</c:if>>Any Status</option>
  </select>
</form>
<script nonce="${cspNonce}">
  document.getElementById("statusFilter").onchange = function() {
    this.form.submit();
  }
</script>
<table class="unstriped stack">
  <thead>
    <tr>
      <th>Account</th>
      <th>Requested By</th>
      <th>Reason</th>
      <th width="140">Requested</th>
      <th width="100">Status</th>
      <th width="160">Actions</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${requestList}" var="record">
    <tr>
      <td><a href="${ctx}/admin/user-details?userId=${record.targetUserId}"><c:out value="${record.targetEmail}" /></a></td>
      <td><c:out value="${record.requestedByEmail}" /></td>
      <td nowrap="true"><small<c:if test="${fn:length(record.reason) > 40}"> title="${fn:escapeXml(record.reason)}"</c:if>><c:out value="${text:trim(record.reason, 40, true)}" /></small></td>
      <td nowrap="true"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${record.requestedAt}" /></td>
      <td nowrap="true">
        <c:choose>
          <c:when test="${record.status eq 'pending'}"><span class="label warning">Pending</span></c:when>
          <c:when test="${record.status eq 'approved'}"><span class="label success">Approved</span></c:when>
          <c:when test="${record.status eq 'reverified'}"><span class="label round success">Reverified</span></c:when>
          <c:when test="${record.status eq 'denied'}"><span class="label alert">Denied</span></c:when>
          <c:when test="${record.status eq 'superseded'}"><span class="label secondary">Superseded</span></c:when>
          <c:otherwise><span class="label secondary"><c:out value="${record.status}" /></span></c:otherwise>
        </c:choose>
      </td>
      <td nowrap="true">
        <c:if test="${record.status eq 'pending' && record.requestedBy ne currentUserId}">
          <a href="#" class="unsuspendApproveBtn" data-request-id="${record.id}" data-target-email="${fn:escapeXml(record.targetEmail)}">Approve</a>
          <a href="#" class="unsuspendDenyBtn" data-request-id="${record.id}" data-target-email="${fn:escapeXml(record.targetEmail)}">Deny</a>
        </c:if>
        <c:if test="${record.status eq 'pending' && record.requestedBy eq currentUserId}">
          <small><em>Awaiting another admin's review</em></small>
        </c:if>
        <c:if test="${record.status eq 'denied' && !empty record.decisionReason}">
          <small title="${fn:escapeXml(record.decisionReason)}">Denied by <c:out value="${record.decidedByEmail}" /></small>
        </c:if>
        <c:url var="historyUrl" value="/admin/audit-log">
          <c:param name="targetType" value="user"/>
          <c:param name="targetLabel" value="${record.targetEmail}"/>
        </c:url>
        <a href="${historyUrl}" title="View audit history for this account">History</a>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty requestList}">
      <tr>
        <td colspan="6">No requests were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<c:set var="recordPagingParams" scope="request" value="statusFilter=${statusFilter}"/>
<%@include file="../paging_control.jspf" %>
<div class="reveal" id="approveUnsuspendReveal" role="dialog" aria-modal="true" aria-labelledby="approveUnsuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="approveUnsuspendRevealTitle">Approve Unsuspend Request</h4>
  <p>
    <strong id="approveUnsuspendTarget"></strong> will be restored, and its current password will
    stop working immediately -- an email will be sent asking the account holder to set a new one
    before they can sign in again.
  </p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="approve"/>
    <input type="hidden" name="requestId" id="approveUnsuspendRequestId" value=""/>
    <label for="approveUnsuspendStepUpCredential">Your password or authenticator code <span class="required">*</span>
      <input type="password" id="approveUnsuspendStepUpCredential" name="stepUpCredential" maxlength="255"
             placeholder="Password or 6-digit code" required
             title="Re-authentication required to approve an unsuspend request"/>
    </label>
    <input type="submit" class="button warning radius" value="Approve"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="denyUnsuspendReveal" role="dialog" aria-modal="true" aria-labelledby="denyUnsuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="denyUnsuspendRevealTitle">Deny Unsuspend Request</h4>
  <p>Denies the request for <strong id="denyUnsuspendTarget"></strong>.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="deny"/>
    <input type="hidden" name="requestId" id="denyUnsuspendRequestId" value=""/>
    <label for="denyUnsuspendReasonInput">Reason <span class="required">*</span>
      <textarea id="denyUnsuspendReasonInput" name="denialReason" maxlength="255" required
                placeholder="Why is this request being denied?"></textarea>
    </label>
    <input type="submit" class="button alert radius" value="Deny"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    $('.unsuspendApproveBtn').on('click', function (e) {
      e.preventDefault();
      var $btn = $(this);
      $('#approveUnsuspendRequestId').val($btn.data('request-id'));
      $('#approveUnsuspendTarget').text($btn.data('target-email'));
      $('#approveUnsuspendReveal').foundation('open');
    });
    $('.unsuspendDenyBtn').on('click', function (e) {
      e.preventDefault();
      var $btn = $(this);
      $('#denyUnsuspendRequestId').val($btn.data('request-id'));
      $('#denyUnsuspendTarget').text($btn.data('target-email'));
      $('#denyUnsuspendReveal').foundation('open');
    });
  })();
</script>
