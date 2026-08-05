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
<%@ taglib prefix="image" uri="/WEB-INF/tlds/image-functions.tld" %>
<jsp:useBean id="imageUrl" class="java.lang.String" scope="request"/>
<jsp:useBean id="altText" class="java.lang.String" scope="request"/>
<c:choose>
  <c:when test="${!empty imageUrl}">
    <c:set var="widgetImageSrcset" value="${image:srcset(imageUrl)}"/>
    <img class="platform-image-widget" src="<c:out value="${imageUrl}"/>" alt="<c:out value="${altText}"/>"
      <c:if test="${not empty widgetImageSrcset}"> srcset="<c:out value="${widgetImageSrcset}"/>" sizes="100vw"</c:if>
      decoding="async"/>
  </c:when>
  <c:otherwise>
    <%-- No image configured yet -- a placeholder, never a broken <img> tag --%>
    <div class="platform-image-widget-placeholder" role="img" aria-label="<c:out value="${empty altText ? 'No image set' : altText}"/>">
      <i class="fa fa-image" aria-hidden="true"></i>
    </div>
  </c:otherwise>
</c:choose>
