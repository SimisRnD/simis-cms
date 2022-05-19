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
<%@ taglib prefix="text" uri="/WEB-INF/text-functions.tld" %>
<%@ taglib prefix="date" uri="/WEB-INF/date-functions.tld" %>
<%@ taglib prefix="user" uri="/WEB-INF/user-functions.tld" %>
<%@ taglib prefix="url" uri="/WEB-INF/url-functions.tld" %>
<jsp:useBean id="userSession" class="com.simisinc.platform.presentation.controller.UserSession" scope="session"/>
<jsp:useBean id="widgetContext" class="com.simisinc.platform.presentation.controller.WidgetContext" scope="request"/>
<jsp:useBean id="blog" class="com.simisinc.platform.domain.model.cms.Blog" scope="request"/>
<jsp:useBean id="blogPostList" class="java.util.ArrayList" scope="request"/>
<jsp:useBean id="recordPaging" class="com.simisinc.platform.infrastructure.database.DataConstraints" scope="request"/>
<jsp:useBean id="recordPagingUri" class="java.lang.String" scope="request"/>
<jsp:useBean id="showAuthor" class="java.lang.String" scope="request"/>
<%--<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>--%>
<jsp:useBean id="showImage" class="java.lang.String" scope="request"/>
<jsp:useBean id="showTags" class="java.lang.String" scope="request"/>
<jsp:useBean id="showSummary" class="java.lang.String" scope="request"/>
<jsp:useBean id="showDate" class="java.lang.String" scope="request"/>
<jsp:useBean id="readMoreText" class="java.lang.String" scope="request"/>
<jsp:useBean id="cardClass" class="java.lang.String" scope="request"/>
<jsp:useBean id="smallCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="mediumCardCount" class="java.lang.String" scope="request"/>
<jsp:useBean id="largeCardCount" class="java.lang.String" scope="request"/>
<c:if test="${!empty title}">
  <h4><c:if test="${!empty icon}"><i class="fa ${icon}"></i> </c:if><c:out value="${title}"/></h4>
</c:if>
<%@include file="../page_messages.jspf" %>
<c:if test="${!blog.enabled}">
  <div class="callout warning">Currently marked offline</div>
</c:if>
<c:choose>
  <c:when test="${!empty blogPostList}">
    <div class="grid-container">
      <c:forEach items="${blogPostList}" var="blogPost" varStatus="status">
        <div class="grid-x grid-padding-x">
          <c:if test="${showImage eq 'true' && !empty blogPost.imageUrl}">
            <div class="small-12 medium-5 cell">
              <div class="featured-blog-image">
                <a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}"><img alt="Blog post banner image" src="${ctx}${blogPost.imageUrl}"/></a>
              </div>
            </div>
          </c:if>
          <div class="small-12 medium-auto cell">
            <div class="featured-blog-title">
              <h3><a href="${ctx}/${blog.uniqueId}/${blogPost.uniqueId}">${html:toHtml(blogPost.title)}</a></h3>
            </div>
            <div class="featured-blog-intro">
              <c:choose>
                <c:when test="${!empty blogPost.summary}">
                  <p>${html:toHtml(text:trim(html:text(blogPost.summary), 256, true))}</p>
                </c:when>
                <c:otherwise>
                  <p>${html:toHtml(text:trim(html:text(blogPost.body), 220, true))}</p>
                </c:otherwise>
              </c:choose>
            </div>
            <c:if test="${showAuthor eq 'true'}">
              <div class="featured-blog-author">
                <c:out value="${user:name(blogPost.createdBy)}"/>
              </div>
            </c:if>
            <c:if test="${showDate eq 'true' && !empty blogPost.startDate}">
              <div class="featured-blog-date">
                <c:out value="${date:formatMonthDayYear(blogPost.startDate)}"/>
              </div>
            </c:if>
          </div>
        </div>
      </c:forEach>
    </div>
    <%-- Paging Control --%>
    <%@include file="../paging_control.jspf" %>
  </c:when>
  <c:otherwise>
    <p class="subheader">
      No posts were found
    </p>
  </c:otherwise>
</c:choose>
