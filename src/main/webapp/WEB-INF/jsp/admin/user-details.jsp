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
<jsp:useBean id="accountLinkState" class="java.lang.String" scope="request"/>
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
    <c:if test="${userSession.hasRole('admin')}">
      <a class="button small radius float-right" href="${ctx}/admin/capability-grants?userId=${user.id}">Capability Grants</a>
    </c:if>
    <a class="button small radius float-right" href="${ctx}/admin/modify-user?userId=${user.id}">Modify User</a>
    <ul class="dropdown menu" style="padding-right: 15px;" data-dropdown-menu>
      <li>
        <a href="#">Actions</a>
        <ul class="menu">
          <c:if test="${user.enabled}">
            <li><a href="#" data-open="resetPasswordReveal">Reset Password</a></li>
            <li><a href="#" data-open="suspendAccountReveal">Suspend Account</a></li>
          </c:if>
          <c:if test="${user.mfaEnabled}">
            <li><a href="#" data-open="resetMfaReveal">Reset MFA</a></li>
          </c:if>
          <%-- #492 Phase 3: an elevated-role account can't be reactivated by one admin acting
               alone -- Restore stays a direct one-click action for everyone else. --%>
          <c:if test="${!user.enabled && !isElevatedTarget}">
            <li><a href="#" data-js-call="restoreAccount">Restore Account</a></li>
          </c:if>
          <c:if test="${!user.enabled && isElevatedTarget && empty pendingUnsuspendRequest}">
            <li><a href="#" data-open="requestUnsuspendReveal">Request Unsuspend&hellip;</a></li>
          </c:if>
          <c:if test="${!user.enabled && isElevatedTarget && !empty pendingUnsuspendRequest && pendingUnsuspendRequest.requestedBy ne currentUserId}">
            <li><a href="#" data-open="approveUnsuspendReveal">Approve Unsuspend Request</a></li>
            <li><a href="#" data-open="denyUnsuspendReveal">Deny Unsuspend Request</a></li>
          </c:if>
          <c:if test="${user.locked}">
            <li><a href="#" data-js-call="unlockAccount">Unlock Account</a></li>
          </c:if>
          <li><a href="#" data-js-call="deleteAccount">Delete Account</a></li>
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
<div class="callout primary radius">
  <p style="margin-bottom:0">
    Full detail and every single-account action for this one user. Bulk equivalents (suspend,
    unsuspend, reset password, grant a role) live on the <a href="${ctx}/admin/users">Users list</a>;
    a few actions here -- Delete, Unlock, Reset MFA, and approving/denying an unsuspend request --
    don't exist there at all.
  </p>
</div>
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
            <%-- Same privilege-ladder colours as /admin/users, so a role reads the same on both --%>
            <span class="label ${user:roleTierClass(userRole.level)}"><c:out value="${userRole.title}" /></span>
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
    <%-- #1836: only one setup/reset link can exist per account, so knowing whether one is
         outstanding decides whether to reissue (which invalidates it) or chase the email. --%>
    <c:if test="${accountLinkState eq 'outstanding' or accountLinkState eq 'expired'}">
      <div class="grid-x grid-padding-x">
        <div class="small-4 text-right cell">
          <small>Setup Link</small>
        </div>
        <div class="small-8 align-self-middle cell">
          <c:choose>
            <c:when test="${accountLinkState eq 'expired'}">
              <span class="label warning">Expired</span>
              <c:if test="${!empty user.accountTokenExpires}">
                <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.accountTokenExpires}" />
              </c:if>
            </c:when>
            <c:otherwise>
              <span class="label success">Outstanding</span>
              <c:if test="${!empty user.accountTokenExpires}">
                until <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.accountTokenExpires}" />
              </c:if>
              <%-- Revealing the link is an explicit, audited action behind a step-up -- it hands over a
                   working credential for this account, so it is never rendered on page load. --%>
              <br />
              <a data-open="revealSetupLinkReveal">Show setup link</a>
            </c:otherwise>
          </c:choose>
        </div>
      </div>
    </c:if>
    <%-- Rendered only in the response to a successful reveal; a reload does not bring it back. --%>
    <c:if test="${!empty setupLink}">
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <div class="callout warning">
            <p><strong>Send this to <c:out value="${user.email}" /> directly.</strong> It sets the account's
              password, so treat it like one: send it over a channel you trust, and do not post it anywhere
              shared. It stops working once used, once it expires, or as soon as a new one is issued.</p>
            <label for="setupLinkValue">Setup link
              <input type="text" id="setupLinkValue" value="${fn:escapeXml(setupLink)}" readonly
                     class="select-on-focus" />
            </label>
            <button type="button" class="button primary radius copy-button" data-copy-target="setupLinkValue">
              Copy link
            </button>
          </div>
        </div>
      </div>
    </c:if>
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

<h5>Actions, explained</h5>
<ul>
  <li><strong>Reset Password</strong> emails the account a password-reset link -- it doesn't set or
    reveal a password directly. Sending it requires you (the admin) to re-enter your own password or
    authenticator code first, and you can't reset the password for an account with a higher role
    level than yours -- re-authenticating proves who you are, not which accounts you may act on.</li>
  <li><strong>Suspend Account</strong> immediately blocks sign-in. The modal marks a reason as
    required, but that's enforced by the form, not the server. You can't suspend your own account, or
    one with a higher role level than yours -- an explicit error message says so if you try, e.g. from
    a stale page or a shared link.</li>
  <li><strong>Reset MFA</strong> only appears once the account actually has MFA enabled. It clears
    that account's second factor and any unused recovery codes immediately -- the account holder has
    to re-enroll from scratch, so it is the recovery path for someone who has lost their authenticator
    device. Like Reset Password, it requires you to re-enter your own password or authenticator code
    first, and you can't reset MFA for an account with a higher role level than yours.</li>
  <li><strong>Restore Account / Request Unsuspend&hellip;</strong> -- which one you see depends on the
    target's role. A non-elevated account restores in one click. A community-manager-or-above account
    instead requires a second, <em>different</em> admin's approval -- filing the request notifies
    other eligible admins, and you can't also approve your own request. Neither path reaches an
    account with a higher role level than yours: that is refused outright, not queued for a second
    admin to review.</li>
  <li><strong>Approve Unsuspend Request / Deny Unsuspend Request</strong> only appear when a request is
    pending <em>and</em> it was filed by someone else. Approving requires your own step-up
    re-authentication, restores the account, and immediately invalidates its password -- the account
    holder gets an email to set a new one before they can sign in again. Denying just requires a
    reason and leaves the account suspended. You can't approve unsuspending an account with a role
    above your own level -- denying one isn't restricted that way, because a denial leaves the
    account suspended either way and so can't lift a control on an account that outranks you.</li>
  <li><strong>Unlock Account</strong> only appears once the account is actually locked (too many failed
    sign-in attempts). It clears the failed-attempt counter and lockout timer only -- it does not
    touch the password, MFA, or suspension status. You can't unlock an account with a higher role
    level than yours: the lockout is a security control on that account, so clearing it is a change
    to the account, not a favour to its owner.</li>
  <li><strong>Delete Account</strong> is permanent, with no confirmation beyond the browser's own "Are
    you sure?" prompt. It fails safely, with an explicit error rather than a partial delete, if the
    account is still referenced elsewhere in the database (it authored content, owns uploaded files,
    etc. -- see "Common problems" below). You can't delete your own account, or one with a higher role
    level than yours -- an explicit error message says so if you try.</li>
</ul>

<script src="${ctx}/javascript/copy-button.js"></script>

<h5>Reading the detail grid</h5>
<ul>
  <li><strong>Validated</strong> shows when the account's invitation or password-reset link was
    actually used. "Not Validated" means that step has never happened and the account can't sign in
    yet, regardless of what the Status badge next to the name says.</li>
  <li><strong>Show setup link</strong> reveals the working link itself so you can deliver it by hand
    when email is not reaching someone. It re-authenticates you first and records the reveal in the
    audit log, because that link sets the account's password. Unlike Reset Password it changes
    nothing -- anything already sent keeps working.</li>
  <li><strong>Setup Link</strong> appears only while an unused invitation or password-reset link
    exists. An account holds <em>one</em> link at a time, so "Reset Password" does not send a second
    copy -- it replaces the link, and the previously emailed one stops working immediately. Check
    this field before reissuing: if it says <span class="label success">Outstanding</span>, a
    working link is already in that person's inbox, and reissuing while they are mid-click is what
    makes activation appear to fail repeatedly.</li>
  <li><strong>Password Changed</strong> gets an <span class="label warning">Aging</span> or
    <span class="label alert">Overdue</span> badge once it passes the site's configured password-age
    threshold (Overdue at twice that threshold). An account whose password change was never tracked
    is always shown as Overdue -- there's no way to distinguish "recently created" from "ancient,
    unmonitored password" from this field alone.</li>
  <li><strong>MFA</strong> here is a status display only -- to clear an enrollment, use the
    <strong>Reset MFA</strong> action above, which appears in the Actions menu only while this field
    shows MFA as enabled.</li>
</ul>

<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>"You cannot suspend/restore/delete an account with a higher role level than your
    own."</strong> A community-manager can act on ordinary users but not on admins (or another account
    holding a higher-level custom role) -- enforced here even if the action is reached via a
    bookmarked link. The same check covers Suspend Account, Restore, Delete Account and Reset MFA,
    each phrasing the message in terms of the action you tried.</li>
  <li><strong>Restore doesn't take effect immediately.</strong> Expected for a community-manager-and-
    above account -- it needs a second, different administrator's approval. Check who requested it in
    the banner at the top of this page, or on <a href="${ctx}/admin/unsuspend-requests">Unsuspend
    Requests</a>.</li>
  <li><strong>A user is locked out of MFA</strong> (lost their device, no backup codes left). Use
    <strong>Reset MFA</strong> in the Actions menu, then have them re-enroll -- it clears the second
    factor and any unused recovery codes in one step.</li>
  <li><strong>Delete failed with "referenced in other tables."</strong> The account owns something
    else in the system (content, files, form submissions, etc.) that deleting it outright would
    orphan. Suspending instead of deleting is usually the safer option here.</li>
</ul>

<div class="reveal" id="resetPasswordReveal" role="dialog" aria-modal="true" aria-labelledby="resetPasswordRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="resetPasswordRevealTitle">Reset Password</h4>
  <p>An email with password reset instructions will be sent to <strong><c:out value="${user.email}" /></strong>.</p>
  <%-- #1836: warn BEFORE the admin commits, not after. An account holds one link at a time, so
       sending this one stops the outstanding link working -- including one being clicked right now. --%>
  <c:if test="${accountLinkState eq 'outstanding'}">
    <div class="callout warning">
      This account already has a working setup link<c:if test="${!empty user.accountTokenExpires}">, valid
      until <fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.accountTokenExpires}" /></c:if>.
      Sending a new one immediately stops that link working. If they are partway through using it,
      this will interrupt them -- check whether they simply need the email resent to a reachable
      address before reissuing.
    </div>
  </c:if>
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
<div class="reveal" id="revealSetupLinkReveal" role="dialog" aria-modal="true"
     aria-labelledby="revealSetupLinkRevealTitle" data-reveal data-close-on-click="true">
  <h4 id="revealSetupLinkRevealTitle">Show setup link</h4>
  <p>This shows the working link for <strong><c:out value="${user.email}" /></strong> so you can send it
    yourself -- useful when email is not reaching them. <strong>It sets their password, so treat it like
    one.</strong> Showing it is recorded in the audit log.</p>
  <p>This does not change or replace the link, so anything already sent keeps working.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="revealSetupLink"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <div class="grid-x grid-padding-x">
      <div class="small-12 cell">
        <label for="revealStepUpCredential">Your password or authenticator code <span class="required">*</span>
          <input type="password" id="revealStepUpCredential" name="stepUpCredential" maxlength="255"
                 placeholder="Password or 6-digit code" required
                 title="Re-authentication required to show another user's setup link"/>
        </label>
      </div>
    </div>
    <input type="submit" class="button primary radius" value="Show Link"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="resetMfaReveal" role="dialog" aria-modal="true" aria-labelledby="resetMfaRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="resetMfaRevealTitle">Reset MFA</h4>
  <p>This immediately clears <strong><c:out value="${user.email}" /></strong>'s second factor and recovery codes.
    They will need to re-enroll from scratch. Use this to recover an account that has lost its authenticator
    device and exhausted its recovery codes.</p>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="action" value="resetMfa"/>
    <input type="hidden" name="userId" value="${user.id}"/>
    <div class="grid-x grid-padding-x">
      <div class="small-12 cell">
        <label for="resetMfaStepUpCredential">Your password or authenticator code <span class="required">*</span>
          <input type="password" id="resetMfaStepUpCredential" name="stepUpCredential" maxlength="255"
                 placeholder="Password or 6-digit code" required
                 title="Re-authentication required to reset another user's MFA"/>
        </label>
      </div>
    </div>
    <input type="submit" class="button warning radius" value="Reset MFA"/>
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