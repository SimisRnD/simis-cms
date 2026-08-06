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
<div class="reveal small" id="formReveal" data-reveal data-close-on-click="false" data-animation-in="slide-in-down fast" role="dialog" aria-modal="true" aria-labelledby="userFormRevealTitle">
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
<%--<script nonce="${cspNonce}">--%>
<%--  $(document).on('open.zf.reveal', '[data-reveal]', function () {--%>
<%--    let modal = $(this);--%>
<%--    modal.find('[autofocus]').focus();--%>
<%--  });--%>
<%--</script>--%>