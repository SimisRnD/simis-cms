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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="userList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="roleList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="groupList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<jsp:useBean id="query" class="java.lang.String" scope="request"/>
<jsp:useBean id="statusFilter" class="java.lang.String" scope="request"/>
<jsp:useBean id="mfaFilter" class="java.lang.String" scope="request"/>
<jsp:useBean id="agingPasswordFilter" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h1><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h1>
</c:if>
<%@include file="../page_messages.jspf" %>
<div class="callout primary radius">
  <p style="margin-bottom:0">
    Every account on the site: search and filter, add one at a time or in bulk, and act on many at
    once (suspend, unsuspend, reset password, grant a role). Click a name to open that account's
    <a href="${ctx}/admin/user-details">full detail page</a>, including its own actions (delete,
    unlock, approve/deny an unsuspend request). Reachable by <strong>admin</strong> and
    <strong>community-manager</strong> -- a community-manager has everything here except granting a
    role above their own level, and the guardrails below apply the same way to both.
  </p>
</div>
<c:if test="${pendingUnsuspendRequestCount gt 0}">
  <div class="callout warning radius">
    <a href="${ctx}/admin/unsuspend-requests">
      <c:out value="${pendingUnsuspendRequestCount}" />
      unsuspend request<c:if test="${pendingUnsuspendRequestCount ne 1}">s</c:if> awaiting review &rarr;
    </a>
  </div>
</c:if>
<button class="button small primary radius float-left" data-open="formReveal"><i class="fa fa-plus"></i> New User</button>
<form id="fileForm" method="post" enctype="multipart/form-data" class="float-left">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="command" value="uploadCSVFile" />
  <label for="file" class="button small secondary radius margin-left-10"><i class="fa fa-upload"></i> Upload CSV File</label>
  <input type="file" id="file" name="file" accept="text/csv" class="show-for-sr">
</form>
<script nonce="${cspNonce}">
    document.getElementById("file").onchange = function() {
        document.getElementById("fileForm").submit();
    }
</script>
<%-- Export: same filter criteria as the results below (mirrored as hidden fields since export is a POST) --%>
<form method="post" autocomplete="off" class="float-left">
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <input type="hidden" name="command" value="downloadCSVFile"/>
  <input type="hidden" name="query" value="<c:out value='${query}'/>"/>
  <input type="hidden" name="statusFilter" value="<c:out value='${statusFilter}'/>"/>
  <input type="hidden" name="mfaFilter" value="<c:out value='${mfaFilter}'/>"/>
  <input type="hidden" name="agingPasswordFilter" value="<c:out value='${agingPasswordFilter}'/>"/>
  <button type="submit" class="button small secondary radius margin-left-10"><i class="fa fa-download"></i> Download CSV</button>
</form>
<form id="tableOptionsForm" method="get" autocomplete="off" class="float-right">
  <label for="statusFilter" class="show-for-sr">Status</label>
  <select id="statusFilter" name="statusFilter" class="float-left width-auto margin-right-10">
    <option value="any"<c:if test="${statusFilter eq 'any'}"> selected</c:if>>Any Status</option>
    <option value="active"<c:if test="${statusFilter eq 'active'}"> selected</c:if>>Active</option>
    <option value="suspended"<c:if test="${statusFilter eq 'suspended'}"> selected</c:if>>Suspended</option>
    <option value="locked"<c:if test="${statusFilter eq 'locked'}"> selected</c:if>>Locked</option>
    <option value="inactive"<c:if test="${statusFilter eq 'inactive'}"> selected</c:if>>Inactive (not yet verified)</option>
  </select>
  <label for="mfaFilter" class="show-for-sr">MFA</label>
  <select id="mfaFilter" name="mfaFilter" class="float-left width-auto margin-right-10">
    <option value="any"<c:if test="${mfaFilter eq 'any'}"> selected</c:if>>Any MFA</option>
    <option value="enabled"<c:if test="${mfaFilter eq 'enabled'}"> selected</c:if>>MFA Enabled</option>
    <option value="disabled"<c:if test="${mfaFilter eq 'disabled'}"> selected</c:if>>MFA Not Enabled</option>
  </select>
  <label class="float-left width-auto margin-right-10" style="line-height: 2.4375rem;">
    <input id="agingPasswordFilter" type="checkbox" name="agingPasswordFilter" value="1"<c:if test="${agingPasswordFilter eq '1'}"> checked</c:if> />
    Aging passwords
  </label>
  <div class="input-group no-gap width-auto">
    <input class="input-group-field" type="search" name="query" aria-label="Search users" placeholder="<c:if test="${empty query}">Search...</c:if>"<c:if test="${!empty query}"> value="<c:out value="${query}"/>"</c:if> autocomplete="off">
    <div class="input-group-button">
      <button type="submit" class="button search" aria-label="Search"><i class="fa fa-search" aria-hidden="true"></i></button>
    </div>
  </div>
</form>
<script nonce="${cspNonce}">
  document.getElementById("statusFilter").onchange = function() {
    document.getElementById("tableOptionsForm").submit();
  }
  document.getElementById("mfaFilter").onchange = function() {
    document.getElementById("tableOptionsForm").submit();
  }
  document.getElementById("agingPasswordFilter").onchange = function() {
    document.getElementById("tableOptionsForm").submit();
  }
</script>
<div id="bulkActionsBar" class="callout radius" style="display:none;padding:10px 15px;margin-bottom:10px;">
  <span id="bulkSelectedCount"></span>
  <button type="button" class="button tiny radius" id="bulkAssignRolesBtn">Assign Roles</button>
  <button type="button" class="button tiny radius" id="bulkResetPasswordBtn">Reset Password</button>
  <button type="button" class="button tiny alert radius" id="bulkSuspendBtn">Suspend</button>
  <button type="button" class="button tiny radius" id="bulkUnsuspendBtn">Unsuspend</button>
</div>
<table class="unstriped">
  <thead>
    <tr>
      <th width="24"><input type="checkbox" id="selectAllUsers" aria-label="Select all users on this page"></th>
      <th>Name</th>
      <th>Email</th>
      <th>Role</th>
      <th width="90">Status</th>
      <th width="50">MFA</th>
      <th width="200">Last Login</th>
    </tr>
  </thead>
  <tbody>
    <c:forEach items="${userList}" var="user">
    <tr>
      <td><input type="checkbox" class="userRowCheckbox" value="${user.id}" data-name="${fn:escapeXml(user.fullName)}" data-email="${fn:escapeXml(user.email)}" aria-label="Select ${fn:escapeXml(user.fullName)}"></td>
      <td>
        <a href="${ctx}/admin/user-details?userId=${user.id}"><c:out value="${user.fullName}" /></a>
        <c:if test="${!empty user.organization}">
          <br /><small class="subheader"><c:out value="${user.organization}" /></small>
        </c:if>
      </td>
      <td><c:out value="${user.email}" /></td>
      <td>
        <c:forEach items="${user.roleList}" var="userRole">
          <span class="label round"><c:out value="${userRole.code}" /></span>
        </c:forEach>
      </td>
      <td class="text-center">
        <c:choose>
          <c:when test="${user.accountStatus eq 'suspended'}"><span class="label alert" title="${fn:escapeXml(user.suspensionReason)}">Suspended</span></c:when>
          <c:when test="${user.accountStatus eq 'locked'}"><span class="label warning">Locked</span></c:when>
          <c:when test="${user.accountStatus eq 'inactive'}"><span class="label secondary">Inactive</span></c:when>
          <c:otherwise><span class="label success">Active</span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <c:choose>
          <c:when test="${user.mfaEnabled}"><span class="label round success" title="MFA enabled"><i class="fa fa-check"></i></span></c:when>
          <c:otherwise><span class="label round secondary" title="MFA not enabled"><i class="fa fa-times"></i></span></c:otherwise>
        </c:choose>
      </td>
      <td class="text-center">
        <c:if test="${!empty user.lastLogin}"><fmt:formatDate pattern="yyyy-MM-dd hh:mm a" value="${user.lastLogin.created}" /></c:if>
      </td>
    </tr>
    </c:forEach>
    <c:if test="${empty userList}">
      <tr>
        <td colspan="7">No users were found</td>
      </tr>
    </c:if>
  </tbody>
</table>
<%-- Paging Control --%>
<c:set var="recordPagingParams" scope="request"
       value="statusFilter=${statusFilter}&mfaFilter=${mfaFilter}&agingPasswordFilter=${agingPasswordFilter}"/>
<%@include file="../paging_control.jspf" %>
<%-- Bulk action reveal modals -- selection is scoped to the current page only (see the JS below);
     each is populated at open time with the live selection (see the JS below), not just a count. --%>
<div class="reveal" id="bulkSuspendReveal" role="dialog" aria-modal="true" aria-labelledby="bulkSuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkSuspendRevealTitle">Suspend <span id="bulkSuspendCount">0</span> Account(s)</h4>
  <p id="bulkSuspendSelfNotice" style="display:none;"><em>Your own account is selected and will be skipped.</em></p>
  <ul id="bulkSuspendList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkSuspend"/>
    <label for="bulkSuspendReason">Reason <span class="required">*</span>
      <textarea id="bulkSuspendReason" name="reason" maxlength="255" required
                placeholder="Why are these accounts being suspended?"></textarea>
    </label>
    <input type="submit" class="button alert radius" value="Suspend Accounts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkUnsuspendReveal" role="dialog" aria-modal="true" aria-labelledby="bulkUnsuspendRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkUnsuspendRevealTitle">Unsuspend <span id="bulkUnsuspendCount">0</span> Account(s)</h4>
  <p class="help-text">
    Accounts holding an elevated role (community-manager and above) can't be reactivated by one
    admin acting alone -- those will be filed as requests for a second administrator to review
    instead of being restored directly. A reason is only required if any selected account is elevated.
  </p>
  <ul id="bulkUnsuspendList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkUnsuspend"/>
    <label for="bulkUnsuspendReason">Reason (required only if any selected account is elevated)
      <textarea id="bulkUnsuspendReason" name="reason" maxlength="255"
                placeholder="Why should these accounts be unsuspended?"></textarea>
    </label>
    <input type="submit" class="button radius" value="Unsuspend Accounts"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkResetPasswordReveal" role="dialog" aria-modal="true" aria-labelledby="bulkResetPasswordRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkResetPasswordRevealTitle">Reset Password for <span id="bulkResetPasswordCount">0</span> Account(s)</h4>
  <p>An email with password reset instructions will be sent to every listed account.</p>
  <ul id="bulkResetPasswordList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkResetPassword"/>
    <label for="bulkResetPasswordStepUpCredential">Your password or authenticator code <span class="required">*</span>
      <input type="password" id="bulkResetPasswordStepUpCredential" name="stepUpCredential" maxlength="255"
             placeholder="Password or 6-digit code" required
             title="Re-authentication required to reset another user's password"/>
    </label>
    <input type="submit" class="button warning radius" value="Send Reset Emails"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<div class="reveal" id="bulkAssignRolesReveal" role="dialog" aria-modal="true" aria-labelledby="bulkAssignRolesRevealTitle"
     data-reveal data-close-on-click="true">
  <h4 id="bulkAssignRolesRevealTitle">Assign Role to <span id="bulkAssignRolesCount">0</span> Account(s)</h4>
  <p class="help-text">This role is added to every selected account; existing roles are left alone.</p>
  <ul id="bulkAssignRolesList"></ul>
  <form method="post">
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <input type="hidden" name="command" value="bulkAssignRoles"/>
    <label for="bulkRoleId">Role <span class="required">*</span>
      <select id="bulkRoleId" name="roleId" required>
        <c:forEach items="${roleList}" var="role">
          <c:choose>
            <c:when test="${role.level > actingRoleLevel}"><%-- not offered --%></c:when>
            <c:otherwise>
              <option value="${role.id}"><c:out value="${role.title}" /></option>
            </c:otherwise>
          </c:choose>
        </c:forEach>
      </select>
    </label>
    <label for="bulkAssignRolesStepUpCredential">Your password or authenticator code <span class="required">*</span>
      <input type="password" id="bulkAssignRolesStepUpCredential" name="stepUpCredential" maxlength="255"
             placeholder="Password or 6-digit code" required
             title="Re-authentication required to change account roles"/>
    </label>
    <input type="submit" class="button warning radius" value="Assign Role"/>
    <button class="button secondary radius" type="button" data-close>Cancel</button>
  </form>
  <button class="close-button" data-close aria-label="Close reveal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
</div>
<script nonce="${cspNonce}">
  (function () {
    var currentUserId = ${userSession.userId};
    var $selectAll = $('#selectAllUsers');
    var $rows = $('.userRowCheckbox');
    var $bar = $('#bulkActionsBar');
    var $count = $('#bulkSelectedCount');

    function selected() {
      return $rows.filter(':checked');
    }

    function refresh() {
      var n = selected().length;
      $count.text(n + (n === 1 ? ' account selected  ' : ' accounts selected  '));
      $bar.toggle(n > 0);
      $selectAll.prop('indeterminate', n > 0 && n < $rows.length);
      $selectAll.prop('checked', n > 0 && n === $rows.length);
    }

    // Populates one bulk modal's hidden userId fields and visible name/email list from the
    // currently-checked rows, so the admin sees exactly who is about to be affected before
    // confirming -- not just a count. checkSelf additionally toggles a non-blocking notice when
    // the acting admin's own account is among the selection (Suspend only -- the real guard is
    // server-side regardless of what this shows).
    function populateBulkModal(revealId, listId, checkSelf) {
      var $reveal = $('#' + revealId);
      var $form = $reveal.find('form');
      var $list = $('#' + listId);
      $form.find('input[name="userId"]').remove();
      $list.empty();
      var includesSelf = false;
      selected().each(function () {
        var $checkbox = $(this);
        $form.append($('<input type="hidden" name="userId">').val($checkbox.val()));
        $list.append($('<li>').text($checkbox.data('name') + ' <' + $checkbox.data('email') + '>'));
        if (checkSelf && String($checkbox.val()) === String(currentUserId)) {
          includesSelf = true;
        }
      });
      $('#' + revealId + 'Count').text(selected().length);
      if (checkSelf) {
        $('#bulkSuspendSelfNotice').toggle(includesSelf);
      }
      $reveal.foundation('open');
    }

    $selectAll.on('change', function () {
      $rows.prop('checked', this.checked);
      refresh();
    });
    $rows.on('change', refresh);

    $('#bulkAssignRolesBtn').on('click', function () { populateBulkModal('bulkAssignRolesReveal', 'bulkAssignRolesList', false); });
    $('#bulkResetPasswordBtn').on('click', function () { populateBulkModal('bulkResetPasswordReveal', 'bulkResetPasswordList', false); });
    $('#bulkSuspendBtn').on('click', function () { populateBulkModal('bulkSuspendReveal', 'bulkSuspendList', true); });
    $('#bulkUnsuspendBtn').on('click', function () { populateBulkModal('bulkUnsuspendReveal', 'bulkUnsuspendList', false); });

    refresh();
  })();
</script>
<%--<div class="reveal small" id="formReveal" data-reveal data-close-on-esc="false" data-close-on-click="false" data-animation-in="slide-in-down fast">--%>
<%-- No data-animation-in (issue #1320, same as #1318): Foundation's Motion-UI animateIn path
     leaves this display:none forever -- a CSS transition can't start on an element that's still
     display:none when the animation class is added, so the transitionend it waits for to reveal
     the element never fires. Omitting it uses Foundation's default, non-animated open. --%>
<div class="reveal small" id="formReveal" data-reveal data-close-on-click="false" role="dialog" aria-modal="true" aria-labelledby="userFormRevealTitle">
  <button class="close-button" data-close aria-label="Close modal" type="button">
    <span aria-hidden="true">&times;</span>
  </button>
  <h4 id="userFormRevealTitle">New User</h4>
  <form id="userForm" method="post" autocomplete="off">
    <%-- Required by controller --%>
    <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
    <input type="hidden" name="token" value="${userSession.formToken}"/>
    <%-- Form --%>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-5 cell">
        <label>First Name <span class="required">*</span>
          <input type="text" placeholder="First Name" name="firstName" value="" autofocus required>
        </label>
      </fieldset>
      <fieldset class="medium-7 cell">
        <label>Last Name <span class="required">*</span>
          <input type="text" placeholder="Last Name" name="lastName" value="" required>
        </label>
      </fieldset>
    </div>
    <label>Email <span class="required">*</span>
      <input type="email" placeholder="Email Address" name="email" value="" required>
    </label>
    <label>Organization
      <input type="text" placeholder="Organization" name="organization" value="">
    </label>
    <div class="grid-x grid-margin-x">
      <fieldset class="medium-6 cell">
        <label>Community Nickname
          <input type="text" placeholder="Nickname" name="nickname" value="">
        </label>
        <p class="help-text" id="nicknameHelpText">Optional name to be shown instead of first/last name</p>
      </fieldset>
      <fieldset class="medium-6 cell">
        <label>Username
          <input type="text" placeholder="Username" name="username" value="">
        </label>
        <p class="help-text" id="usernameHelpText">Optional, system uses email address when this is empty</p>
      </fieldset>
    </div>
    <c:if test="${!empty roleList}">
      <legend>Roles</legend>
      <c:forEach items="${roleList}" var="role">
        <c:choose>
          <c:when test="${role.level > actingRoleLevel}"><%-- --%></c:when>
          <c:otherwise>
            <input id="roleId${role.id}" type="checkbox" name="roleId${role.id}" value="${role.id}" /><label for="roleId${role.id}"><c:out value="${role.title}" /></label>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </c:if>
    <c:if test="${!empty groupList}">
      <legend>Groups</legend>
      <c:forEach items="${groupList}" var="group">
        <c:choose>
          <c:when test="${group.name eq 'All Guests'}">
            <%-- not a logged in user group --%>
          </c:when>
          <c:otherwise>
            <input id="groupId${group.id}" type="checkbox" name="groupId${group.id}" value="${group.id}" /><label for="groupId${group.id}"><c:out value="${group.name}" /></label>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </c:if>
    <div class="button-container">
      <input type="submit" class="button radius expanded" value="Save" />
    </div>
  </form>
</div>

<hr>
<h5>Adding users</h5>
<ul>
  <li><strong>New User</strong> creates one account and emails it an invitation with instructions to
    set a password. The account shows as <strong>Inactive</strong> until that link is used -- there's
    no separate "activate" step, using the link is what activates it.</li>
  <li><strong>Upload CSV File</strong> creates many accounts at once. The file needs
    <code>Email</code>, <code>First Name</code>, and <code>Last Name</code> columns; an optional
    <code>Groups</code> column (comma-separated group names) adds group membership beyond the default
    "All Users", and an optional <code>Date</code> column (<code>yyyy-MM-dd hh:mm:ss</code>) backdates
    the created timestamp. A row is skipped as a likely duplicate, silently and not reported as an
    error, if its email already matches an existing account's <em>username</em> -- true for any
    account still using the default (its username was never set separately from its email), but not
    a guaranteed email-uniqueness check once an account's username has been customized away from its
    email on the edit form.
    <strong>Unlike New User, CSV import sends no invitation email and grants no role</strong> -- every
    imported account can't sign in until an admin explicitly sends it a password-reset email (select
    the imported rows on this page and use <strong>Reset Password</strong> below), and has no role
    beyond default group membership until one is granted via <strong>Assign Roles</strong> or the
    account's own <a href="${ctx}/admin/user-details">detail page</a>.</li>
  <li>The roles offered on the <strong>New User</strong> form are capped at
    <strong>your own highest role level</strong> -- you can't grant admin from this form unless you
    are yourself an admin, and community-managers won't see it as an option at all. CSV import has no
    role selection at all, at any level -- see above.</li>
</ul>

<h5>Bulk actions</h5>
<p>
  Select rows with the checkboxes to reveal the bulk action bar. Selection is scoped to <strong>the
  current page of results</strong> only (up to <c:out value="${recordPaging.pageSize}" /> at a time,
  and a request is rejected outright, never silently trimmed, past 100 ids) -- "select all" does not
  reach across pages.
</p>
<ul>
  <li><strong>Assign Roles</strong> adds one role to every selected account; it never removes an
    existing role, and an account that already has the role is counted as already-done, not a
    failure. The <em>grant itself</em> is capped at your own role level either way -- but unlike New
    User's role checkboxes, this dropdown's visible options aren't filtered by level, only "admin" is
    hidden from non-admins by name. A community-manager may still see a role above their own level
    listed here; picking it and submitting is rejected, just not hidden from the list first.</li>
  <li><strong>Reset Password</strong> emails password-reset instructions to every selected account,
    same as the single-account action on its detail page.</li>
  <li><strong>Suspend</strong> requires a reason (via the modal; a raw request without one is not
    rejected server-side the way Deny/Request Unsuspend are) and skips your own account if it's in
    the selection -- suspending yourself out of the admin console isn't possible from here or from
    the detail page. A suspended account can't sign in until it's unsuspended.
    <strong>Unlike the single-account Suspend action on the detail page, this bulk action does not
    currently check whether a selected account outranks you</strong> -- a community-manager can bulk-
    suspend an admin account here even though they'd be refused doing the same thing one at a time
    from that account's own page. Treat that gap as a reason to double-check a selection before
    suspending in bulk, not as protection you can rely on.</li>
  <li><strong>Unsuspend</strong> restores a suspended account directly -- <em>unless</em> that account
    holds an elevated role (community-manager and above), in which case it can't be reactivated by one
    admin acting alone: it's filed as a request instead, and a <em>different</em> eligible admin has to
    review and approve it from that account's <a href="${ctx}/admin/user-details">detail page</a>
    (or from <a href="${ctx}/admin/unsuspend-requests">Unsuspend Requests</a>). A reason is required
    only when at least one selected account is elevated. Approval immediately invalidates the account's
    password, so its owner has to set a new one.</li>
  <li><strong>Reset Password</strong> and <strong>Assign Roles</strong> both require you to re-enter
    your own password or authenticator code first (a "step-up" prompt) -- this re-authentication is
    good for 5 minutes per session, so acting on several batches in a row won't re-prompt every time.
    A missing or wrong step-up code rejects the whole batch before anything happens; nothing is done
    partially.</li>
  <li>No bulk action can ever grant a role above your own level or apply to more than 100 accounts at
    once -- both are enforced on every account in the batch, not just checked once up front, so a
    mixed selection can't slip a higher-level role grant through. The equivalent protection against
    acting on an account that outranks you exists for Assign Roles' grant and (indirectly, via the
    maker-checker approval requirement) for Unsuspend, but currently does <strong>not</strong> exist
    for bulk Suspend -- see above.</li>
</ul>

<h5>Common problems and how to fix them</h5>
<ul>
  <li><strong>A CSV-imported user says they can't sign in.</strong> Expected -- CSV import sends no
    invitation. Select them here and use bulk <strong>Reset Password</strong> to send them a
    password-setup link.</li>
  <li><strong>An admin role isn't offered when adding or promoting someone.</strong> You can only
    grant a role at or below your own highest role level. If you need to grant admin and aren't one
    yourself, an existing admin needs to do it (or grant you the role first).</li>
  <li><strong>"You cannot suspend/restore an account with a higher role level than your own."</strong>
    The same role-level guard that limits what you can grant also limits who you can act on for the
    single-account Suspend and Restore actions on a detail page -- a community-manager can't suspend
    or restore an admin account that way. That specific check does not extend to bulk Suspend on this
    page (see "Bulk actions" above) or to Delete Account on the detail page, which today only blocks
    deleting your own account, not a higher-level one.</li>
  <li><strong>Unsuspending an elevated account didn't restore it immediately.</strong> By design -- a
    community-manager or higher account requires a second, different admin's approval. Check
    <a href="${ctx}/admin/unsuspend-requests">Unsuspend Requests</a> for its status.</li>
  <li><strong>"Too many accounts were selected."</strong> The cap is 100 per bulk action. Narrow the
    filters/search first, or run the action in smaller batches.</li>
</ul>

<h5>What to monitor</h5>
<p>
  The <a href="${ctx}/admin/audit-log">Audit Log</a> records every action on this page individually --
  each account in a bulk batch gets its own event (so you can see exactly which 3 of 40 selected
  accounts failed), plus one summary event for the batch as a whole. Watch in particular for repeated
  <code>user.disable</code> (suspensions) or <code>user.delete</code> events you didn't expect, and for
  the <span class="label warning radius">unsuspend request awaiting review</span> banner at the top of
  this page piling up -- a growing queue usually means there aren't enough admins available to approve
  each other's unsuspend requests (remember: the requester can't approve their own).
</p>

<div class="callout radius" style="margin-top:10px">
  <p style="margin-bottom:0">
    <i class="fa fa-info-circle"></i> <strong>Coming soon, not yet available:</strong> a CSV export/
    download button on this page, a "Reset MFA" action on the account detail page, and moving this
    page's access check from a hardcoded role list to the newer capability-grant system (so a custom
    role could be given user-management access without also being a full community-manager).
  </p>
</div>

<%--<script nonce="${cspNonce}">--%>
<%--  $(document).on('open.zf.reveal', '[data-reveal]', function () {--%>
<%--    let modal = $(this);--%>
<%--    modal.find('[autofocus]').focus();--%>
<%--  });--%>
<%--</script>--%>