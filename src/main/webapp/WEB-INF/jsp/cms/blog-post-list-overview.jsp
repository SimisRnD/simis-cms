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
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="html" uri="/WEB-INF/tlds/html-functions.tld" %>
<%@ taglib prefix="text" uri="/WEB-INF/tlds/text-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/tlds/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/tlds/user-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/tlds/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPostList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="showReadMore" class="java.lang.String" scope="request"/>
<jsp:useBean id="blogPostReviewStatusMap" class="java.util.HashMap" scope="request"/>
<c:if test="${!empty title}">
  <h2 class="widget-title"><c:if test="${!empty icon}"><i class="fa ${fn:escapeXml(icon)}"></i> </c:if><c:out value="${title}"/></h2>
  <hr />
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
      <h5>
        <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a>
        <c:if test="${empty blogPost.published}"><span class="label warning">not published</span></c:if>
        <c:if test="${date:isAfterNow(blogPost.startDate)}"><span class="label success">Set to display <c:out value="${date:relative(blogPost.startDate)}"/></span></c:if>
        <%-- Governed publish workflow status (issue #407, phase 2) -- only present in the map for
             admin/content-manager viewers, and only when the post has a pending draft awaiting
             review; mirrors WebPageListWidget/web-page-list.jsp's identical status label. --%>
        <c:if test="${!empty blogPostReviewStatusMap[blogPost.id]}">
          <a href="${ctx}/admin/blog-post-review?blogPostId=${blogPost.id}" class="secondary label">
            <i class="fa fa-clipboard-check"></i> <c:out value="${blogPostReviewStatusMap[blogPost.id]}" />
          </a>
        </c:if>
      </h5>
      <c:if test="${!empty blogPost.startDate}">
        <small>
          <c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/>
        </small>
      </c:if>
      <c:if test="${showReadMore eq 'true'}">
        <p>
          <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}" class="read-more">Read more</a>
        </p>
      </c:if>
      <c:if test="${!status.last}">
        <hr/>
      </c:if>
    </c:forEach>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No posts were found
    </p>
  </c:otherwise>
</c:choose>
