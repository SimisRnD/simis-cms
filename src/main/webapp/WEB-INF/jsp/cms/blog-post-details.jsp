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
<%@ taglib prefix="html" uri="/WEB-INF/html-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPost" class="com.simisinc.platform.domain.model.cms.BlogPost" scope="request"/>
<jsp:useBean id="showTitle" class="java.lang.String" scope="request"/>
<jsp:useBean id="showAuthor" class="java.lang.String" scope="request"/>
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<c:if test="${userSession.hasRole('admin') || userSession.hasRole('content-manager')}">
  <script>
    function deletePost() {
      if (!confirm("Are you sure you want to DELETE this post?")) {
        return;
      }
      window.location.href = '${widgetContext.uri}?action=deletePost&widget=${widgetContext.uniqueId}&token=${userSession.formToken}&blogPostId=${blogPost.id}';
    }
  </script>
</c:if>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:if test="${empty blogPost.published}">
  <div class="callout warning">Currently not published</div>
</c:if>
<div class="platform-blog-container">
  <c:if test="${showTitle eq 'true'}">
  <div class="platform-blog-title">
    <h2>${html:toHtml(blogPost.title)}</h2>
  </div>
  </c:if>
  <c:if test="${showAuthor eq 'true' || (showDate eq 'true' && !empty blogPost.startDate) || !empty blogPost.tagsList || date:isAfterNow(blogPost.startDate)}">
    <div class="platform-blog-byline">
      <div class="grid-x grid-margin-x">
        <c:if test="${showAuthor eq 'true'}">
          <div class="cell shrink">
            by <c:out value="${user:name(blogPost.createdBy)}"/>
          </div>
        </c:if>
        <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
          <div class="cell shrink">
            <c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/>
          </div>
        </c:if>
        <c:if test="${!empty blogPost.tagsList}">
          <div class="cell auto">
            <c:forEach items="${blogPost.tagsList}" var="tag">
              <span class="label secondary"><c:out value="${tag}"/></span>
            </c:forEach>
          </div>
        </c:if>
        <c:if test="${date:isAfterNow(blogPost.startDate)}">
          <div class="cell auto">
            <span class="label success"><i class="fa fa-bell"></i> Set to display <c:out value="${date:relative(blogPost.startDate)}" /></span>
          </div>
        </c:if>
      </div>
    </div>
  </c:if>
  <div class="platform-blog-body">
    <div class="grid-x grid-margin-x">
      <div class="small-12 cell">
        ${blogPost.body}
      </div>
    </div>
  </div>
</div>
<c:if test="${userSession.hasRole('admin') || userSession.hasRole('content-manager')}">
  <hr/>
  <c:choose>
    <c:when test="${!empty link}">
      <a class="button radius secondary no-gap"" href="${ctx}/blog-editor?blogUniqueId=${blog.uniqueId}&returnPage=${link}&blogPostId=${blogPost.id}"><i class="fa fa-edit"></i> Edit Post</a>
    </c:when>
    <c:otherwise>
      <a class="button radius secondary no-gap"" href="${ctx}/blog-editor?blogUniqueId=${blog.uniqueId}&returnPage=/${blog.uniqueId}&blogPostId=${blogPost.id}"><i class="fa fa-edit"></i> Edit Post</a>
    </c:otherwise>
  </c:choose>
  <a class="button radius alert no-gap"" href="javascript:deletePost()"><i class="fa fa-trash-o"></i> Delete Post</a>
</c:if>