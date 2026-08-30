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
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="role" class="com.simisinc.platform.domain.model.Role" scope="request"/>
<jsp:useBean id="capabilityList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="grantedCodes" class="java.util.HashSet" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
</c:if>
<h3 class="h4"><c:out value="${role.title}" /></h3>
<%@include file="../page_messages.jspf" %>
<p class="help-text">Capabilities checked below apply to everyone who holds this role. A user's effective access is the union of every role they hold plus any active direct grants made to them individually - a direct grant (see a user's Capability Grants page from their user details) can only add to what a role provides, never restrict it, so don't use this form to try to carve out an exception for one person.</p>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form --%>
  <input type="hidden" name="roleId" value="${role.id}"/>
  <fieldset>
    <legend>Capabilities</legend>
    <c:forEach items="${capabilityList}" var="capability">
      <label>
        <input type="checkbox" name="capability${capability.id}" value="true"<c:if test="${grantedCodes.contains(capability.code)}"> checked</c:if>>
        <code><c:out value="${capability.code}" /></code> - <c:out value="${capability.description}" />
      </label>
    </c:forEach>
  </fieldset>
  <p class="help-text">Revoking "admin:manage" is refused if no one would be left holding it through any role or direct grant. Every other capability can be revoked here down to zero holders with no warning - confirm some other path to that functionality exists first.</p>
  <label>Reason (required)
    <input type="text" name="reason" placeholder="Why is this change being made?" required>
  </label>
  <p class="help-text">The reason you enter is the only free-text detail that lands in the audit trail for this exact change - write something a future reader would actually find useful (what changed and why, e.g. a ticket or decision reference), not "cleanup" or "fix".</p>
  <p class="help-text">Every grant or revoke here is recorded in the platform's audit log along with this reason. Sessions already logged in as an affected user won't see the change take effect until they log in again - tell them to log out and back in if the change needs to apply now.</p>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save"/>
    <a href="${ctx}/admin/role-capabilities" class="button radius secondary">Cancel</a>
  </div>
</form>
