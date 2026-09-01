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
<jsp:useBean id="mailingList" class="com.simisinc.platform.domain.model.mailinglists.MailingList" scope="request"/>
<form method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form values --%>
  <input type="hidden" name="id" value="${mailingList.id}"/>
  <%-- Title and Message block --%>
  <c:if test="${!empty title}">
    <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  </c:if>
  <%@include file="../page_messages.jspf" %>
  <%-- Form Content --%>
  <c:if test="${mailingList.id ne -1}">
    <label>Unique Id
      <input type="text" value="<c:out value="${mailingList.uniqueId}"/>" aria-describedby="mailingListUniqueIdHelpText" readonly>
    </label>
    <p class="help-text" id="mailingListUniqueIdHelpText">Generated from the name when this list was created, and never changed after that &mdash; renaming the list below does not affect it. Point a page's Email Subscription widget here with its <code>mailingListUniqueId</code> preference and the form keeps working however the list is later renamed.</p>
  </c:if>
    <label>Basic Name <span class="required">*</span>
      <input type="text" placeholder="What kind is this..." name="name" maxlength="200" aria-describedby="mailingListNameHelpText" value="<c:out value="${mailingList.name}"/>">
    </label>
  <p class="help-text" id="mailingListNameHelpText">Older pages point a form at this list by name, using the Email Subscription widget's <code>mailingList</code> preference, so renaming it here stops any such form rendering until its preference is updated to match. Use <strong>Unique Id</strong> above in new page configuration instead &mdash; it does not change. Rename <strong>Title</strong> when you just want the wording visitors see to change.</p>
  <label>Title <span class="required">*</span>
    <input type="text" placeholder="Give it a title users will see..." name="title" maxlength="200" value="<c:out value="${mailingList.title}"/>" required>
  </label>
  <label>Description
    <input type="text" placeholder="Describe it..." name="description" value="<c:out value="${mailingList.description}"/>">
  </label>
  <input id="showOnline" type="checkbox" name="showOnline" value="true" <c:if test="${mailingList.showOnline}">checked</c:if>/><label for="showOnline">Show Online?</label>
  <div class="button-container">
    <c:choose>
      <c:when test="${mailingList.id eq -1}">
        <input type="submit" class="button radius success expanded" value="Save"/>
      </c:when>
      <c:otherwise>
        <input type="submit" class="button radius success" value="Save"/>
        <a href="${ctx}/admin/mailing-lists" class="button radius secondary">Cancel</a>
      </c:otherwise>
    </c:choose>
  </div>
</form>