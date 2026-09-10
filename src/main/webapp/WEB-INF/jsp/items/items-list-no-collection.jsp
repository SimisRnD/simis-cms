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
<%--
  Editor-only placeholder for ItemsListWidget (issue #817). Rendered only when
  ItemsListWidget.execute() has already confirmed pageEditMode && canBuildLayout -- this JSP does
  not re-check permissions itself, the same way content-html.jsp trusts ContentHtmlCommand. A real
  site visitor, or an editor without layout-build rights, never reaches this file.
--%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}" /></h2>
</c:if>
<div class="callout radius warning text-center" role="alert" data-widget-placeholder="itemsList">
  <p><i class="${font:fas()} fa-triangle-exclamation"></i> This Items List widget has no collection configured, or the configured collection could not be found.</p>
  <p>
    <a class="button tiny radius primary" href="${ctx}/admin/collections">
      <i class="${font:fas()} fa-database"></i> Select a Collection
    </a>
  </p>
</div>
