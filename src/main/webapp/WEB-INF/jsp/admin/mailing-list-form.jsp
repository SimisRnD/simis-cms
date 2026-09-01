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
    <label>Basic Name <span class="required">*</span>
      <input type="text" placeholder="What kind is this..." name="name" value="<c:out value="${mailingList.name}"/>">
    </label>
  <p class="help-text">This is the key signup forms are pointed at, not a label &mdash; a page's Email Subscription widget names the list it subscribes to with its <code>mailingList</code> preference. Renaming it here breaks every form still naming the old value: the form stops rendering until the preference is updated to match. Rename <strong>Title</strong> instead when you just want the wording visitors see to change.</p>
  <label>Title <span class="required">*</span>
    <input type="text" placeholder="Give it a title users will see..." name="title" value="<c:out value="${mailingList.title}"/>">
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