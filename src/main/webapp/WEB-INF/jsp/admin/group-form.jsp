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
<jsp:useBean id="group" class="com.simisinc.platform.domain.model.Group" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${group.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <c:if test="${group.name eq 'All Users'}">
    <div class="callout radius warning">
      <p style="margin-bottom:0">
        <i class="fa fa-exclamation-triangle"></i> This is the built-in default group every new
        account is automatically added to, looked up by this exact name. Changing the name here
        means new accounts stop being found by that lookup and silently miss the group they're
        supposed to get. If you're trying to reorganize access, create a new group instead.
      </p>
    </div>
  </c:if>
  <%-- Form Content --%>
  <label>Name <span class="required">*</span>
    <input type="text" placeholder="Give it a name..." name="name" maxlength="100" aria-describedby="uniqueNameHelpText" value="<c:out value="${group.name}"/>" required>
  </label>
    <p class="help-text" id="uniqueNameHelpText">Must be unique among groups when creating a new one;
      editing an existing group does not re-check this, so it's possible (if unwise) to rename one
      group to collide with another's name.</p>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${group.description}"/>">
  </label>
  <label>Unique Id <span class="required">*</span>
    <input type="text" placeholder="Internal Reference Id..." name="uniqueId" aria-describedby="uniqueIdHelpText" value="<c:out value="${group.uniqueId}"/>">
  </label>
  <p class="help-text" id="uniqueIdHelpText">Leave blank to auto-generate; this value does not usually change! No spaces, use lowercase, a-z, 0-9, dashes</p>
  <p class="help-text">
    This isn't just an internal label -- some access rules elsewhere on the site (restricting a page,
    section, or widget to this group) reference it by this exact Unique Id, not by the Name above.
    Unlike a Collection's "Access Groups" settings (which track a group by an internal id that
    doesn't change), those rules match this string directly, so editing it after the group is
    already in use can silently disconnect them -- they won't error, they'll just stop matching
    anyone. If in doubt, leave it as-is once set.
  </p>
  <div class="button-container">
    <input type="submit" class="button radius success expanded" value="Save"/>
  </div>
</form>