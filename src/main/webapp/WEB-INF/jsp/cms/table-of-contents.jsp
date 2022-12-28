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
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="font" uri="/WEB-INF/tlds/font-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="tableOfContents" class="com.simisinc.platform.domain.model.cms.TableOfContents" scope="request"/>
<div class="platform-toc-container">
  <c:if test="${showEditor eq 'true' && !empty uniqueId}">
    <c:choose>
      <c:when test="${!empty tableOfContents.entries}">
        <div class="platform-toc-editor"><a class="hollow button small secondary" href="${ctx}/table-of-contents-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i></a></div>
      </c:when>
      <c:otherwise>
        <a class="button tiny radius primary" href="${ctx}/table-of-contents-editor?uniqueId=${uniqueId}&returnPage=${returnPage}"><i class="${font:fas()} fa-edit"></i> Add Table of Contents Here</a>
      </c:otherwise>
    </c:choose>
  </c:if>
  <nav aria-label="You are here:" role="navigation">
    <ul class="vertical menu">
      <c:forEach items="${tableOfContents.entries}" var="link" varStatus="status">
        <c:choose>
          <c:when test="${link.active}">
            <li class="is-active"><a class="is-active" href="${ctx}/${url:encodeUri(link.link.substring(1))}"><c:out value="${link.name}"/></a></li>
          </c:when>
          <c:when test="${!empty link.link}">
            <li><a href="${ctx}${link.link}"><c:out value="${link.name}"/></a></li>
          </c:when>
          <c:otherwise>
            <li>
              <span class="show-for-sr">Current: </span> <c:out value="${link.name}" />
            </li>
          </c:otherwise>
        </c:choose>
      </c:forEach>
    </ul>
  </nav>
</div>
