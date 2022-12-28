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
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="tableOfContents" class="com.simisinc.platform.domain.model.cms.TableOfContents" scope="request"/>
<small>Unique Id: <c:out value="${tableOfContents.tocUniqueId}" /></small>
<form class="table-of-contents-editor" method="post">
  <%-- Required by controller --%>
  <input type="hidden" name="widget" value="${widgetContext.uniqueId}"/>
  <input type="hidden" name="token" value="${userSession.formToken}"/>
  <%-- Form Content --%>
  <input type="hidden" name="uniqueId" value="${tableOfContents.tocUniqueId}"/>
  <input type="hidden" name="returnPage" value="${returnPage}"/>
  <table class="no-gap-all">
    <thead>
    <tr>
      <th width="10%">Order</th>
      <th width="40%">Name <span class="required">*</span></th>
      <th width="50%">Web Page Link (/page) <span class="required">*</span></th>
    </tr>
    </thead>
    <tbody>
    <c:forEach items="${tableOfContents.entries}" var="entry" varStatus="status">
      <tr>
        <td>
          <input type="text" name="order${status.count}" value="${status.count}" />
        </td>
        <td>
          <input type="text" name="name${status.count}" value="${entry.name}" />
        </td>
        <td>
          <input type="text" name="link${status.count}" value="${entry.link}" />
        </td>
      </tr>
    </c:forEach>
    <%-- Extra rows --%>
    <c:forEach var="i" begin="1" end="10">
      <tr>
        <td>
          <input type="text" name="order${tableOfContents.entries.size() + i}" value="${tableOfContents.entries.size() + i}" />
        </td>
        <td>
          <input type="text" name="name${tableOfContents.entries.size() + i}" value="" />
        </td>
        <td>
          <input type="text" name="link${tableOfContents.entries.size() + i}" value="" />
        </td>
      </tr>
    </c:forEach>
    </tbody>
  </table>
  <div class="button-container">
    <input type="submit" class="button radius success" value="Save"/>
    <a href="${returnPage}" class="button radius secondary">Cancel</a>
  </div>
</form>
