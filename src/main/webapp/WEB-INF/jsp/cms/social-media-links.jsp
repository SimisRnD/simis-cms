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
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<c:set var="firstIcon" scope="request" value=""/>
<c:set var="iconClass" scope="request" value="margin-left-10"/>
<c:if test="${!empty socialPropertyMap['social.instagram.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.instagram.url']}"/>"><i class="fa fa-2x fa-instagram"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>
<c:if test="${!empty socialPropertyMap['social.twitter.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.twitter.url']}"/>"><i class="fa fa-2x fa-twitter"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>
<c:if test="${!empty socialPropertyMap['social.facebook.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.facebook.url']}"/>"><i class="fa fa-2x fa-facebook-square"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>
<c:if test="${!empty socialPropertyMap['social.youtube.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.youtube.url']}"/>"><i class="fa fa-2x fa-youtube"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>
<c:if test="${!empty socialPropertyMap['social.flickr.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.flickr.url']}"/>"><i class="fa fa-2x fa-flickr"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>
<c:if test="${!empty socialPropertyMap['social.linkedin.url']}">
  <a <c:if test="${!empty firstIcon}">class="${iconClass}" </c:if>target="_blank" href="<c:out value="${socialPropertyMap['social.linkedin.url']}"/>"><i class="fa fa-2x fa-linkedin"></i></a>
  <c:set var="firstIcon" scope="request" value="true"/>
</c:if>