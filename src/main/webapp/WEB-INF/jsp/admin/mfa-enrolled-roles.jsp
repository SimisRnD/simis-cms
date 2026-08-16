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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<h4>Roles already MFA-enrolled</h4>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Roles with at least one member who has already enrolled MFA -- a role with some but not all members enrolled still appears here, since enabling enforcement is per-role, not per-user.</p>
<c:choose>
  <c:when test="${empty roleList}">
    <p><em>None</em></p>
  </c:when>
  <c:otherwise>
    <ul>
      <c:forEach var="role" items="${roleList}">
        <li>
          <c:out value="${role.title}" /> <code><c:out value="${role.code}" /></code>
          <a href="#" data-open="removeMfaRoleReveal${role.id}">Remove MFA</a>
        </li>
      </c:forEach>
    </ul>
  </c:otherwise>
</c:choose>
<c:forEach var="role" items="${roleList}">
  <div class="reveal" id="removeMfaRoleReveal${role.id}" role="dialog" aria-modal="true"
       aria-labelledby="removeMfaRoleRevealTitle${role.id}" data-reveal data-close-on-click="true">
    <h4 id="removeMfaRoleRevealTitle${role.id}">Remove MFA from <c:out value="${role.title}" /></h4>
    <p>This immediately clears the second factor and recovery codes for every enrolled member of
      <strong><c:out value="${role.title}" /></strong>. They will each need to re-enroll from scratch.</p>
    <form method="post">
      <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
      <input type="hidden" name="token" value="${userSession.formToken}"/>
      <input type="hidden" name="action" value="removeMfaFromRole"/>
      <input type="hidden" name="roleId" value="${role.id}"/>
      <div class="grid-x grid-padding-x">
        <div class="small-12 cell">
          <label for="removeMfaRoleStepUpCredential${role.id}">Your password or authenticator code <span class="required">*</span>
            <input type="password" id="removeMfaRoleStepUpCredential${role.id}" name="stepUpCredential" maxlength="255"
                   placeholder="Password or 6-digit code" required
                   title="Re-authentication required to remove MFA for a role"/>
          </label>
        </div>
      </div>
      <input type="submit" class="button warning radius" value="Remove MFA"/>
      <button class="button secondary radius" type="button" data-close>Cancel</button>
    </form>
    <button class="close-button" data-close aria-label="Close reveal" type="button">
      <span aria-hidden="true">&times;</span>
    </button>
  </div>
</c:forEach>
