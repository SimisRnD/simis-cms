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
<jsp:useBean id="blockedIP" class="com.simisinc.platform.domain.model.BlockedIP" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${blockedIP.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h4><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h4>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <div class="callout warning radius">
    <p style="margin-bottom:0;">This is a general, <strong>site-wide</strong> block, not scoped to any particular page. An <a href="${ctx}/admin/allowed-ip-list">Allowed IP</a> entry always wins over this one if it also matches the same address, since the Allowed list is checked first -- saving below will warn you inline if that's the case here. If a saved block doesn't seem to work, see the <a href="${ctx}/admin/blocked-ip-list">Blocked IP list</a> page for the "your current IP" diagnostic and full troubleshooting guidance (a reverse proxy without <code>CMS_TRUSTED_PROXIES</code> configured is a common cause).</p>
  </div>
  <%-- Form Content --%>
  <label>IP Address or CIDR Range to Block <span class="required">*</span>
    <input type="text" placeholder="ip address or CIDR e.g. 203.0.113.0/24" name="ipAddress" value="<c:out value="${blockedIP.ipAddress}"/>" required>
  </label>
  <label>Reason
    <input type="text" placeholder="" name="reason" value="<c:out value="${blockedIP.reason}"/>">
  </label>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="Save"/>
  </div>
</form>