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
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="targetUser" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="capabilityList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="grantList" class="java.util.ArrayList" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<h4><c:out value="${targetUser.fullName}" /></h4>
<p class="help-text">Direct grants give this specific user a capability without changing their role - useful for a
  temporary need (set an expiration) or a one-off exception (leave it permanent). This user's effective capabilities
  are the union of what their role(s) grant (see <a href="${ctx}/admin/role-capabilities">Role Capabilities</a>) and
  any active grants listed below - a direct grant can only add access on top of a role; it can never subtract from
  or override what a role already provides. Every grant or revoke here is recorded in the platform's audit log. A
  session already logged in as this user won't see the change take effect until they log in again - tell them to
  log out and back in if the change needs to apply now.</p>
<%@include file="../page_messages.jspf" %>
<form method="post">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="command" value="add"/>
  <input type="hidden" name="userId" value="${targetUser.id}"/>
  <div class="grid-x grid-padding-x">
    <div class="cell medium-4">
      <label>Capability
        <select name="capabilityId" required>
          <option value="">Select...</option>
          <c:forEach items="${capabilityList}" var="capability">
            <option value="${capability.id}"><c:out value="${capability.code}" /> - <c:out value="${capability.description}" /></option>
          </c:forEach>
        </select>
      </label>
    </div>
    <div class="cell medium-3">
      <label>Expires (optional)
        <input type="date" name="expiresAt">
      </label>
    </div>
    <div class="cell medium-5">
      <label>Reason (required)
        <input type="text" name="reason" placeholder="Why is this being granted?" required>
      </label>
    </div>
  </div>
  <p class="help-text">Only capabilities that already exist in the system appear in the list above, and only one
    active grant of a given capability is allowed per user at a time - granting a second one while the first is
    still active is refused with a message telling you to revoke the first rather than silently overwriting it. If
    you set an expiration date, re-open this page afterward and confirm the Expires column below shows the date you
    intended; a malformed date is rejected with an error rather than silently creating a permanent grant, but it's
    still worth double-checking.</p>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Grant"/>
  </div>
</form>
<table class="unstriped stack">
  <thead>
    <tr>
      <th>Capability</th>
      <th>Granted</th>
      <th>Granted By</th>
      <th>Reason</th>
      <th>Expires</th>
      <th>Status</th>
      <th width="70">History</th>
      <th width="70">Revoke</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${grantList}" var="grant">
      <tr>
        <td><code><c:out value="${grant.capabilityCode}" /></code></td>
        <td><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${grant.granted}" /></td>
        <td><c:out value="${user:name(grant.grantedBy)}" /></td>
        <td><c:out value="${grant.reason}" /></td>
        <td>
          <c:choose>
            <c:when test="${!empty grant.expiresAt}"><fmt:formatDate pattern="yyyy-MM-dd" value="${grant.expiresAt}" /></c:when>
            <c:otherwise><small class="subheader">Never</small></c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:choose>
            <c:when test="${!empty grant.revokedAt}"><span class="label alert">Revoked</span></c:when>
            <c:otherwise><span class="label success">Active</span></c:otherwise>
          </c:choose>
        </td>
        <td>
          <c:url var="historyUrl" value="/admin/audit-log">
            <c:param name="targetType" value="capability_grant"/>
            <c:param name="targetLabel" value="${targetUser.username}"/>
          </c:url>
          <a href="${historyUrl}" title="View audit history for this user's grants">History</a>
        </td>
        <td>
          <c:if test="${empty grant.revokedAt}">
            <a href="#" title="Revoke this grant" onclick="openRevokeCapabilityGrantReveal(${grant.id}); return false;"><i class="fa fa-trash"></i></a>
          </c:if>
        </td>
      </tr>
    </c:forEach>
    <c:if test="${empty grantList}">
      <tr>
        <td colspan="8">No direct grants for this user</td>
      </tr>
    </c:if>
  </tbody>
</table>
<p><a href="${ctx}/admin/user-details?userId=${targetUser.id}"><i class="fa fa-angle-double-left"></i> Back to user</a></p>
<div class="reveal" id="revokeCapabilityGrantReveal" role="dialog" aria-modal="true" aria-labelledby="revokeCapabilityGrantTitle"
     data-reveal data-close-on-click="true">
  <h4 id="revokeCapabilityGrantTitle">Revoke Capability Grant</h4>
  <p class="help-text">Revoking an "admin:manage" grant is refused if no one would be left effectively holding it -
    via any role or any other active direct grant. Every other capability has no such check and can be revoked with
    no warning even if this is its last holder; confirm some other route to that functionality exists first.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="revoke"/>
    <input type="hidden" name="userId" value="${targetUser.id}"/>
    <input type="hidden" name="capabilityGrantId" id="revokeCapabilityGrantId" value=""/>
    <label>Reason (required)
      <input type="text" name="reason" placeholder="Why is this being revoked?" required>
    </label>
    <input type="submit" class="button radius alert" value="Revoke"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  function openRevokeCapabilityGrantReveal(capabilityGrantId) {
    document.getElementById('revokeCapabilityGrantId').value = capabilityGrantId;
    $('#revokeCapabilityGrantReveal').foundation('open');
  }
</script>
