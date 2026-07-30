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
<%@ taglib prefix="group" uri="/WEB-INF/tlds/group-functions.tld" %>
<%@ taglib prefix="geoip" uri="/WEB-INF/tlds/geoip-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="themePropertyMap" class="java.util.HashMap" scope="request"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="user" class="com.simisinc.platform.domain.model.User" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="userLogin" class="com.simisinc.platform.domain.model.login.UserLogin" scope="request"/>
<jsp:useBean id="passwordAgeSeverity" class="java.lang.String" scope="request"/>
<script nonce="${cspNonce}">
  function restoreAccount() {
    if (!confirm("Are you sure you want to RESTORE this user account?")) {
      return;
    }
    postAction('${widgetContext.uri}?action=restoreAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}');
  }
  function deleteAccount() {
    if (!confirm("Are you sure you want to DELETE this user account?")) {
      return;
    }
    postAction('${widgetContext.uri}?action=deleteAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}');
  }
  function unlockAccount() {
    if (!confirm("Are you sure you want to UNLOCK this user account? This clears the failed login attempts and lockout.")) {
      return;
    }
    postAction('${widgetContext.uri}?action=unlockAccount&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&userId=${user.id}');
  }
</script>
<div style="margin-top: 6px;background-color:<c:out value="${themePropertyMap['theme.body.backgroundColor']}" />;">
  <div class="button-container float-right">
    <a class="button small radius float-right" href="${ctx}/admin/capability-grants?userId=${user.id}">Capability Grants</a>
    <a class="button small radius float-right" href="${ctx}/admin/modify-user?userId=${user.id}">Modify User</a>
    <ul class="dropdown menu" style="padding-right: 15px;" data-dropdown-menu>
      <li>
        <a href="#">Actions</a>
        <ul class="menu">
          <c:if test="${user.enabled}">
            <li><a href="#" data-open="resetPasswordReveal">Reset Password</a></li>
            <li><a href="#" data-open="suspendAccountReveal">Suspend Account</a></li>
          </c:if>
          <%-- #492 Phase 3: an elevated-role account can't be reactivated by one admin acting
               alone -- Restore stays a direct one-click action for everyone else. --%>
          <c:if test="${!user.enabled && !isElevatedTarget}">
            <li><a href="javascript:restoreAccount()">Restore Account</a></li>
          </c:if>
          <c:if test="${!user.enabled && isElevatedTarget && empty pendingUnsuspendRequest}">
            <li><a href="#" data-open="requestUnsuspendReveal">Request Unsuspend&hellip;</a></li>
          </c:if>
          <c:if test="${!user.enabled && isElevatedTarget && !empty pendingUnsuspendRequest && pendingUnsuspendRequest.requestedBy ne currentUserId}">
            <li><a href="#" data-open="approveUnsuspendReveal">Approve Unsuspend Request</a></li>
            <li><a href="#" data-open="denyUnsuspendReveal">Deny Unsuspend Request</a></li>
          </c:if>
          <c:if test="${user.locked}">
            <li><a href="javascript:unlockAccount()">Unlock Account</a></li>
          </c:if>
          <li><a href="javascript:deleteAccount()">Delete Account</a></li>
        </ul>
      </li>
    </ul>
  </div>
  <h3>
    <c:out value="${user.fullName}" />
    <c:choose>
      <c:when test="${user.accountStatus eq 'suspended'}"><span class="label alert">Suspended</span></c:when>
      <c:when test="${user.accountStatus eq 'locked'}"><span class="label warning">Locked</span></c:when>
      <c:when test="${user.accountStatus eq 'inactive'}"><span class="label secondary">Inactive</span></c:when>
      <c:otherwise><span class="label success">Active</span></c:otherwise>
    </c:choose>
    <c:if test="${user.mfaEnabled}">
      <span class="label round success" title="MFA enabled"><i class="fa fa-shield-halved"></i> MFA</span>
    </c:if>
  </h3>
  <c:if test="${!user.enabled && isElevatedTarget && !empty pendingUnsuspendRequest}">
    <p>
      <span class="label warning">Unsuspend requested</span>
      by <c:out value="${pendingUnsuspendRequest.requestedByEmail}" />
      on <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${pendingUnsuspendRequest.requestedAt}" />
      &mdash; reason: <c:out value="${pendingUnsuspendRequest.reason}" />.
      <c:if test="${pendingUnsuspendRequest.requestedBy eq currentUserId}">
        Awaiting another administrator's review (separation of duties -- you can't approve your own request).
      </c:if>
    </p>
  </c:if>
  <c:if test="${!empty user.title || !empty user.city || !empty user.state}">
    <p>
      <c:if test="${!empty user.title}">
        <c:out value="${user.title}" /><br />
      </c:if>
      <c:if test="${!empty user.city || !empty state}">
        <small><i class="fa fa-map-marker"></i>
          <c:if test="${!empty user.city}"><c:out value="${user.city}" /></c:if>
          <c:if test="${!empty user.state}"><c:out value="${user.state}" /></c:if>
        </small>
      </c:if>
    </p>
  </c:if>
  <hr>
</div>
<%@include file="../page_messages.jspf" %>
<div class="grid-container">
<div class="grid-x">
  <div class="small-12 medium-6 large-4 cell">
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>First Name</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user.firstName}" />
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Last Name</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user.lastName}" />
      </div>
    </div>
    <c:if test="${!empty user.title}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Title</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.title}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.organization}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Organization</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.organization}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.department}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Department</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.department}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.nickname}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Community Nickname</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.nickname}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.email && fn:contains(user.email, '@')}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Email</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.email}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.city}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>City</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.city}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.state}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>State/Province</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.state}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.country}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Country</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.country}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.postalCode}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Postal Code</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.postalCode}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.timeZone}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Time Zone</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.timeZone}" />
        </div>
      </div>
    </c:if>
  </div>
  <div class="small-12 medium-6 large-4 cell">
    <c:if test="${user.email ne user.username}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Username</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.username}" />
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.roleList}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Roles</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:forEach items="${user.roleList}" var="userRole" varStatus="status">
            <span class="label"><c:out value="${userRole.title}" /></span>
          </c:forEach>
        </div>
      </div>
    </c:if>
    <c:if test="${!empty user.groupList}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Groups</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:forEach items="${user.groupList}" var="group" varStatus="status">
            <span class="label secondary"><c:out value="${group:name(group.id)}" /></span><c:if test="${!status.last}"><br /></c:if>
          </c:forEach>
        </div>
      </div>
    </c:if>
  </div>
  <div class="small-12 medium-6 large-4 cell">
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Validated</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:choose>
          <c:when test="${!empty user.validated}"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.validated}" /></c:when>
          <c:otherwise><span class="label warning">Not Validated</span></c:otherwise>
        </c:choose>
      </div>
    </div>
    <c:if test="${user.locked}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Locked Until</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.lockedUntil}" /> (${user.failedAttemptCount} failed attempts)
        </div>
      </div>
    </c:if>
    <c:if test="${!user.locked && user.failedAttemptCount gt 0}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Failed Logins</small>
        </div>
        <div class="small-8 align-self-middle cell">
          ${user.failedAttemptCount}
        </div>
      </div>
    </c:if>
    <c:if test="${!user.enabled && !empty user.suspensionReason}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Suspension Reason</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${user.suspensionReason}" />
        </div>
      </div>
    </c:if>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>MFA</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:choose>
          <c:when test="${user.mfaEnabled}"><span class="label success">Enabled</span></c:when>
          <c:otherwise><span class="label secondary">Not Enabled</span></c:otherwise>
        </c:choose>
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Password Changed</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:choose>
          <c:when test="${!empty user.lastPasswordChangedAt}">
            <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.lastPasswordChangedAt}" />
          </c:when>
          <c:otherwise>Never tracked</c:otherwise>
        </c:choose>
        <c:if test="${passwordAgeSeverity eq 'warning'}"> <span class="label warning">Aging</span></c:if>
        <c:if test="${passwordAgeSeverity eq 'critical'}"> <span class="label alert">Overdue</span></c:if>
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Created</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.created}" />
      </div>
    </div>
    <div class="grid-x grid-padding-x">
      <div class="small-4 text-right cell">
        <small>Created By</small>
      </div>
      <div class="small-8 align-self-middle cell">
        <c:out value="${user:name(user.createdBy)}"/>
      </div>
    </div>
    <c:if test="${user.created ne user.modified}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Modified</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.modified}" />
        </div>
      </div>
      <c:if test="${user.modifiedBy gt 0}">
        <div class="grid-x grid-padding-x">
          <div class="small-4 text-right cell">
            <small>Modified By</small>
          </div>
          <div class="small-8 align-self-middle cell">
            <c:out value="${user:name(user.modifiedBy)}"/>
          </div>
        </div>
      </c:if>
    </c:if>
    <c:if test="${!empty userLogin.created}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Last Login</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${userLogin.created}" />
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Last IP</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${userLogin.ipAddress}" />
        </div>
      </div>
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Geo IP</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:out value="${geoip:cityState(userLogin.ipAddress, '---')}"/>
        </div>
      </div>
    </c:if>
  </div>
</div>
</div>
<hr>
<p><a href="${ctx}/admin/users"><i class="fa fa-angle-double-left"></i> Back to list</a></p>
<div class="reveal" id="resetPasswordReveal" role="dialog" aria-modal="true" aria-labelledby="resetPasswordRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="resetPasswordRevealTitle">Reset Password</h4>
  <p>An email with password reset instructions will be sent to <strong><c:out value="${user.email}" /></strong>.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="resetPassword"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <div class="grid-x grid-padding-x">
      <div class="small-12 cell">
        <label for="resetStepUpCredential">Your password or authenticator code <span class="required">*</span>
          <input type="password" id="resetStepUpCredential" name="stepUpCredential" maxlength="255"
                 placeholder="Password or 6-digit code" required
                 title="Re-authentication required to reset another user's password"/>
        </label>
      </div>
    </div>
    <input type="submit" class="button warning radius" value="Send Reset Email"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="suspendAccountReveal" role="dialog" aria-modal="true" aria-labelledby="suspendAccountRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="suspendAccountRevealTitle">Suspend Account</h4>
  <p><strong><c:out value="${user.email}" /></strong> will no longer be able to sign in.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="suspendAccount"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <label for="suspendReason">Reason <span class="required">*</span>
      <textarea id="suspendReason" name="reason" maxlength="255" required
                placeholder="Why is this account being suspended?"></textarea>
    </label>
    <input type="submit" class="button alert radius" value="Suspend Account"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="requestUnsuspendReveal" role="dialog" aria-modal="true" aria-labelledby="requestUnsuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="requestUnsuspendRevealTitle">Request Unsuspend</h4>
  <p>
    <strong><c:out value="${user.email}" /></strong> holds a role that requires a second
    administrator's review before it can be reactivated. This creates a request; eligible
    admins/community-managers (other than you) will be notified and can approve or deny it.
  </p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="restoreAccount"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <label for="requestUnsuspendReason">Reason <span class="required">*</span>
      <textarea id="requestUnsuspendReason" name="reason" maxlength="255" required
                placeholder="Why should this account be unsuspended?"></textarea>
    </label>
    <input type="submit" class="button warning radius" value="Request Unsuspend"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<c:if test="${!empty pendingUnsuspendRequest}">
<div class="reveal" id="approveUnsuspendReveal" role="dialog" aria-modal="true" aria-labelledby="approveUnsuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="approveUnsuspendRevealTitle">Approve Unsuspend Request</h4>
  <p>
    <strong><c:out value="${user.email}" /></strong> will be restored, and its current password will
    stop working immediately -- an email will be sent asking the account holder to set a new one
    before they can sign in again.
  </p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="approveUnsuspend"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <input type="hidden" name="requestId" value="${pendingUnsuspendRequest.id}"/>
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
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="denyUnsuspend"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <input type="hidden" name="requestId" value="${pendingUnsuspendRequest.id}"/>
    <label for="denyUnsuspendReason">Reason <span class="required">*</span>
      <textarea id="denyUnsuspendReason" name="denialReason" maxlength="255" required
                placeholder="Why is this request being denied?"></textarea>
    </label>
    <input type="submit" class="button alert radius" value="Deny"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
</c:if>