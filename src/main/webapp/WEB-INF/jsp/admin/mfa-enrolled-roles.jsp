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
<%-- Sits directly under the settings form's Save/Cancel buttons, so it needs its own breathing
     room rather than butting up against them. --%>
<div style="margin-top: 2.5rem;">
<h2 class="h4">MFA status by role</h2>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Every role in the system and where it stands. <strong>Required</strong> means the role is listed above in
  &ldquo;Roles that must enroll in MFA&rdquo;. <strong>Enrolled</strong> means at least one member has actually set up a second
  factor &mdash; a role with some but not all members enrolled still counts, since enforcement is per-role, not per-user.
  The two are independent, so a role can be required with nobody enrolled yet, or enrolled without being required.</p>
<c:choose>
  <c:when test="${empty statusList}">
    <p><em>No roles found</em></p>
  </c:when>
  <c:otherwise>
    <table class="unstriped">
      <thead>
        <tr>
          <th>Role</th>
          <th>Code</th>
          <th>Required</th>
          <th>Enrolled</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
      <c:forEach var="status" items="${statusList}">
        <tr>
          <td><c:out value="${status.role.title}" /></td>
          <td><code><c:out value="${status.role.code}" /></code></td>
          <td>
            <c:choose>
              <c:when test="${status.required}"><span class="label success">Required</span></c:when>
              <c:otherwise><span class="label secondary">Not required</span></c:otherwise>
            </c:choose>
          </td>
          <td>
            <c:choose>
              <c:when test="${status.enrolled}"><span class="label success">Enrolled</span></c:when>
              <c:otherwise><span class="label secondary">None enrolled</span></c:otherwise>
            </c:choose>
          </td>
          <td>
            <c:if test="${status.enrolled}">
              <a href="#" data-open="removeMfaRoleReveal${status.role.id}">Remove MFA</a>
            </c:if>
          </td>
        </tr>
      </c:forEach>
      </tbody>
    </table>
  </c:otherwise>
</c:choose>
</div>
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
